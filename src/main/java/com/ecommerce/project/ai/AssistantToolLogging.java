package com.ecommerce.project.ai;

import org.slf4j.Logger;

/**
 * Consistent log lines for assistant {@code @Tool} invocations so you can grep the console for
 * {@code [ASSISTANT-TOOL]} and trace call → result without digging through Spring AI internals.
 */
final class AssistantToolLogging {

    private AssistantToolLogging() {
    }

    static void logCall(Logger log, String toolName, String inputs, String scopedUser) {
        log.info("[ASSISTANT-TOOL] CALL tool={} scopedUser={} inputs={}",
                toolName, scopedUser, inputs);
    }

    static void logResult(Logger log, String toolName, String result) {
        log.info("[ASSISTANT-TOOL] RESULT tool={} result={}", toolName, result);
    }

    static void logError(Logger log, String toolName, Exception ex) {
        log.error("[ASSISTANT-TOOL] ERROR tool={} message={}", toolName, ex.getMessage(), ex);
    }
}
