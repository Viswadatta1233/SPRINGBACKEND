package com.ecommerce.project.ai;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.generation.augmentation.QueryAugmenter;

import java.util.List;

/**
 * Keeps the original user message for cart/order questions instead of wrapping it in a
 * product RAG template. Product-discovery questions still use contextual augmentation.
 */
public class IntentAwareQueryAugmenter implements QueryAugmenter {

    private final QueryAugmenter productAugmenter;

    public IntentAwareQueryAugmenter() {
        this.productAugmenter = ContextualQueryAugmenter.builder()
                .allowEmptyContext(true)
                .build();
    }

    @Override
    public Query augment(Query query, List<Document> documents) {
        if (AssistantQuerySupport.isAccountQuery(query.text())) {
            return query;
        }
        return productAugmenter.augment(query, documents);
    }
}
