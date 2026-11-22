package com.sgmart.live;

import com.sgmart.auth.AdminRole;
import com.sgmart.auth.SgMartPrincipal;
import com.sgmart.order.CustomerOrder;
import com.sgmart.order.CustomerOrderRepository;
import com.sgmart.order.PaymentMethod;
import com.sgmart.order.TrackingTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StompAuthInterceptorTest {

    private final CustomerOrderRepository orders = mock(CustomerOrderRepository.class);
    private final StompAuthInterceptor interceptor = new StompAuthInterceptor(orders);
    private final String token = TrackingTokens.newToken();

    private static SgMartPrincipal principal(SgMartPrincipal.Kind kind, long id) {
        OidcIdToken idToken = new OidcIdToken("id-token", Instant.now(), Instant.now().plusSeconds(600), Map.of("sub", "g-" + id));
        String role = kind == SgMartPrincipal.Kind.ADMIN ? "ROLE_ADMIN" : "ROLE_CUSTOMER";
        return new SgMartPrincipal(kind, id, "Name", kind == SgMartPrincipal.Kind.ADMIN ? AdminRole.OWNER : null,
                List.of(new SimpleGrantedAuthority(role)), idToken, null);
    }

    @BeforeEach
    void setUp() {
        CustomerOrder o = CustomerOrder.place(TrackingTokens.hash(token), 9L, "Priya", "9876543210",
                "Flat 302, Road 12", null, null, null, PaymentMethod.CASH_ON_DELIVERY, Instant.now());
        when(orders.findByOrderNo("SG1001")).thenReturn(Optional.of(o));
        when(orders.findByOrderNo("SG9999")).thenReturn(Optional.empty());
    }

    @Test
    void adminTopicsAreForAdminsOnly() {
        assertThat(interceptor.mayListen("/topic/admin/orders", principal(SgMartPrincipal.Kind.ADMIN, 1), null)).isTrue();
        assertThat(interceptor.mayListen("/topic/admin/orders", principal(SgMartPrincipal.Kind.CUSTOMER, 9), null)).isFalse();
        assertThat(interceptor.mayListen("/topic/admin/orders", null, null)).isFalse();
    }

    @Test
    void anOrderTopicNeedsItsTokenOrItsOwner() {
        assertThat(interceptor.mayListen("/topic/orders/SG1001", null, token)).isTrue();
        assertThat(interceptor.mayListen("/topic/orders/SG1001", principal(SgMartPrincipal.Kind.CUSTOMER, 9), null)).isTrue();
        assertThat(interceptor.mayListen("/topic/orders/SG1001", null, TrackingTokens.newToken())).isFalse();
        assertThat(interceptor.mayListen("/topic/orders/SG1001", principal(SgMartPrincipal.Kind.CUSTOMER, 5), null)).isFalse();
        assertThat(interceptor.mayListen("/topic/orders/SG9999", null, token)).isFalse();
        assertThat(interceptor.mayListen("/topic/orders/SG1001", principal(SgMartPrincipal.Kind.ADMIN, 1), null)).isTrue();
    }

    @Test
    void wildcardsAndUnknownTopicsAreRefused() {
        assertThat(interceptor.mayListen("/topic/orders/*", principal(SgMartPrincipal.Kind.ADMIN, 1), null)).isFalse();
        assertThat(interceptor.mayListen("/topic/other", principal(SgMartPrincipal.Kind.ADMIN, 1), null)).isFalse();
        assertThat(interceptor.mayListen(null, null, null)).isFalse();
    }

    @Test
    void browsersCantSendMessages() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/topic/admin/orders");
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        assertThatThrownBy(() -> interceptor.preSend(message, mock(MessageChannel.class)))
                .isInstanceOf(MessageDeliveryException.class);
    }
}
