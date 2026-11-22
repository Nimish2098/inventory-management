package com.miyuki.Inventory.Management.logistics.live;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Separate filter chain that only covers /ws/**, so your existing SecurityConfig stays untouched.
 * The HTTP handshake is open because browsers cannot attach a bearer header to it; the connection is useless until
 * the STOMP CONNECT frame carries a valid JWT (see StompJwtAuthInterceptor), and sockets that never send CONNECT are
 * closed after tracking.live.time-to-first-message-ms.
 */
@Configuration
public class LiveSecurityConfig {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    SecurityFilterChain webSocketHandshakeChain(HttpSecurity http) throws Exception {
        http.securityMatcher(LiveWebSocketConfig.ENDPOINT, LiveWebSocketConfig.ENDPOINT + "/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }
}
