package com.ecommerce.project.payload;

/**
 * One streamed piece of the assistant's answer, serialized as a single NDJSON line
 * (e.g. {@code {"token":"Hello"}}). The frontend appends each token to the live message.
 */
public record StreamChunk(String token) {
}
