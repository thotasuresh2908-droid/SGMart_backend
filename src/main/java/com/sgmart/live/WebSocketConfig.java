package com.sgmart.live;

import com.sgmart.config.SgMartProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * STOMP over WebSocket at /ws with an in-memory broker for /topic/**.
 * Only the web app's own origins may connect, and every SUBSCRIBE is checked by StompAuthInterceptor.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final SgMartProperties props;
    private final StompAuthInterceptor authInterceptor;

    public WebSocketConfig(SgMartProperties props, StompAuthInterceptor authInterceptor) {
        this.props = props;
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Same rule as CORS: a trailing "/" is ignored, because the browser's Origin never has one.
        Set<String> origins = new LinkedHashSet<>();
        if (props.corsAllowedOrigins() != null) {
            props.corsAllowedOrigins().forEach(o -> addOrigin(origins, o));
        }
        addOrigin(origins, props.publicUrl());
        registry.addEndpoint("/ws").setAllowedOriginPatterns(origins.toArray(String[]::new));
    }

    private static void addOrigin(Set<String> target, String origin) {
        if (origin == null) {
            return;
        }
        String o = origin.trim();
        while (o.endsWith("/")) {
            o = o.substring(0, o.length() - 1);
        }
        if (!o.isEmpty()) {
            target.add(o);
        }
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }
}
