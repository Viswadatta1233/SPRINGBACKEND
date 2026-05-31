package com.ecommerce.project.controller;

import com.ecommerce.project.payload.AssistantRequest;
import com.ecommerce.project.payload.AssistantResponse;
import com.ecommerce.project.payload.StreamChunk;
import com.ecommerce.project.service.AiAssistantService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class AiAssistantController {

    private static final Logger log = LoggerFactory.getLogger(AiAssistantController.class);

    /** Response header carrying the conversation id so the client can continue the conversation. */
    public static final String CONVERSATION_ID_HEADER = "X-Conversation-Id";

    @Autowired
    private AiAssistantService aiAssistantService;

    // Created directly (not injected): this project's modular web starter does not expose a
    // Spring-managed ObjectMapper bean, and we only need it to serialize tiny NDJSON token lines.
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/assistant")
    public ResponseEntity<AssistantResponse> chat(@RequestBody AssistantRequest request){
        log.info("[ASSISTANT] HTTP POST /api/assistant conversationId={} message=\"{}\"",
                request.getConversationId(), request.getMessage());
        AssistantResponse response = aiAssistantService.chat(request.getConversationId(), request.getMessage());
        log.info("[ASSISTANT] HTTP POST /api/assistant done conversationId={}", response.getConversationId());
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    /**
     * Streaming variant: emits the answer as it is generated, one NDJSON line per token
     * (e.g. {@code {"token":"Hi"}}). The conversation id is returned in the
     * {@value #CONVERSATION_ID_HEADER} header so the client can reuse it on the next turn.
     *
     * <p>We deliberately write directly to the servlet response (blocking the request thread and
     * flushing each token) instead of returning a reactive {@code Flux}. Returning a {@code Flux}
     * triggers a Spring MVC async re-dispatch, on which this stateless app loses the JWT-based
     * {@code SecurityContext} (the {@code OncePerRequestFilter} skips async dispatches), causing a
     * spurious 401. Streaming on the request thread keeps the standard authenticated request flow.
     */
    @PostMapping(value = "/assistant/stream", produces = MediaType.APPLICATION_NDJSON_VALUE)
    public void chatStream(@RequestBody AssistantRequest request, HttpServletResponse response) throws IOException {
        String conversationId = StringUtils.hasText(request.getConversationId())
                ? request.getConversationId()
                : UUID.randomUUID().toString();

        response.setStatus(HttpStatus.OK.value());
        response.setContentType(MediaType.APPLICATION_NDJSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader(CONVERSATION_ID_HEADER, conversationId);

        PrintWriter writer = response.getWriter();

        // Block on the request thread and forward each token to the client as it is generated.
        // Tool calls run on Reactor threads; the security context is propagated there by
        // ReactiveContextPropagationConfig so the user-scoped order/cart tools keep working.
        //
        // We catch everything here so an exception never propagates out of the controller: if it
        // did, the servlet container would ERROR-dispatch to /error, which (being a fresh,
        // unauthenticated dispatch in this stateless app) returns a misleading 401 that masks the
        // real cause. Instead we log the real error and stream a friendly message to the client.
        log.info("[ASSISTANT] HTTP POST /api/assistant/stream START conversationId={} message=\"{}\"",
                conversationId, request.getMessage());
        try {
            aiAssistantService.streamChat(conversationId, request.getMessage())
                    .toStream()
                    .forEach(token -> {
                        writer.write(toNdjsonLine(token));
                        writer.write("\n");
                        writer.flush();
                    });
            log.info("[ASSISTANT] HTTP POST /api/assistant/stream END conversationId={}", conversationId);
        } catch (Exception ex) {
            log.error("Assistant stream failed for conversation {}", conversationId, ex);
            writer.write(toNdjsonLine("\n\nSorry, I ran into a problem answering that. Please try again."));
            writer.write("\n");
            writer.flush();
        }
    }

    private String toNdjsonLine(String token) {
        try {
            return objectMapper.writeValueAsString(new StreamChunk(token));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize stream token", e);
            return "{\"token\":\"\"}";
        }
    }
}
