package com.ecommerce.project.ai;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;

import java.util.List;

/**
 * Skips vector search for cart/order questions — those must be answered with account tools,
 * not product catalog similarity search.
 */
public class IntentAwareDocumentRetriever implements DocumentRetriever {

    private final DocumentRetriever delegate;

    public IntentAwareDocumentRetriever(DocumentRetriever delegate) {
        this.delegate = delegate;
    }

    @Override
    public List<Document> retrieve(Query query) {
        if (AssistantQuerySupport.isAccountQuery(query.text())) {
            return List.of();
        }
        return delegate.retrieve(query);
    }
}
