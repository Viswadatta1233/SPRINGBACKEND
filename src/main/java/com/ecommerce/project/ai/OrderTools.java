package com.ecommerce.project.ai;

import com.ecommerce.project.model.Order;
import com.ecommerce.project.repositories.OrderRepository;
import com.ecommerce.project.util.AuthUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;

/**
 * Tools that let the assistant answer questions about the CURRENTLY LOGGED-IN customer's orders.
 * The user is resolved from the security context via {@link AuthUtil}, so a customer can only
 * ever see their own orders - never another user's.
 */
@Component
public class OrderTools {

    private static final Logger log = LoggerFactory.getLogger(OrderTools.class);

    private final OrderRepository orderRepository;
    private final AuthUtil authUtil;

    public OrderTools(OrderRepository orderRepository, AuthUtil authUtil) {
        this.orderRepository = orderRepository;
        this.authUtil = authUtil;
    }

    @Tool(description = "Get the logged-in customer's most recent orders, including order id, date, "
            + "status and total amount. Use this when the user asks about their orders, order "
            + "history, recent purchases or 'where is my order'.")
    @Transactional(readOnly = true)
    public String getMyRecentOrders() {
        String toolName = "getMyRecentOrders";
        try {
            String email = authUtil.loggedInEmail();
            AssistantToolLogging.logCall(log, toolName, "(none)", email);

            Page<Order> orders = orderRepository.findByEmail(email,
                    PageRequest.of(0, 10, Sort.by("orderDate").descending()));

            log.info("[ASSISTANT-TOOL] DB lookup tool={} email={} ordersFound={}",
                    toolName, email, orders.getTotalElements());

            String result;
            if (orders.isEmpty()) {
                result = "You have not placed any orders yet.";
            } else {
                result = orders.getContent().stream()
                        .map(this::summarize)
                        .collect(Collectors.joining("\n"));
            }
            AssistantToolLogging.logResult(log, toolName, result);
            return result;
        } catch (Exception ex) {
            AssistantToolLogging.logError(log, toolName, ex);
            throw ex;
        }
    }

    @Tool(description = "Get the status and details of one specific order by its order id, for the "
            + "logged-in customer. Use this when the user asks about a particular order number.")
    @Transactional(readOnly = true)
    public String getOrderStatus(
            @ToolParam(description = "The numeric order id the customer is asking about") Long orderId) {
        String toolName = "getOrderStatus";
        try {
            String email = authUtil.loggedInEmail();
            AssistantToolLogging.logCall(log, toolName, "orderId=" + orderId, email);

            String result = orderRepository.findById(orderId)
                    // Only reveal the order if it belongs to the logged-in customer.
                    .filter(order -> email.equalsIgnoreCase(order.getEmail()))
                    .map(this::summarize)
                    .orElse("No order with id " + orderId + " was found for your account.");

            log.info("[ASSISTANT-TOOL] DB lookup tool={} orderId={} email={} found={}",
                    toolName, orderId, email, !result.startsWith("No order"));

            AssistantToolLogging.logResult(log, toolName, result);
            return result;
        } catch (Exception ex) {
            AssistantToolLogging.logError(log, toolName, ex);
            throw ex;
        }
    }

    /**
     * Uses only scalar {@link Order} fields so this works on the Reactor tool thread when
     * {@code orderItems} is lazy and the persistence context is closed. Item count is loaded
     * inside {@link #getMyRecentOrders()} / {@link #getOrderStatus()} via {@code @Transactional}.
     */
    private String summarize(Order order) {
        double total = order.getTotalAmount() == null ? 0.0 : order.getTotalAmount();
        int itemCount = countOrderItems(order);
        return "Order #%d | Date: %s | Status: %s | Total: Rs.%.2f | Items: %d".formatted(
                order.getOrderId(),
                order.getOrderDate(),
                order.getOrderStatus(),
                total,
                itemCount);
    }

    private int countOrderItems(Order order) {
        try {
            return order.getOrderItems() == null ? 0 : order.getOrderItems().size();
        } catch (Exception ex) {
            // Fallback if lazy collection cannot be loaded (should not happen inside @Transactional).
            log.warn("[ASSISTANT-TOOL] Could not load orderItems for orderId={}: {}",
                    order.getOrderId(), ex.getMessage());
            return 0;
        }
    }
}
