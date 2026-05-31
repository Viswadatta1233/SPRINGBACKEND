package com.ecommerce.project.ai;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Filters malformed tool-call messages from chat history before they are sent to OpenAI.
 * <p>
 * Spring AI + streaming can occasionally persist an empty {@link AssistantMessage} and an
 * empty {@link ToolResponseMessage} (no {@code toolCallId}), which breaks the next request with:
 * {@code IllegalStateException: toolCallId is required, but was not set}.
 */
public class SanitizingChatMemory implements ChatMemory {

    private final ChatMemory delegate;

    public SanitizingChatMemory(ChatMemory delegate) {
        this.delegate = delegate;
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        delegate.add(conversationId, sanitize(messages));
    }

    @Override
    public List<Message> get(String conversationId) {
        return sanitize(delegate.get(conversationId));
    }

    @Override
    public void clear(String conversationId) {
        delegate.clear(conversationId);
    }

    static List<Message> sanitize(List<Message> messages) {
        if (CollectionUtils.isEmpty(messages)) {
            return messages == null ? List.of() : messages;
        }

        List<Message> filtered = new ArrayList<>(messages.size());
        for (Message message : messages) {
            if (message instanceof ToolResponseMessage toolResponse) {
                if (!isValidToolResponse(toolResponse)) {
                    continue;
                }
            } else if (message instanceof AssistantMessage assistant) {
                if (!hasAssistantContent(assistant)) {
                    continue;
                }
            }
            filtered.add(message);
        }

        return removeOrphanToolResponses(filtered);
    }

    private static boolean hasAssistantContent(AssistantMessage assistant) {
        boolean hasText = StringUtils.hasText(assistant.getText());
        boolean hasToolCalls = !CollectionUtils.isEmpty(assistant.getToolCalls());
        return hasText || hasToolCalls;
    }

    private static boolean isValidToolResponse(ToolResponseMessage toolResponse) {
        if (CollectionUtils.isEmpty(toolResponse.getResponses())) {
            return false;
        }
        return toolResponse.getResponses().stream()
                .allMatch(response -> StringUtils.hasText(response.id()));
    }

    private static List<Message> removeOrphanToolResponses(List<Message> messages) {
        List<Message> result = new ArrayList<>(messages.size());
        for (int i = 0; i < messages.size(); i++) {
            Message message = messages.get(i);
            if (message instanceof ToolResponseMessage toolResponse) {
                Set<String> expectedIds = expectedToolCallIds(messages, i);
                boolean allMatched = toolResponse.getResponses().stream()
                        .allMatch(response -> expectedIds.contains(response.id()));
                if (!allMatched) {
                    continue;
                }
            }
            result.add(message);
        }
        return result;
    }

    private static Set<String> expectedToolCallIds(List<Message> messages, int toolResponseIndex) {
        for (int i = toolResponseIndex - 1; i >= 0; i--) {
            Message previous = messages.get(i);
            if (previous instanceof AssistantMessage assistant && !CollectionUtils.isEmpty(assistant.getToolCalls())) {
                Set<String> ids = new HashSet<>();
                assistant.getToolCalls().forEach(toolCall -> {
                    if (StringUtils.hasText(toolCall.id())) {
                        ids.add(toolCall.id());
                    }
                });
                return ids;
            }
            if (previous instanceof ToolResponseMessage) {
                break;
            }
        }
        return Set.of();
    }
}
