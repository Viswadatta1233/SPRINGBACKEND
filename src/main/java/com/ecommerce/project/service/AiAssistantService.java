package com.ecommerce.project.service;

import com.ecommerce.project.payload.AssistantResponse;
import reactor.core.publisher.Flux;

/**
 * RAG-powered shopping assistant. Answers natural-language product questions using retrieved
 * catalog context (RAG), live data tools, and per-conversation memory.
 */
public interface AiAssistantService {

    /**
     * Send a message to the assistant within a conversation.
     *
     * @param conversationId conversation id to continue (may be null/blank to start a new one)
     * @param message        the user's message
     * @return the assistant's answer plus the conversation id to reuse on the next turn
     */
    AssistantResponse chat(String conversationId, String message);

    /**
     * Same as {@link #chat(String, String)} but streams the answer token-by-token as it is
     * generated, so the client can render it progressively.
     *
     * @param conversationId resolved conversation id (must not be blank)
     * @param message        the user's message
     * @return a stream of answer fragments
     */
    Flux<String> streamChat(String conversationId, String message);
}
