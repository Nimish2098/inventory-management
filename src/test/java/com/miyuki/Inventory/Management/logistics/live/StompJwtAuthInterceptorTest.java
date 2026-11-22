package com.miyuki.Inventory.Management.logistics.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@ExtendWith(MockitoExtension.class)
class StompJwtAuthInterceptorTest {

    @Mock JwtDecoder decoder;
    @Mock MessageChannel channel;
    StompJwtAuthInterceptor interceptor;

    record Frame(Message<byte[]> message, StompHeaderAccessor accessor) {}

    @BeforeEach
    void setUp() {
        interceptor = new StompJwtAuthInterceptor(decoder);
    }

    private static Frame frame(StompCommand command, String destination, String authorization, Principal user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        if (authorization != null) {
            accessor.addNativeHeader("Authorization", authorization);
        }
        if (user != null) {
            accessor.setUser(user);
        }
        accessor.setLeaveMutable(true);
        return new Frame(MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders()), accessor);
    }

    private static Jwt jwt(String subject) {
        return Jwt.withTokenValue("good-token")
                .header("alg", "none")
                .subject(subject)
                .claim("scope", "read")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
    }

    private static Principal someone() {
        return () -> "user-1";
    }

    // ---------- CONNECT ----------

    @Test
    void connectWithValidTokenSetsThePrincipal() {
        when(decoder.decode("good-token")).thenReturn(jwt("user-1"));
        Frame f = frame(StompCommand.CONNECT, null, "Bearer good-token", null);

        Message<?> result = interceptor.preSend(f.message(), channel);

        assertThat(result).isSameAs(f.message());
        assertThat(f.accessor().getUser()).isNotNull();
        assertThat(f.accessor().getUser().getName()).isEqualTo("user-1");
    }

    @Test
    void schemeNameIsCaseInsensitive() {
        when(decoder.decode("good-token")).thenReturn(jwt("user-1"));
        Frame f = frame(StompCommand.CONNECT, null, "bearer   good-token ", null);

        interceptor.preSend(f.message(), channel);

        assertThat(f.accessor().getUser()).isNotNull();
    }

    @Test
    void stompCommandIsTreatedLikeConnect() {
        when(decoder.decode("good-token")).thenReturn(jwt("user-1"));
        Frame f = frame(StompCommand.STOMP, null, "Bearer good-token", null);

        interceptor.preSend(f.message(), channel);

        assertThat(f.accessor().getUser()).isNotNull();
    }

    @Test
    void connectWithoutTokenIsRejected() {
        Frame f = frame(StompCommand.CONNECT, null, null, null);

        assertThatThrownBy(() -> interceptor.preSend(f.message(), channel))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("Missing");
        verifyNoInteractions(decoder);
    }

    @Test
    void connectWithAnotherAuthSchemeIsRejected() {
        Frame f = frame(StompCommand.CONNECT, null, "Basic dXNlcjpwYXNz", null);

        assertThatThrownBy(() -> interceptor.preSend(f.message(), channel)).isInstanceOf(MessagingException.class);
        verifyNoInteractions(decoder);
    }

    @Test
    void connectWithInvalidOrExpiredTokenIsRejected() {
        when(decoder.decode("bad")).thenThrow(new BadJwtException("expired"));
        Frame f = frame(StompCommand.CONNECT, null, "Bearer bad", null);

        assertThatThrownBy(() -> interceptor.preSend(f.message(), channel))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("Invalid");
        assertThat(f.accessor().getUser()).isNull();
    }

    // ---------- SUBSCRIBE ----------

    @Test
    void authenticatedUserMaySubscribeToTheVehicleTopic() {
        Frame f = frame(StompCommand.SUBSCRIBE, LiveBroadcaster.TOPIC, null, someone());

        assertThatCode(() -> interceptor.preSend(f.message(), channel)).doesNotThrowAnyException();
    }

    @Test
    void subscribingWithoutAuthenticationIsRejected() {
        Frame f = frame(StompCommand.SUBSCRIBE, LiveBroadcaster.TOPIC, null, null);

        assertThatThrownBy(() -> interceptor.preSend(f.message(), channel))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("Not authenticated");
    }

    @Test
    void otherDestinationsAreRejected() {
        for (String destination : new String[] {"/topic/admin", "/topic/vehicles/1", "/queue/x", "/app/anything"}) {
            Frame f = frame(StompCommand.SUBSCRIBE, destination, null, someone());
            assertThatThrownBy(() -> interceptor.preSend(f.message(), channel))
                    .as(destination)
                    .isInstanceOf(MessagingException.class);
        }
    }

    // ---------- everything else ----------

    @Test
    void clientsCannotSendMessages() {
        Frame f = frame(StompCommand.SEND, "/app/hello", null, someone());

        assertThatThrownBy(() -> interceptor.preSend(f.message(), channel)).isInstanceOf(MessagingException.class);
    }

    @Test
    void disconnectAndUnsubscribePassThrough() {
        Frame disconnect = frame(StompCommand.DISCONNECT, null, null, null);
        Frame unsubscribe = frame(StompCommand.UNSUBSCRIBE, null, null, someone());

        assertThatCode(() -> interceptor.preSend(disconnect.message(), channel)).doesNotThrowAnyException();
        assertThatCode(() -> interceptor.preSend(unsubscribe.message(), channel)).doesNotThrowAnyException();
    }

    @Test
    void heartbeatsPassThrough() {
        StompHeaderAccessor accessor = StompHeaderAccessor.createForHeartbeat();
        accessor.setLeaveMutable(true);
        Message<byte[]> heartbeat = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThat(interceptor.preSend(heartbeat, channel)).isSameAs(heartbeat);
    }
}
