package com.sgmart.live;

import com.sgmart.auth.SgMartPrincipal;
import com.sgmart.order.CustomerOrderRepository;
import com.sgmart.order.OrderController;
import com.sgmart.order.TrackingTokens;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.security.Principal;

/**
 * Decides who may listen to what.
 *  - /topic/admin/**          signed-in admins only
 *  - /topic/orders/{orderNo}  admins, the signed-in owner, or whoever sends the order's
 *                             X-Tracking-Token header on SUBSCRIBE
 * Clients may not SEND anything: updates only flow from the server.
 */
@Component
public class StompAuthInterceptor implements ChannelInterceptor {

    private final CustomerOrderRepository orders;

    public StompAuthInterceptor(CustomerOrderRepository orders) {
        this.orders = orders;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        if (accessor.getCommand() == StompCommand.SEND) {
            throw new MessageDeliveryException("Sending messages is not allowed.");
        }
        if (accessor.getCommand() == StompCommand.SUBSCRIBE
                && !mayListen(accessor.getDestination(), principalOf(accessor.getUser()),
                accessor.getFirstNativeHeader(OrderController.TOKEN_HEADER))) {
            throw new MessageDeliveryException("Not allowed to listen to " + accessor.getDestination());
        }
        return message;
    }

    boolean mayListen(String destination, SgMartPrincipal user, String trackingToken) {
        if (destination == null) {
            return false;
        }
        boolean admin = user != null && user.isAdmin();
        if (destination.startsWith(LiveTopics.ADMIN_PREFIX)) {
            return admin;
        }
        if (destination.startsWith(LiveTopics.ORDER_PREFIX)) {
            String orderNo = destination.substring(LiveTopics.ORDER_PREFIX.length());
            if (orderNo.isEmpty() || orderNo.contains("/") || orderNo.contains("*")) {
                return false;
            }
            if (admin) {
                return true;
            }
            Long customerId = user != null && user.getKind() == SgMartPrincipal.Kind.CUSTOMER ? user.getAppUserId() : null;
            return orders.findByOrderNo(orderNo)
                    .map(o -> (customerId != null && customerId.equals(o.getCustomerId()))
                            || TrackingTokens.matches(trackingToken, o.getTrackingTokenHash()))
                    .orElse(false);
        }
        return false;
    }

    static SgMartPrincipal principalOf(Principal user) {
        if (user instanceof Authentication auth && auth.getPrincipal() instanceof SgMartPrincipal p) {
            return p;
        }
        return null;
    }
}
