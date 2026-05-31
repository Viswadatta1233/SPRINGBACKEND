package com.ecommerce.project.config;

import io.micrometer.context.ContextRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import reactor.core.publisher.Hooks;

/**
 * Bridges servlet {@link ThreadLocal}s into the Reactor pipeline.
 *
 * <p>The shopping assistant streams its answer with {@code ChatClient.stream()}, which runs on
 * Reactor threads. Our order/cart {@code @Tool}s, however, read the signed-in user from the
 * servlet {@link SecurityContextHolder} (a {@link ThreadLocal}). Without propagation that context
 * is lost on the Reactor threads and the user-scoped tools would fail.
 *
 * <p>By registering a {@link io.micrometer.context.ThreadLocalAccessor} for the security context
 * and enabling Reactor's automatic context propagation, the security context captured on the
 * request thread (at subscription time) is restored on whichever thread actually executes the
 * stream and the tool calls.
 */
@Configuration
public class ReactiveContextPropagationConfig {

    public static final String SECURITY_CONTEXT_KEY = "ecom.security-context";

    @PostConstruct
    public void enableSecurityContextPropagation() {
        ContextRegistry.getInstance().registerThreadLocalAccessor(
                SECURITY_CONTEXT_KEY,
                SecurityContextHolder::getContext,
                (SecurityContext context) -> SecurityContextHolder.setContext(context),
                SecurityContextHolder::clearContext);

        // Make Reactor automatically capture/restore registered ThreadLocals across operators.
        Hooks.enableAutomaticContextPropagation();
    }
}
