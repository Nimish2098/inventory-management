package com.miyuki.Inventory.Management.logistics.tracking;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Tunables, all overridable in application.properties under the "tracking." prefix. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "tracking")
public class TrackingProperties {

    /** Pings older than this are rejected (offline queues are fine, ancient data is not). */
    private Duration maxAge = Duration.ofHours(24);

    /** Pings further in the future than this are rejected (clock skew allowance). */
    private Duration maxFutureSkew = Duration.ofMinutes(5);

    private String streamKey = "tracking:positions";
    private String deadLetterKey = "tracking:positions:dead";
    private String group = "tracking-writer";

    /** Must be stable across restarts of the same instance so unacknowledged entries are recovered. */
    private String consumerName = System.getenv().getOrDefault("HOSTNAME", "worker-1");

    /** Stream entries read per poll (each entry is one HTTP batch of up to 500 pings). */
    private int batchSize = 100;

    /** Memory guard: the stream is trimmed (approximately) to this many entries. */
    private long streamMaxLength = 100_000L;

    private long deadLetterMaxLength = 10_000L;

    /** How long a vehicle's latest position stays in Redis without new pings. */
    private Duration cacheTtl = Duration.ofHours(24);
}
