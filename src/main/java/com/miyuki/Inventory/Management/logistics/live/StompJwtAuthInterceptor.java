package com.miyuki.Inventory.Management.logistics.live;

import java.util.Set;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.stereotype.Component;

/**
 * Browsers cannot set an Authorization header on a WebSocket handshake, so authentication happens on the STOMP
 * CONNECT frame instead: the client sends "Authorization: Bearer <jwt>" as a STOMP header and it is verified with the
 * same JwtDecoder your REST API uses.
 * Authorization is deliberately tiny: any authenticated user may SUBSCRIBE to the live vehicle topic, nobody may
 * SEND anything to the server over this socket. (Phase 8 can add role checks here.)
 */
@Component
public class StompJwtAuthInterceptor implements ChannelInterceptor {

    static final Set<String> ALLOWED_DESTINATIONS = Set.of(LiveBroadcaster.TOPIC);
    private static final String BEARER = "Bearer ";

    private final JwtDecoder jwtDecoder;
    private final JwtAuthenticationConverter converter = new JwtAuthenticationConverter();

    public StompJwtAuthInterceptor(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message; // heartbeats and internal messages
        }
        switch (accessor.getCommand()) {
            case CONNECT, STOMP -> authenticate(accessor);
            case SUBSCRIBE -> authorizeSubscription(accessor);
            case SEND -> throw new MessagingException("Clients are not allowed to send messages");
            default -> {
                // DISCONNECT, UNSUBSCRIBE, ACK ... need no checks
            }
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            throw new MessagingException("Missing bearer token");
        }
        try {
            Jwt jwt = jwtDecoder.decode(header.substring(BEARER.length()).trim());
            accessor.setUser(converter.convert(jwt));
        } catch (JwtException e) {
            throw new MessagingException("Invalid or expired token");
        }
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        if (accessor.getUser() == null) {
            throw new MessagingException("Not authenticated");
        }
        if (!ALLOWED_DESTINATIONS.contains(accessor.getDestination())) {
            throw new MessagingException("Subscription not allowed: " + accessor.getDestination());
        }
    }
}
