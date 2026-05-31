package com.ecommerce.project.ai;

import java.util.regex.Pattern;

/**
 * Detects user questions about account-specific data (cart, orders) so RAG product
 * retrieval can be skipped — those questions must be answered via tools, not catalog search.
 */
public final class AssistantQuerySupport {

    private static final Pattern ACCOUNT_QUERY = Pattern.compile(
            "\\b(cart|shopping cart|my cart|order|orders|purchase|purchases|checkout|"
                    + "shipment|shipping|delivery|tracking|track my|order history|order status|"
                    + "what did i buy|recent orders)\\b",
            Pattern.CASE_INSENSITIVE);

    private AssistantQuerySupport() {
    }

    public static boolean isAccountQuery(String text) {
        return text != null && ACCOUNT_QUERY.matcher(text).find();
    }
}
