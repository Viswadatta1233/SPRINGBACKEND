package com.ecommerce.project.service;

import com.ecommerce.project.model.Product;

/**
 * Keeps the pgvector {@code vector_store} table in sync with the relational {@code products}
 * table. Each product is stored as a single embedded {@link org.springframework.ai.document.Document}
 * so it can be found via semantic similarity search and used as RAG context.
 */
public interface ProductIndexService {

    /** Embed a single product and upsert it into the vector store (id = productId). */
    void index(Product product);

    /** Remove a product's embedding from the vector store. */
    void remove(Long productId);

    /** Embed every product currently in the database. Returns how many were indexed. */
    long reindexAll();
}
