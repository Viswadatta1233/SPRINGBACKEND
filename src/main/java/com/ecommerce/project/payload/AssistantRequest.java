package com.ecommerce.project.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single chat turn sent to the shopping assistant.
 * {@code conversationId} ties messages together so the assistant remembers the conversation;
 * if omitted, the server generates a new one and returns it.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssistantRequest {
    private String conversationId;
    private String message;
}
