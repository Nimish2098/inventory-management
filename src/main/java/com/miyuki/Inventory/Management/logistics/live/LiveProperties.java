package com.miyuki.Inventory.Management.logistics.live;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** application.properties prefix: tracking.live.* */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "tracking.live")
public class LiveProperties {

    /** Browser origins allowed to open the WebSocket. Set your real frontend origin(s) in production. */
    private List<String> allowedOrigins = List.of("http://localhost:*", "https://localhost:*");

    /** A client that cannot keep up for this long is disconnected instead of slowing everyone down. */
    private int sendTimeLimitMs = 10_000;

    /** Max bytes queued for one slow client before it is disconnected. */
    private int sendBufferLimitBytes = 2 * 1024 * 1024;

    /** A socket that does not send CONNECT within this time is closed. */
    private int timeToFirstMessageMs = 15_000;
}
