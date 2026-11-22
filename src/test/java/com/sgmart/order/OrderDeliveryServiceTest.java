package com.sgmart.order;

import com.sgmart.auth.AdminRole;
import com.sgmart.auth.AdminUserDtos.AdminUserView;
import com.sgmart.auth.AdminUserService;
import com.sgmart.common.ApiException;
import com.sgmart.customer.CustomerService;
import com.sgmart.delivery.DeliveryPartner;
import com.sgmart.delivery.DeliveryPartnerRepository;
import com.sgmart.delivery.PartnerStatus;
import com.sgmart.delivery.VehicleType;
import com.sgmart.order.OrderDtos.AdminOrderView;
import com.sgmart.order.OrderDtos.DeliverRequest;
import com.sgmart.payment.Payment;
import com.sgmart.payment.PaymentMode;
import com.sgmart.payment.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static com.sgmart.order.OrderTestData.line;
import static com.sgmart.order.OrderTestData.placedOrder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderDeliveryServiceTest {

    private final CustomerOrderRepository orders = mock(CustomerOrderRepository.class);
    private final OrderEventRepository events = mock(OrderEventRepository.class);
    private final DeliveryPartnerRepository partners = mock(DeliveryPartnerRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final CustomerService customers = mock(CustomerService.class);
    private final AdminUserService admins = mock(AdminUserService.class);
    private final OrderMapper mapper = mock(OrderMapper.class);
    private final OrderDeliveryService service = new OrderDeliveryService(orders, events, partners, payments,
            customers, admins, mapper, mock(ApplicationEventPublisher.class),
            Clock.fixed(Instant.parse("2026-11-12T03:15:00Z"), ZoneOffset.UTC));

    private CustomerOrder order;
    private DeliveryPartner ravi;
    private DeliveryPartner salma;

    private DeliveryPartner partner(long id, String name, String mobile, VehicleType v, PartnerStatus status) {
        DeliveryPartner p = new DeliveryPartner(name, mobile, v);
        p.setStatus(status);
        ReflectionTestUtils.setField(p, "id", id);
        when(partners.findByIdForUpdate(id)).thenReturn(Optional.of(p));
        when(partners.findById(id)).thenReturn(Optional.of(p));
        return p;
    }

    @BeforeEach
    void setUp() {
        order = placedOrder(line(1, "Toned Milk", "28.00", 2), line(2, "Basmati Rice", "110.00", 2));   // ₹276, free delivery
        order.accept(Instant.parse("2026-11-12T03:05:00Z"));
        when(orders.findByOrderNoForUpdate("SG1001")).thenReturn(Optional.of(order));
        ravi = partner(1, "Ravi Kumar", "9848012345", VehicleType.BIKE, PartnerStatus.AVAILABLE);
        salma = partner(2, "Salma Begum", "9949023456", VehicleType.SCOOTER, PartnerStatus.ON_DELIVERY);
        when(admins.get(7L)).thenReturn(new AdminUserView(7L, "owner@gmail.com", "Store Owner", AdminRole.OWNER, true, null));
        when(mapper.toAdminViews(any())).thenReturn(Arrays.asList((AdminOrderView) null));
    }

    private List<OrderEvent> events(int count) {
        ArgumentCaptor<OrderEvent> captor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(events, times(count)).save(captor.capture());
        return captor.getAllValues();
    }

    private Payment savedPayment() {
        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(payments).save(captor.capture());
        return captor.getValue();
    }

    private void sendOutWithRavi() {
        order.sendOutForDelivery(1L, Instant.parse("2026-11-12T03:07:00Z"));
        ravi.setStatus(PartnerStatus.ON_DELIVERY);
    }

    @Test
    void dispatchSendsTheOrderOutAndMarksThePartnerBusy() {
        service.dispatch("SG1001", 1L, 7L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.OUT_FOR_DELIVERY);
        assertThat(order.getDeliveryPartnerId()).isEqualTo(1L);
        assertThat(ravi.getStatus()).isEqualTo(PartnerStatus.ON_DELIVERY);
        List<OrderEvent> saved = events(2);
        assertThat(saved.get(0).getMessage()).isEqualTo("Assigned to Ravi Kumar (Bike, 9848012345).");
        assertThat(saved.get(1).getMessage()).isEqualTo("Out for delivery with Ravi Kumar.");
    }

    @Test
    void busyPartnerCantTakeAnotherOrder() {
        assertThatThrownBy(() -> service.dispatch("SG1001", 2L, 7L))
                .isInstanceOf(ApiException.class)
                .hasMessage("Salma Begum is on another delivery. Choose another partner.")
                .extracting("code").isEqualTo("PARTNER_NOT_AVAILABLE");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.ACCEPTED);
    }

    @Test
    void orderMustBeAcceptedBeforeDispatch() {
        CustomerOrder fresh = placedOrder(line(1, "Toned Milk", "28.00", 2));
        when(orders.findByOrderNoForUpdate("SG1001")).thenReturn(Optional.of(fresh));
        assertThatThrownBy(() -> service.dispatch("SG1001", 1L, 7L))
                .isInstanceOf(ApiException.class).hasMessage("Accept the order first.");
    }

    @Test
    void deliverWithUpiRecordsTheFullPaymentTrail() {
        sendOutWithRavi();

        service.deliver("SG1001", new DeliverRequest(PaymentMode.UPI, " Priya Sharma ", 1L, " 4821 9930 7712 "), 7L);

        Payment p = savedPayment();
        assertThat(p.getMethod()).isEqualTo(PaymentMode.UPI);
        assertThat(p.getAmount()).isEqualByComparingTo("276.00");   // always the order total
        assertThat(p.getPaidByName()).isEqualTo("Priya Sharma");
        assertThat(p.getUpiRef()).isEqualTo("4821 9930 7712");
        assertThat(p.getCollectedByPartnerId()).isEqualTo(1L);
        assertThat(p.getConfirmedByAdminId()).isEqualTo(7L);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(ravi.getStatus()).isEqualTo(PartnerStatus.AVAILABLE);
        verify(customers).recordDelivery(9L);
        assertThat(events(2).get(0).getMessage())
                .isEqualTo("Paid ₹276 by UPI (ref 4821 9930 7712) by Priya Sharma to Ravi Kumar. Confirmed by Store Owner.");
    }

    @Test
    void cashPaymentNeverKeepsAUpiReference() {
        sendOutWithRavi();
        service.deliver("SG1001", new DeliverRequest(PaymentMode.CASH, "Priya Sharma", 1L, "typed by mistake"), 7L);
        assertThat(savedPayment().getUpiRef()).isNull();
        assertThat(events(2).get(0).getMessage())
                .isEqualTo("Paid ₹276 in cash by Priya Sharma to Ravi Kumar. Confirmed by Store Owner.");
    }

    @Test
    void anotherPartnerCanCollectAndTheAssignedOneIsFreed() {
        sendOutWithRavi();
        service.deliver("SG1001", new DeliverRequest(PaymentMode.CASH, "Priya Sharma", 2L, null), 7L);
        assertThat(savedPayment().getCollectedByPartnerId()).isEqualTo(2L);
        assertThat(ravi.getStatus()).isEqualTo(PartnerStatus.AVAILABLE);
    }

    @Test
    void cantDeliverBeforeItIsSentOut() {
        assertThatThrownBy(() -> service.deliver("SG1001",
                new DeliverRequest(PaymentMode.CASH, "Priya Sharma", 1L, null), 7L))
                .isInstanceOf(ApiException.class)
                .hasMessage("Send the order out for delivery first.");
        verify(payments, never()).save(any());
    }

    @Test
    void cantDeliverTwice() {
        sendOutWithRavi();
        order.markDelivered(Instant.now());
        assertThatThrownBy(() -> service.deliver("SG1001",
                new DeliverRequest(PaymentMode.CASH, "Priya Sharma", 1L, null), 7L))
                .isInstanceOf(ApiException.class).hasMessage("This order was already delivered.");
    }
}
