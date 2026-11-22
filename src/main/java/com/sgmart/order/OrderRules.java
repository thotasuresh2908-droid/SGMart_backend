package com.sgmart.order;

import com.sgmart.common.ApiException;
import com.sgmart.common.Money;
import com.sgmart.customer.Customer;
import com.sgmart.order.OrderDtos.ItemRequest;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small rules shared by the customer and admin order services. */
final class OrderRules {

    static final int MAX_QTY_PER_PRODUCT = 50;

    private OrderRules() {
    }

    /** Same product twice in a request? Add the quantities (the database allows one line per product). */
    static Map<Long, Integer> mergeItems(List<ItemRequest> items) {
        Map<Long, Integer> qty = new LinkedHashMap<>();
        for (ItemRequest i : items) {
            qty.merge(i.productId(), i.qty(), Integer::sum);
        }
        qty.forEach((id, q) -> {
            if (q > MAX_QTY_PER_PRODUCT) {
                throw ApiException.badRequest("QTY_TOO_HIGH",
                        "You can order up to " + MAX_QTY_PER_PRODUCT + " of one product. Call the store for more.", "items");
            }
        });
        return qty;
    }

    /**
     * New customers' first order is capped (store_settings.first_order_cod_limit; empty = no cap).
     * The amount is a business rule still to be confirmed, so it is never hard-coded.
     */
    static void checkFirstOrderCap(Customer customer, BigDecimal orderTotal, BigDecimal cap, String storePhone) {
        if (cap != null && customer.isNewCustomer() && orderTotal.compareTo(cap) > 0) {
            throw ApiException.badRequest("FIRST_ORDER_LIMIT",
                    "Your first order can be up to " + Money.format(cap) + ". Remove a few items, or call the store on "
                            + storePhone + " to order more.", null);
        }
    }

    /** Customer edit or cancel: only while the store hasn't accepted yet. */
    static void requireWaitingForCustomer(CustomerOrder o, String storePhone) {
        switch (o.getStatus()) {
            case PLACED -> { }
            case CANCELLED -> throw ApiException.conflict("ORDER_CANCELLED", "This order was already cancelled.");
            case REJECTED -> throw ApiException.conflict("ORDER_REJECTED", "The store couldn't accept this order.");
            default -> throw ApiException.conflict("ORDER_LOCKED",
                    "The store has already accepted this order, so it can't be changed. Call the store on "
                            + storePhone + " for help.");
        }
    }

    /** Store accept or reject: only new orders. */
    static void requireNewForStore(CustomerOrder o) {
        switch (o.getStatus()) {
            case PLACED -> { }
            case CANCELLED -> throw ApiException.conflict("ORDER_CANCELLED", "The customer cancelled this order.");
            default -> throw ApiException.conflict("ORDER_NOT_NEW",
                    "This order was already " + o.getStatus().name().toLowerCase().replace('_', ' ') + ".");
        }
    }

    /** Send out for delivery: only accepted orders. */
    static void requireAccepted(CustomerOrder o) {
        switch (o.getStatus()) {
            case ACCEPTED -> { }
            case PLACED -> throw ApiException.conflict("ORDER_NOT_READY", "Accept the order first.");
            case OUT_FOR_DELIVERY -> throw ApiException.conflict("ORDER_NOT_READY", "This order is already out for delivery.");
            case DELIVERED -> throw ApiException.conflict("ORDER_NOT_READY", "This order was already delivered.");
            case CANCELLED -> throw ApiException.conflict("ORDER_CANCELLED", "The customer cancelled this order.");
            case REJECTED -> throw ApiException.conflict("ORDER_NOT_READY", "This order was rejected.");
        }
    }

    /** Mark as delivered: only orders that are out for delivery. */
    static void requireOutForDelivery(CustomerOrder o) {
        switch (o.getStatus()) {
            case OUT_FOR_DELIVERY -> { }
            case DELIVERED -> throw ApiException.conflict("ORDER_NOT_OUT", "This order was already delivered.");
            default -> throw ApiException.conflict("ORDER_NOT_OUT", "Send the order out for delivery first.");
        }
    }

    static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
