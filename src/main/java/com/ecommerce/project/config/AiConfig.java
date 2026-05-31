package com.ecommerce.project.config;

import com.ecommerce.project.ai.IntentAwareDocumentRetriever;
import com.ecommerce.project.ai.IntentAwareQueryAugmenter;
import com.ecommerce.project.ai.SanitizingChatMemory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

/**
 * Central Spring AI wiring for the shopping assistant.
 *
 * <p>Auto-configuration already provides a {@link ChatModel}, an EmbeddingModel and a
 * {@link VectorStore} (pgvector). Here we only assemble the higher-level pieces:
 * a conversation {@link ChatMemory} and a {@link ChatClient} pre-loaded with the
 * advisor chain (RAG + memory + logging) and a strict shopping system prompt.
 */
@Configuration
public class AiConfig {

    /**
     * System prompt loaded from {@code src/main/resources/prompts/shopping-assistant-system.st}.
     * Keeping it in an external file makes the prompt easy to iterate on without recompiling and
     * keeps prompt engineering separate from application wiring.
     */
    @Value("classpath:prompts/shopping-assistant-system.st")
    private Resource shoppingAssistantSystemPrompt;

    /**
     * Conversation memory keyed by a conversation id (supplied per request). Keeps a rolling
     * window of the most recent messages so the assistant remembers earlier turns.
     *
     * <p>Backed by {@link JdbcChatMemoryRepository} (auto-configured against the existing Postgres
     * datasource), so conversations are persisted in the SPRING_AI_CHAT_MEMORY table and survive
     * application restarts.
     */
    @Bean
    public ChatMemory chatMemory(JdbcChatMemoryRepository chatMemoryRepository) {
        ChatMemory memory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(20)
                .build();
        // Strip empty tool messages that break OpenAI on follow-up turns (toolCallId required).
        return new SanitizingChatMemory(memory);
    }

    /**
     * Pre-configured {@link ChatClient} used by the assistant.
     *
     * <p>Advisor chain:
     * <ul>
     *   <li>{@link RetrievalAugmentationAdvisor} - modular RAG: retrieves the most similar
     *       products from the pgvector store and augments the user query with them.</li>
     *   <li>{@link MessageChatMemoryAdvisor} - injects prior conversation turns.</li>
     *   <li>{@link ToolCallAdvisor} - runs the tool-calling loop (model requests tool → execute → feed back).</li>
     *   <li>{@link SimpleLoggerAdvisor} - logs requests/responses for observability.</li>
     * </ul>
     */
    @Bean
    public ChatClient chatClient(ChatModel chatModel, VectorStore vectorStore, ChatMemory chatMemory) {

        VectorStoreDocumentRetriever vectorRetriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                // Low threshold because exact (un-indexed) cosine search + a code model's
                // embeddings produce conservative similarity scores; tune as needed.
                .similarityThreshold(0.3)
                .topK(5)
                .build();

        // Cart/order questions skip RAG entirely; product questions use contextual augmentation.
        IntentAwareDocumentRetriever documentRetriever = new IntentAwareDocumentRetriever(vectorRetriever);
        IntentAwareQueryAugmenter queryAugmenter = new IntentAwareQueryAugmenter();

        RetrievalAugmentationAdvisor ragAdvisor = RetrievalAugmentationAdvisor.builder()
                .documentRetriever(documentRetriever)
                .queryAugmenter(queryAugmenter)
                .build();

        return ChatClient.builder(chatModel)
                .defaultSystem(shoppingAssistantSystemPrompt)
                .defaultAdvisors(
                        ragAdvisor,
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        ToolCallAdvisor.builder().build(),
                        new SimpleLoggerAdvisor())
                .build();
    }
}
