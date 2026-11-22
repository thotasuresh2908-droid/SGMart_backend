package com.sgmart.order;

import com.sgmart.customer.CustomerDtos.CustomerAdminView;
import com.sgmart.delivery.VehicleType;
import com.sgmart.payment.PaymentMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class OrderDtos {

    private OrderDtos() {
    }

    // ------------------------------------------------------------------ requests

    public record ItemRequest(
            @NotNull(message = "Choose a product.")
            Long productId,
            @NotNull(message = "Enter a quantity.")
            @Min(value = 1, message = "Quantity must be at least 1.")
            @Max(value = 50, message = "You can order up to 50 of one product. Call the store for more.")
            Integer qty) {
    }

    /** Checkout. Name, mobile and address are required (as in the prototype); GPS is optional. */
    public record PlaceOrderRequest(
            @NotBlank(message = "Enter the name of the person receiving the order.")
            @Size(min = 2, max = 100, message = "Name must be 2 to 100 characters.")
            String customerName,
            @NotBlank(message = "Enter a valid 10-digit mobile number.")
            String mobile,
            @NotBlank(message = "Enter your full address so the partner can find you.")
            @Size(min = 8, max = 500, message = "Enter your full address so the partner can find you.")
            String addressLine,
            @Size(max = 200, message = "Keep the landmark under 200 characters.")
            String landmark,
            @DecimalMin(value = "-90", message = "Location is not valid.")
            @DecimalMax(value = "90", message = "Location is not valid.")
            BigDecimal latitude,
            @DecimalMin(value = "-180", message = "Location is not valid.")
            @DecimalMax(value = "180", message = "Location is not valid.")
            BigDecimal longitude,
            @NotNull(message = "Choose Cash on delivery or UPI on delivery.")
            PaymentMethod paymentMethod,
            @NotEmpty(message = "Your cart is empty.")
            @Size(max = 50, message = "An order can have up to 50 different products.")
            List<@Valid ItemRequest> items,
            String captchaToken) {
    }

    /** "Add or remove items": the full list the customer wants. Products not listed are removed. */
    public record EditItemsRequest(
            @NotNull(message = "Send the items for the order.")
            @Size(max = 50, message = "An order can have up to 50 different products.")
            List<@Valid ItemRequest> items) {
    }

    public record CancelRequest(
            @NotBlank(message = "Choose a reason.")
            @Size(max = 255, message = "Keep the reason under 255 characters.")
            String reason) {
    }

    /** Guest "My orders": the tracking tokens saved in this browser. */
    public record TokensRequest(
            @NotNull(message = "Send the saved order tokens.")
            @Size(max = 50, message = "Too many tokens.")
            List<String> trackingTokens) {
    }

    public record UnavailableItem(
            @NotNull(message = "Choose the item.")
            Long itemId,
            @NotBlank(message = "Choose why it isn't available.")
            @Size(max = 100, message = "Keep the reason under 100 characters.")
            String reason) {
    }

    /**
     * Store accepts. Items listed are marked "Not available" with a reason; the rest are accepted.
     * customerCallConfirmed must be true for a New customer (the store called them first).
     */
    public record AcceptRequest(
            List<@Valid UnavailableItem> unavailableItems,
            boolean customerCallConfirmed) {
    }

    public record RejectRequest(
            @NotBlank(message = "Choose a reason. The customer will see it.")
            @Size(max = 255, message = "Keep the reason under 255 characters.")
            String reason) {
    }

    /** Send out for delivery with this partner (must be Available). */
    public record DispatchRequest(@NotNull(message = "Choose a delivery partner.") Long partnerId) {
    }

    /**
     * Mark as delivered and confirm payment. The amount is always the order total.
     * collectedByPartnerId is usually the assigned partner, but can be another one.
     */
    public record DeliverRequest(
            @NotNull(message = "Choose how the customer paid: Cash or UPI.")
            PaymentMode method,
            @NotBlank(message = "Enter the name of the person who paid.")
            @Size(min = 2, max = 100, message = "Name must be 2 to 100 characters.")
            String paidByName,
            @NotNull(message = "Choose who collected the payment.")
            Long collectedByPartnerId,
            @Size(max = 64, message = "Keep the UPI reference under 64 characters.")
            String upiRef) {
    }

    // ------------------------------------------------------------------ responses

    /** Shown as "Ravi Kumar is on the way" with a Call button. */
    public record PartnerInfo(Long id, String name, String mobile, VehicleType vehicleType) {
    }

    /** The payment trail: what the customer chose, who paid what to whom, who confirmed. */
    public record PaymentInfo(PaymentMode method, BigDecimal amount, String paidByName, String collectedBy,
                              String upiRef, String confirmedBy, Instant confirmedAt) {
    }

    public record OrderItemView(Long id, Long productId, String name, String unit, String icon, String imageUrl,
                                BigDecimal unitPrice, int qty, BigDecimal lineTotal, OrderItemStatus status,
                                String unavailableReason) {
    }

    /** One line of "Order updates". by = Customer / Store / System. */
    public record OrderUpdate(Instant at, String by, OrderEventType type, String message) {
    }

    /** Everything the customer's order card needs (and the base of the admin card). */
    public record OrderView(
            String orderNo,
            OrderStatus status,
            Instant placedAt,
            Instant etaAt,
            Instant acceptedAt,
            Instant outForDeliveryAt,
            Instant deliveredAt,
            Instant rejectedAt,
            Instant cancelledAt,
            String customerName,
            String customerMobile,
            String addressLine,
            String landmark,
            BigDecimal latitude,
            BigDecimal longitude,
            PaymentMethod paymentMethod,
            BigDecimal itemTotal,
            BigDecimal deliveryFee,
            BigDecimal total,
            int itemCount,
            List<OrderItemView> items,
            List<OrderUpdate> updates,
            boolean canEdit,
            boolean canCancel,
            String rejectReason,
            String cancelReason,
            String storePhone,
            PartnerInfo deliveryPartner,
            PaymentInfo payment) {
    }

    /** Admin order card: the order plus the customer badge (New customer, verified, blocked). */
    public record AdminOrderView(OrderView order, CustomerAdminView customer) {
    }

    /** Returned once at checkout. The web app saves trackingToken in the browser for guest tracking. */
    public record PlacedOrder(String orderNo, String trackingToken, OrderView order) {
    }

    public record ClaimResult(int linked, int skipped) {
    }

    /** The stat tiles at the top of Admin > Orders. */
    public record OrderCounts(long newOrders, long accepted, long outForDelivery, long deliveredToday,
                              BigDecimal deliveredSalesToday, long rejectedToday, long cancelledToday) {
    }
}
