package com.ecommerce.project.service;

import com.ecommerce.project.ai.CartTools;
import com.ecommerce.project.ai.OrderTools;
import com.ecommerce.project.ai.ProductTools;
import com.ecommerce.project.payload.AssistantResponse;
import com.ecommerce.project.util.AuthUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.UUID;

@Service
public class AiAssistantServiceImpl implements AiAssistantService {

    private static final Logger log = LoggerFactory.getLogger(AiAssistantServiceImpl.class);

    private final ChatClient chatClient;
    private final ProductTools productTools;
    private final OrderTools orderTools;
    private final CartTools cartTools;
    private final AuthUtil authUtil;

    public AiAssistantServiceImpl(ChatClient chatClient,
                                  ProductTools productTools,
                                  OrderTools orderTools,
                                  CartTools cartTools,
                                  AuthUtil authUtil) {
        this.chatClient = chatClient;
        this.productTools = productTools;
        this.orderTools = orderTools;
        this.cartTools = cartTools;
        this.authUtil = authUtil;
    }

    @Override
    public AssistantResponse chat(String conversationId, String message) {
        // Start a new conversation if the client didn't supply an id.
        String convId = StringUtils.hasText(conversationId) ? conversationId : UUID.randomUUID().toString();

        logAssistantRequest("BLOCKING", convId, message);

        String answer = buildRequest(convId, message)
                .call()
                .content();

        logAssistantResponse("BLOCKING", convId, answer);
        return new AssistantResponse(convId, answer);
    }

    @Override
    public Flux<String> streamChat(String conversationId, String message) {
        logAssistantRequest("STREAM", conversationId, message);

        // The customer name + security context are resolved here, on the request thread, while
        // the actual token generation and tool calls run later on Reactor threads (the security
        // context is propagated there via ReactiveContextPropagationConfig).
        StringBuilder answerCollector = new StringBuilder();
        return buildRequest(conversationId, message)
                .stream()
                .content()
                .doOnNext(answerCollector::append)
                .doOnComplete(() -> logAssistantResponse("STREAM", conversationId, answerCollector.toString()))
                .doOnError(ex -> log.error("[ASSISTANT] STREAM failed conversationId={} message={}",
                        conversationId, message, ex))
                .onErrorResume(ex -> {
                    log.error("Streaming assistant failed for conversation {}", conversationId, ex);
                    return Flux.just("\n\nSorry, I ran into a problem answering that. Please try again.");
                });
    }

    /**
     * Builds the shared prompt request: the per-customer system prompt (so the assistant can greet
     * by name), the user message, the DB-backed tools, and the conversation id for chat memory.
     */
    private ChatClient.ChatClientRequestSpec buildRequest(String conversationId, String message) {
        String customerName = resolveCustomerName();
        String customerEmail = resolveCustomerEmail();

        log.info("[ASSISTANT] Building prompt conversationId={} customerName={} customerEmail={} "
                        + "registeredTools=[findProductsByName, checkProductStock, getMyRecentOrders, "
                        + "getOrderStatus, getMyCartSummary] advisors=[RAG, MessageChatMemory, ToolCall, SimpleLogger]",
                conversationId, customerName, customerEmail);

        return chatClient.prompt()
                // Fills the {customerName} placeholder in the system prompt.
                .system(systemSpec -> systemSpec.param("customerName", customerName))
                .user(message)
                // Expose the DB-backed tools so the model can fetch live product, order and cart data.
                // Order/cart tools resolve the logged-in user from the security context.
                .tools(productTools, orderTools, cartTools)
                // Bind this turn to its conversation so MessageChatMemoryAdvisor recalls history.
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, conversationId));
    }

    private void logAssistantRequest(String mode, String conversationId, String message) {
        log.info("[ASSISTANT] >>> REQUEST mode={} conversationId={} userMessage=\"{}\"",
                mode, conversationId, message);
    }

    private void logAssistantResponse(String mode, String conversationId, String answer) {
        log.info("[ASSISTANT] <<< RESPONSE mode={} conversationId={} answerLength={} answer=\"{}\"",
                mode, conversationId, answer == null ? 0 : answer.length(), answer);
        if (answer != null && looksLikeHallucinatedToolSyntax(answer)) {
            log.warn("[ASSISTANT] WARNING: model may have printed a tool-call as text instead of "
                    + "executing it (check for missing [ASSISTANT-TOOL] CALL/RESULT lines above). "
                    + "conversationId={}", conversationId);
        }
    }

    /** Detects when the LLM echoes tool-call JSON instead of using Spring AI tool execution. */
    private boolean looksLikeHallucinatedToolSyntax(String answer) {
        String lower = answer.toLowerCase();
        return lower.contains("\"name\"") && lower.contains("parameters")
                || lower.contains("getorderstatus")
                || lower.contains("getmyrecentorders");
    }

    private String resolveCustomerEmail() {
        try {
            return authUtil.loggedInEmail();
        } catch (Exception ex) {
            log.warn("[ASSISTANT] Could not resolve logged-in customer email on request thread", ex);
            return "unknown";
        }
    }

    private String resolveCustomerName() {
        try {
            String name = authUtil.loggedInUser().getUserName();
            return StringUtils.hasText(name) ? name : "there";
        } catch (Exception ex) {
            // Should not happen on an authenticated endpoint, but never let a missing name break chat.
            log.debug("Could not resolve logged-in customer name", ex);
            return "there";
        }
    }
}
