package com.ecommerce.project.ai;

import com.ecommerce.project.model.Cart;
import com.ecommerce.project.repositories.CartRepository;
import com.ecommerce.project.util.AuthUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;

/**
 * Tool that lets the assistant report what is in the CURRENTLY LOGGED-IN customer's cart.
 * The cart is looked up by the user's email from the security context, so customers only
 * ever see their own cart.
 */
@Component
public class CartTools {

    private static final Logger log = LoggerFactory.getLogger(CartTools.class);

    private final CartRepository cartRepository;
    private final AuthUtil authUtil;

    public CartTools(CartRepository cartRepository, AuthUtil authUtil) {
        this.cartRepository = cartRepository;
        this.authUtil = authUtil;
    }

    @Tool(description = "Get a summary of the items currently in the logged-in customer's shopping "
            + "cart, including product names, quantities and the cart total. Use this when the user "
            + "asks what is in their cart or about their cart total.")
    @Transactional(readOnly = true)
    public String getMyCartSummary() {
        String toolName = "getMyCartSummary";
        try {
            String email = authUtil.loggedInEmail();
            AssistantToolLogging.logCall(log, toolName, "(none)", email);

            Cart cart = cartRepository.findCartByEmailWithItems(email);
            int itemCount = (cart == null || cart.getCartItems() == null) ? 0 : cart.getCartItems().size();
            log.info("[ASSISTANT-TOOL] DB lookup tool={} email={} cartExists={} itemCount={}",
                    toolName, email, cart != null, itemCount);

            String result;
            if (cart == null || cart.getCartItems() == null || cart.getCartItems().isEmpty()) {
                result = "Your shopping cart is currently empty.";
            } else {
                String items = cart.getCartItems().stream()
                        .map(item -> "- %s x%d - Rs.%.2f each".formatted(
                                item.getProduct() != null ? item.getProduct().getProductName() : "Unknown product",
                                item.getQuantity() == null ? 0 : item.getQuantity(),
                                item.getProductPrice()))
                        .collect(Collectors.joining("\n"));

                double total = cart.getTotalPrice() == null ? 0.0 : cart.getTotalPrice();
                result = "Your cart total is Rs.%.2f and contains:\n%s".formatted(total, items);
            }
            AssistantToolLogging.logResult(log, toolName, result);
            return result;
        } catch (Exception ex) {
            AssistantToolLogging.logError(log, toolName, ex);
            throw ex;
        }
    }
}
