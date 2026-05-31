package com.ecommerce.project.ai;

import com.ecommerce.project.model.Product;
import com.ecommerce.project.repositories.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Tools the chat assistant (LLM) can call to fetch live, authoritative product data straight
 * from the relational database. This lets the model answer precise questions about a specific
 * product's current price, discount and stock instead of relying only on retrieved context.
 */
@Component
public class ProductTools {

    private static final Logger log = LoggerFactory.getLogger(ProductTools.class);

    private final ProductRepository productRepository;

    public ProductTools(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Tool(description = "Look up products in the store by (part of) their name and return their "
            + "current price, discount and available stock. Use this when the user asks about a "
            + "specific product's price, discount or details.")
    public String findProductsByName(
            @ToolParam(description = "Full or partial product name to search for, e.g. 'yoga mat'") String name) {
        String toolName = "findProductsByName";
        try {
            AssistantToolLogging.logCall(log, toolName, "name=" + name, "n/a (product catalog)");

            if (name == null || name.isBlank()) {
                String result = "No product name was provided.";
                AssistantToolLogging.logResult(log, toolName, result);
                return result;
            }

            List<Product> products = productRepository.findByProductNameContainingIgnoreCase(name.trim());
            log.info("[ASSISTANT-TOOL] DB lookup tool={} name='{}' matches={}",
                    toolName, name, products.size());

            String result;
            if (products.isEmpty()) {
                result = "No product found matching '" + name + "' in the store.";
            } else {
                result = products.stream()
                        .limit(5)
                        .map(this::describe)
                        .collect(Collectors.joining("\n"));
            }
            AssistantToolLogging.logResult(log, toolName, result);
            return result;
        } catch (Exception ex) {
            AssistantToolLogging.logError(log, toolName, ex);
            throw ex;
        }
    }

    @Tool(description = "Check whether a specific product is in stock and how many units are "
            + "available. Use this when the user asks about availability or stock.")
    public String checkProductStock(
            @ToolParam(description = "Full or partial product name to check stock for") String name) {
        String toolName = "checkProductStock";
        try {
            AssistantToolLogging.logCall(log, toolName, "name=" + name, "n/a (product catalog)");

            if (name == null || name.isBlank()) {
                String result = "No product name was provided.";
                AssistantToolLogging.logResult(log, toolName, result);
                return result;
            }

            List<Product> products = productRepository.findByProductNameContainingIgnoreCase(name.trim());
            log.info("[ASSISTANT-TOOL] DB lookup tool={} name='{}' matches={}",
                    toolName, name, products.size());

            String result;
            if (products.isEmpty()) {
                result = "No product found matching '" + name + "' in the store.";
            } else {
                result = products.stream()
                        .limit(5)
                        .map(product -> {
                            int qty = product.getQuantity() == null ? 0 : product.getQuantity();
                            String availability = qty > 0 ? ("in stock (" + qty + " available)") : "out of stock";
                            return "%s is %s.".formatted(product.getProductName(), availability);
                        })
                        .collect(Collectors.joining("\n"));
            }
            AssistantToolLogging.logResult(log, toolName, result);
            return result;
        } catch (Exception ex) {
            AssistantToolLogging.logError(log, toolName, ex);
            throw ex;
        }
    }

    private String describe(Product product) {
        String categoryName = product.getCategory() != null ? product.getCategory().getCategoryName() : "Uncategorized";
        int qty = product.getQuantity() == null ? 0 : product.getQuantity();
        return "Product: %s | Category: %s | Price: Rs.%.2f | Special price: Rs.%.2f | Discount: %.0f%% | Stock: %d"
                .formatted(
                        product.getProductName(),
                        categoryName,
                        product.getPrice(),
                        product.getSpecialPrice(),
                        product.getDiscount(),
                        qty);
    }
}
