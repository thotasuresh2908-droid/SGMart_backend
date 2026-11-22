package com.sgmart.order;

import com.sgmart.catalog.Product;
import com.sgmart.catalog.ProductRepository;
import com.sgmart.common.ApiException;
import com.sgmart.customer.Customer;
import com.sgmart.customer.CustomerService;
import com.sgmart.inventory.StockReason;
import com.sgmart.inventory.StockService;
import com.sgmart.order.OrderDtos.EditItemsRequest;
import com.sgmart.order.OrderDtos.ItemRequest;
import com.sgmart.order.OrderDtos.PlaceOrderRequest;
import com.sgmart.order.OrderDtos.PlacedOrder;
import com.sgmart.store.StoreSettings;
import com.sgmart.store.StoreSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.sgmart.order.OrderTestData.guest;
import static com.sgmart.order.OrderTestData.line;
import static com.sgmart.order.OrderTestData.placedOrder;
import static com.sgmart.order.OrderTestData.product;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceTest {

    private final CustomerOrderRepository orders = mock(CustomerOrderRepository.class);
    private final OrderEventRepository events = mock(OrderEventRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final StockService stock = mock(StockService.class);
    private final CustomerService customers = mock(CustomerService.class);
    private final StoreSettingsService store = mock(StoreSettingsService.class);
    private final OrderMapper mapper = mock(OrderMapper.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final OrderService service = new OrderService(orders, events, products, stock, customers, store, mapper,
            publisher, Clock.fixed(Instant.parse("2026-11-12T03:00:00Z"), ZoneOffset.UTC));

    private final Product milk = product(1, "Toned Milk", "28.00", 80);
    private final Product bread = product(2, "Whole Wheat Bread", "45.00", 30);
    private final Product cola = product(3, "Cola", "40.00", 48);
    private final Product rice = product(4, "Basmati Rice", "110.00", 30);
    private final StoreSettings settings = StoreSettings.defaults("9000012345");

    @BeforeEach
    void setUp() {
        when(store.current()).thenReturn(settings);
        when(store.deliveryFeeFor(any())).thenAnswer(inv -> ((BigDecimal) inv.getArgument(0))
                .compareTo(new BigDecimal("199")) >= 0 ? BigDecimal.ZERO : new BigDecimal("25.00"));
        when(products.findAllById(any())).thenReturn(List.of(milk, bread, cola, rice));
        when(products.findLive(3L)).thenReturn(Optional.of(cola));
        when(orders.save(any(CustomerOrder.class))).thenAnswer(inv -> {
            CustomerOrder o = inv.getArgument(0);
            ReflectionTestUtils.setField(o, "id", 1L);
            return o;
        });
    }

    private PlaceOrderRequest checkout(ItemRequest... items) {
        return new PlaceOrderRequest(" Priya Sharma ", "+91 98765 43210", "Flat 302, Green Residency, Road No. 12",
                "", null, null, PaymentMethod.CASH_ON_DELIVERY, List.of(items), null);
    }

    private CustomerOrder captureSavedOrder() {
        ArgumentCaptor<CustomerOrder> captor = ArgumentCaptor.forClass(CustomerOrder.class);
        verify(orders).save(captor.capture());
        return captor.getValue();
    }

    private OrderEvent lastEvent() {
        ArgumentCaptor<OrderEvent> captor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(events, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }

    // ------------------------------------------------------------------ place

    @Test
    void guestOrderGetsTotalsFeeNumberStockAndToken() {
        when(customers.findOrCreateGuest("Priya Sharma", "9876543210")).thenReturn(guest(9, 0, false));

        PlacedOrder placed = service.place(checkout(new ItemRequest(1L, 2), new ItemRequest(2L, 1)), null);

        CustomerOrder o = captureSavedOrder();
        assertThat(placed.orderNo()).isEqualTo("SG1001");
        assertThat(o.getItemTotal()).isEqualByComparingTo("101.00");
        assertThat(o.getDeliveryFee()).isEqualByComparingTo("25.00");
        assertThat(o.getTotal()).isEqualByComparingTo("126.00");
        assertThat(o.getCustomerMobile()).isEqualTo("9876543210");
        assertThat(o.getCustomerName()).isEqualTo("Priya Sharma");
        assertThat(o.getLandmark()).isNull();
        assertThat(TrackingTokens.matches(placed.trackingToken(), o.getTrackingTokenHash())).isTrue();
        verify(stock).takeForOrder(Map.of(1L, 2, 2L, 1), 1L, StockReason.ORDER_PLACED);
        assertThat(lastEvent().getMessage()).isEqualTo("Order placed: 3 items, ₹126.");
        verify(publisher).publishEvent(any(OrderChangedEvent.class));
    }

    @Test
    void sameProductTwiceBecomesOneLine() {
        when(customers.findOrCreateGuest(anyString(), anyString())).thenReturn(guest(9, 0, false));
        service.place(checkout(new ItemRequest(1L, 1), new ItemRequest(1L, 2)), null);
        assertThat(captureSavedOrder().getItems()).singleElement()
                .satisfies(i -> assertThat(i.getQty()).isEqualTo(3));
    }

    @Test
    void newCustomersFirstOrderIsCapped() {
        when(customers.findOrCreateGuest(anyString(), anyString())).thenReturn(guest(9, 0, false));
        assertThatThrownBy(() -> service.place(checkout(new ItemRequest(4L, 11)), null))   // ₹1,210
                .isInstanceOf(ApiException.class)
                .hasMessage("Your first order can be up to ₹1000. Remove a few items, or call the store on 9000012345 to order more.")
                .extracting("code").isEqualTo("FIRST_ORDER_LIMIT");
        verify(orders, never()).save(any());
        verify(stock, never()).takeForOrder(anyMap(), anyLong(), any());
    }

    @Test
    void capFollowsStoreSettingsAndCanBeTurnedOff() {
        when(customers.findOrCreateGuest(anyString(), anyString())).thenReturn(guest(9, 0, false));
        settings.setFirstOrderCodLimit(null);
        service.place(checkout(new ItemRequest(4L, 11)), null);
        assertThat(captureSavedOrder().getTotal()).isEqualByComparingTo("1210.00");
    }

    @Test
    void returningCustomerHasNoCap() {
        when(customers.findOrCreateGuest(anyString(), anyString())).thenReturn(guest(9, 1, false));
        service.place(checkout(new ItemRequest(4L, 11)), null);
        verify(stock).takeForOrder(Map.of(4L, 11), 1L, StockReason.ORDER_PLACED);
    }

    @Test
    void blockedNumberCantOrder() {
        Customer blocked = guest(9, 3, true);
        blocked.block("Refused 3 deliveries", Instant.now());
        when(customers.findOrCreateGuest(anyString(), anyString())).thenReturn(blocked);
        assertThatThrownBy(() -> service.place(checkout(new ItemRequest(1L, 1)), null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("call the store on 9000012345")
                .extracting("code").isEqualTo("CUSTOMER_BLOCKED");
    }

    @Test
    void hiddenProductCantBeOrdered() {
        cola.setActive(false);
        assertThatThrownBy(() -> service.place(checkout(new ItemRequest(3L, 1)), null))
                .isInstanceOf(ApiException.class)
                .hasMessage("Cola is no longer available. Remove it from your cart.");
    }

    @Test
    void signedInShopperGetsTheNumberOnTheirAccount() {
        when(customers.attachMobile(5L, "9876543210")).thenReturn(guest(5, 2, true));
        service.place(checkout(new ItemRequest(1L, 1)), 5L);
        verify(customers).attachMobile(5L, "9876543210");
        verify(customers, never()).findOrCreateGuest(anyString(), anyString());
    }

    // ------------------------------------------------------------------ edit and cancel

    @Test
    void editAddsRemovesAndChangesQuantitiesWithOnlyTheStockDifference() {
        CustomerOrder o = placedOrder(line(1, "Toned Milk", "28.00", 2), line(2, "Whole Wheat Bread", "45.00", 1));
        when(orders.findByOrderNoForUpdate("SG1001")).thenReturn(Optional.of(o));
        when(customers.get(9L)).thenReturn(guest(9, 0, false));

        service.editItems("SG1001", OrderTestData.TOKEN, null,
                new EditItemsRequest(List.of(new ItemRequest(1L, 3), new ItemRequest(3L, 1))));

        verify(stock).returnForOrder(Map.of(2L, 1), 1L, StockReason.ORDER_EDITED, null);
        verify(stock).takeForOrder(Map.of(1L, 1, 3L, 1), 1L, StockReason.ORDER_EDITED);
        assertThat(o.getTotal()).isEqualByComparingTo("149.00");     // 3 × 28 + 40 + 25 delivery
        assertThat(o.itemForProduct(2L).orElseThrow().getStatus()).isEqualTo(OrderItemStatus.REMOVED_BY_CUSTOMER);
        assertThat(lastEvent().getMessage())
                .isEqualTo("Toned Milk 2 → 3, removed Whole Wheat Bread, added 1 × Cola. New total ₹149.");
    }

    @Test
    void editAfterTheStoreAcceptedIsRefused() {
        CustomerOrder o = placedOrder(line(1, "Toned Milk", "28.00", 2));
        o.accept(Instant.now());
        when(orders.findByOrderNoForUpdate("SG1001")).thenReturn(Optional.of(o));
        assertThatThrownBy(() -> service.editItems("SG1001", OrderTestData.TOKEN, null,
                new EditItemsRequest(List.of(new ItemRequest(1L, 1)))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already accepted")
                .extracting("code").isEqualTo("ORDER_LOCKED");
    }

    @Test
    void wrongTokenLooksLikeNoSuchOrder() {
        when(orders.findByOrderNoForUpdate("SG1001")).thenReturn(Optional.of(placedOrder(line(1, "Toned Milk", "28.00", 2))));
        assertThatThrownBy(() -> service.cancel("SG1001", "someone-elses-token", null, "Ordered by mistake"))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("NOT_FOUND");
    }

    @Test
    void ownerCanUseTheirSessionInsteadOfAToken() {
        CustomerOrder o = placedOrder(line(1, "Toned Milk", "28.00", 2));
        when(orders.findByOrderNo("SG1001")).thenReturn(Optional.of(o));
        service.view("SG1001", null, 9L);
        verify(mapper).toView(o);
    }

    @Test
    void removingEverythingIsRefused() {
        when(orders.findByOrderNoForUpdate("SG1001")).thenReturn(Optional.of(placedOrder(line(1, "Toned Milk", "28.00", 2))));
        assertThatThrownBy(() -> service.editItems("SG1001", OrderTestData.TOKEN, null, new EditItemsRequest(List.of())))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("ORDER_EMPTY");
    }

    @Test
    void cancelReturnsAllStockAndKeepsTheReason() {
        CustomerOrder o = placedOrder(line(1, "Toned Milk", "28.00", 2), line(2, "Whole Wheat Bread", "45.00", 1));
        when(orders.findByOrderNoForUpdate("SG1001")).thenReturn(Optional.of(o));

        service.cancel("SG1001", OrderTestData.TOKEN, null, "Ordered by mistake");

        verify(stock).returnForOrder(Map.of(1L, 2, 2L, 1), 1L, StockReason.ORDER_CANCELLED, null);
        assertThat(o.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(o.getCancelReason()).isEqualTo("Ordered by mistake");
        assertThat(lastEvent().getMessage()).isEqualTo("Cancelled the order: Ordered by mistake.");
    }

    @Test
    void cancellingTwiceSaysItWasAlreadyCancelled() {
        CustomerOrder o = placedOrder(line(1, "Toned Milk", "28.00", 2));
        o.cancel("Ordered by mistake", Instant.now());
        when(orders.findByOrderNoForUpdate("SG1001")).thenReturn(Optional.of(o));
        assertThatThrownBy(() -> service.cancel("SG1001", OrderTestData.TOKEN, null, "Again"))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("ORDER_CANCELLED");
        verify(stock, never()).returnForOrder(anyMap(), eq(1L), any(), any());
    }
}
