package com.sgmart.order;

import com.sgmart.common.ApiException;
import com.sgmart.customer.CustomerService;
import com.sgmart.inventory.StockReason;
import com.sgmart.inventory.StockService;
import com.sgmart.order.OrderDtos.AcceptRequest;
import com.sgmart.order.OrderDtos.AdminOrderView;
import com.sgmart.order.OrderDtos.UnavailableItem;
import com.sgmart.store.StoreSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.sgmart.order.OrderTestData.guest;
import static com.sgmart.order.OrderTestData.line;
import static com.sgmart.order.OrderTestData.placedOrder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminOrderServiceTest {

    private final CustomerOrderRepository orders = mock(CustomerOrderRepository.class);
    private final OrderEventRepository events = mock(OrderEventRepository.class);
    private final StockService stock = mock(StockService.class);
    private final CustomerService customers = mock(CustomerService.class);
    private final StoreSettingsService store = mock(StoreSettingsService.class);
    private final OrderMapper mapper = mock(OrderMapper.class);
    private final AdminOrderService service = new AdminOrderService(orders, events, stock, customers, store, mapper,
            mock(NamedParameterJdbcTemplate.class), mock(ApplicationEventPublisher.class),
            Clock.fixed(Instant.parse("2026-11-12T03:05:00Z"), ZoneOffset.UTC));

    private CustomerOrder order;

    @BeforeEach
    void setUp() {
        // Items: 11 = 2 × Toned Milk ₹28, 12 = 1 × Whole Wheat Bread ₹45, 13 = 1 × Green Tea ₹140 -> ₹241, free delivery
        order = placedOrder(line(1, "Toned Milk", "28.00", 2), line(2, "Whole Wheat Bread", "45.00", 1),
                line(3, "Green Tea", "140.00", 1));
        when(orders.findByOrderNoForUpdate("SG1001")).thenReturn(Optional.of(order));
        when(customers.get(9L)).thenReturn(guest(9, 1, true));   // returning customer
        when(store.deliveryFeeFor(any())).thenAnswer(inv -> ((BigDecimal) inv.getArgument(0))
                .compareTo(new BigDecimal("199")) >= 0 ? BigDecimal.ZERO : new BigDecimal("25.00"));
        when(mapper.toAdminViews(any())).thenReturn(Arrays.asList((AdminOrderView) null));
    }

    private List<OrderEvent> savedEvents(int count) {
        ArgumentCaptor<OrderEvent> captor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(events, times(count)).save(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void acceptTwoOfThreeItemsReturnsStockAndRecalculatesTotal() {
        assertThat(order.getTotal()).isEqualByComparingTo("241.00");

        service.accept("SG1001", new AcceptRequest(List.of(new UnavailableItem(13L, "Out of stock")), false), 7L);

        verify(stock).returnForOrder(Map.of(3L, 1), 1L, StockReason.ITEM_UNAVAILABLE, 7L);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.ACCEPTED);
        assertThat(order.getItemTotal()).isEqualByComparingTo("101.00");
        assertThat(order.getDeliveryFee()).isEqualByComparingTo("25.00");     // dropped below the free-delivery amount
        assertThat(order.getTotal()).isEqualByComparingTo("126.00");
        assertThat(order.itemForProduct(3L).orElseThrow().getUnavailableReason()).isEqualTo("Out of stock");
        List<OrderEvent> saved = savedEvents(2);
        assertThat(saved.get(0).getMessage()).isEqualTo("Not available: 1 × Green Tea (Out of stock). New total ₹126.");
        assertThat(saved.get(1).getEventType()).isEqualTo(OrderEventType.ACCEPTED);
    }

    @Test
    void acceptAllItemsTouchesNoStock() {
        service.accept("SG1001", new AcceptRequest(null, false), 7L);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.ACCEPTED);
        assertThat(order.getTotal()).isEqualByComparingTo("241.00");
        verify(stock, never()).returnForOrder(anyMap(), anyLong(), any(), any());
    }

    @Test
    void newCustomerMustBeCalledFirst() {
        when(customers.get(9L)).thenReturn(guest(9, 0, false));
        assertThatThrownBy(() -> service.accept("SG1001", new AcceptRequest(null, false), 7L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Call 9876543210")
                .extracting("code").isEqualTo("CALL_NEW_CUSTOMER");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PLACED);
    }

    @Test
    void confirmingTheCallVerifiesTheNumber() {
        when(customers.get(9L)).thenReturn(guest(9, 0, false));
        service.accept("SG1001", new AcceptRequest(null, true), 7L);
        verify(customers).markVerified(9L, 7L);
        assertThat(savedEvents(2).get(0).getEventType()).isEqualTo(OrderEventType.CUSTOMER_VERIFIED);
    }

    @Test
    void cantAcceptWhenNothingIsAvailable() {
        AcceptRequest none = new AcceptRequest(List.of(new UnavailableItem(11L, "Out of stock"),
                new UnavailableItem(12L, "Out of stock"), new UnavailableItem(13L, "Expired")), false);
        assertThatThrownBy(() -> service.accept("SG1001", none, 7L))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("NOTHING_TO_ACCEPT");
    }

    @Test
    void itemFromAnotherOrderIsRefused() {
        assertThatThrownBy(() -> service.accept("SG1001",
                new AcceptRequest(List.of(new UnavailableItem(999L, "Out of stock")), false), 7L))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("INVALID_ITEM");
    }

    @Test
    void orderCancelledByTheCustomerCantBeAccepted() {
        order.cancel("Ordered by mistake", Instant.now());
        assertThatThrownBy(() -> service.accept("SG1001", new AcceptRequest(null, true), 7L))
                .isInstanceOf(ApiException.class)
                .hasMessage("The customer cancelled this order.");
    }

    @Test
    void rejectReturnsAllStockWithTheReason() {
        service.reject("SG1001", "Address is outside our delivery area", 7L);
        verify(stock).returnForOrder(Map.of(1L, 2, 2L, 1, 3L, 1), 1L, StockReason.ORDER_REJECTED, 7L);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(order.getRejectReason()).isEqualTo("Address is outside our delivery area");
    }

    @Test
    void acceptedOrderCantBeRejected() {
        order.accept(Instant.now());
        assertThatThrownBy(() -> service.reject("SG1001", "Store closing", 7L))
                .isInstanceOf(ApiException.class)
                .hasMessage("This order was already accepted.");
    }
}
