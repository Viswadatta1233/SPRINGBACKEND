package com.ecommerce.project.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The assistant's reply. The {@code conversationId} should be sent back on the next request
 * to continue the same conversation (so chat memory works).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssistantResponse {
    private String conversationId;
    private String answer;
}
