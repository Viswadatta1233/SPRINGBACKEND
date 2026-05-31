package com.ecommerce.project.service;

import com.ecommerce.project.model.Product;
import com.ecommerce.project.repositories.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ProductIndexServiceImpl implements ProductIndexService {

    private static final Logger log = LoggerFactory.getLogger(ProductIndexServiceImpl.class);

    private final VectorStore vectorStore;
    private final ProductRepository productRepository;

    public ProductIndexServiceImpl(VectorStore vectorStore, ProductRepository productRepository) {
        this.vectorStore = vectorStore;
        this.productRepository = productRepository;
    }

    @Override
    public void index(Product product) {
        // Indexing is best-effort: a vector-store/Ollama hiccup must never break the
        // core product CRUD flow, so we log and move on instead of propagating.
        try {
            Document document = toDocument(product);
            log.debug("Indexing product {} -> vector-store doc id {} (content {} chars)",
                    product.getProductId(), document.getId(), document.getText().length());
            vectorStore.add(List.of(document));
            log.debug("Indexed product {} into vector store", product.getProductId());
        } catch (Exception e) {
            log.warn("Failed to index product {} into vector store: {}", product.getProductId(), e.getMessage());
            log.debug("Indexing failure stack trace", e);
        }
    }

    @Override
    public void remove(Long productId) {
        try {
            String docId = documentId(productId);
            log.debug("Removing product {} -> vector-store doc id {}", productId, docId);
            vectorStore.delete(List.of(docId));
            log.debug("Removed product {} from vector store", productId);
        } catch (Exception e) {
            log.warn("Failed to remove product {} from vector store: {}", productId, e.getMessage());
            log.debug("Removal failure stack trace", e);
        }
    }

    @Override
    public long reindexAll() {
        List<Product> products = productRepository.findAll();
        List<Document> documents = products.stream()
                .map(this::toDocument)
                .toList();

        if (!documents.isEmpty()) {
            // PgVectorStore upserts by document id, so re-running this safely refreshes embeddings.
            vectorStore.add(documents);
        }
        log.info("Re-indexed {} products into the vector store", documents.size());
        return documents.size();
    }

    /**
     * Builds the embeddable {@link Document} for a product. The natural-language {@code text} is
     * what actually gets embedded and matched semantically, while {@code metadata} carries the
     * structured fields we use to hydrate full results from the relational DB afterwards.
     */
    private Document toDocument(Product product) {
        String categoryName = product.getCategory() != null
                ? product.getCategory().getCategoryName()
                : "Uncategorized";

        String content = """
                Product name: %s
                Category: %s
                Description: %s
                Price: %.2f
                Special price (after discount): %.2f
                """.formatted(
                product.getProductName(),
                categoryName,
                product.getDescription(),
                product.getPrice(),
                product.getSpecialPrice());

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("productId", product.getProductId());
        metadata.put("productName", product.getProductName());
        metadata.put("category", categoryName);
        metadata.put("price", product.getPrice());
        metadata.put("specialPrice", product.getSpecialPrice());

        return Document.builder()
                .id(documentId(product.getProductId()))
                .text(content)
                .metadata(metadata)
                .build();
    }

    /**
     * pgvector stores the document id in a {@code uuid} column, so we cannot use the raw
     * numeric productId. Instead we derive a deterministic (name-based) UUID from the productId:
     * the same product always maps to the same UUID, which keeps upserts and deletes correct.
     * The real productId is preserved in the document metadata for hydrating search results.
     */
    private String documentId(Long productId) {
        return UUID.nameUUIDFromBytes(("product-" + productId).getBytes(StandardCharsets.UTF_8)).toString();
    }
}
