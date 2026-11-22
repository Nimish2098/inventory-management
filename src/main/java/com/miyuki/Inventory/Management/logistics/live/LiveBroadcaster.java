package com.miyuki.Inventory.Management.logistics.live;

import com.miyuki.Inventory.Management.logistics.tracking.TrackingDtos.LatestPosition;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Every flush interval (default 500 ms) sends ONE message to /topic/vehicles containing the newest position of every
 * vehicle that moved since the previous flush. Clients merge the array by vehicleId.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "tracking.live.enabled", havingValue = "true", matchIfMissing = true)
public class LiveBroadcaster {

    public static final String TOPIC = "/topic/vehicles";

    private final LiveBuffer buffer;
    private final SimpMessageSendingOperations messaging;

    public LiveBroadcaster(LiveBuffer buffer, SimpMessageSendingOperations messaging) {
        this.buffer = buffer;
        this.messaging = messaging;
    }

    @Scheduled(fixedDelayString = "${tracking.live.flush-interval-ms:500}")
    public void flush() {
        List<LatestPosition> batch = buffer.drain();
        if (batch.isEmpty()) {
            return;
        }
        try {
            messaging.convertAndSend(TOPIC, batch);
        } catch (MessagingException e) {
            // Live data is disposable: the next ping of each vehicle replaces what was dropped.
            log.warn("Could not broadcast {} live positions: {}", batch.size(), e.getMessage());
        }
    }
}
