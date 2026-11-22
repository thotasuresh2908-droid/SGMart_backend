package com.sgmart.order;

import com.sgmart.catalog.Category;
import com.sgmart.catalog.Product;
import com.sgmart.customer.Customer;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;

/** Products and orders shaped like the prototype's sample data. */
final class OrderTestData {

    static final String TOKEN = "test-token-123";
    static final Category DAIRY = new Category("Dairy, bread & eggs", "🥛", 20);

    private OrderTestData() {
    }

    static Product product(long id, String name, String price, int stock) {
        Product p = Product.create(DAIRY, name, "1 pc", new BigDecimal(price), new BigDecimal(price), stock, "🛒");
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    static Customer guest(long id, int deliveredOrders, boolean verified) {
        Customer c = Customer.guest("Priya Sharma", "9876543210");
        ReflectionTestUtils.setField(c, "id", id);
        c.setDeliveredOrdersCount(deliveredOrders);
        if (verified) {
            c.markVerifiedByStore(1L, Instant.parse("2026-11-01T05:00:00Z"));
        }
        return c;
    }

    /** SG1001 for customer 9: 2 × Toned Milk (₹28), 1 × Whole Wheat Bread (₹45). Items get ids 11, 12, ... */
    static CustomerOrder placedOrder(Object[]... lines) {
        CustomerOrder o = CustomerOrder.place(TrackingTokens.hash(TOKEN), 9L, "Priya Sharma", "9876543210",
                "Flat 302, Green Residency, Road No. 12", null, null, null, PaymentMethod.CASH_ON_DELIVERY,
                Instant.parse("2026-11-12T03:00:00Z"));
        ReflectionTestUtils.setField(o, "id", 1L);
        o.assignOrderNo();
        long itemId = 11;
        for (Object[] line : lines) {
            OrderItem item = new OrderItem(o, (Long) line[0], (String) line[1], "1 pc", "🛒",
                    new BigDecimal((String) line[2]), (Integer) line[3]);
            ReflectionTestUtils.setField(item, "id", itemId++);
            o.addItem(item);
        }
        o.recalculateTotals(t -> t.compareTo(new BigDecimal("199")) >= 0 ? BigDecimal.ZERO : new BigDecimal("25.00"));
        return o;
    }

    static Object[] line(long productId, String name, String price, int qty) {
        return new Object[]{productId, name, price, qty};
    }
}
