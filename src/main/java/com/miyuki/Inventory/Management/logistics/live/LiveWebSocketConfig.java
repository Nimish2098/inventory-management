package com.miyuki.Inventory.Management.logistics.live;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

/** STOMP over plain WebSocket at /ws, in-memory broker for /topic. Server-to-client only. */
@Configuration
@EnableWebSocketMessageBroker
public class LiveWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    public static final String ENDPOINT = "/ws";

    private final StompJwtAuthInterceptor authInterceptor;
    private final LiveProperties props;

    public LiveWebSocketConfig(StompJwtAuthInterceptor authInterceptor, LiveProperties props) {
        this.authInterceptor = authInterceptor;
        this.props = props;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(ENDPOINT).setAllowedOriginPatterns(props.getAllowedOrigins().toArray(new String[0]));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Private scheduler (deliberately not a bean, so Boot's @Scheduled thread pool is left alone).
        ThreadPoolTaskScheduler heartbeat = new ThreadPoolTaskScheduler();
        heartbeat.setPoolSize(1);
        heartbeat.setThreadNamePrefix("ws-heartbeat-");
        heartbeat.setDaemon(true);
        heartbeat.initialize();

        registry.enableSimpleBroker("/topic")
                .setHeartbeatValue(new long[] {10_000, 10_000})
                .setTaskScheduler(heartbeat);
        registry.setApplicationDestinationPrefixes("/app"); // unused: clients may not SEND (see interceptor)
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration
                .setSendTimeLimit(props.getSendTimeLimitMs())
                .setSendBufferSizeLimit(props.getSendBufferLimitBytes())
                .setTimeToFirstMessage(props.getTimeToFirstMessageMs());
    }
}
