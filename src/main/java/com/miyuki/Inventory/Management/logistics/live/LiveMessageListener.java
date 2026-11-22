package com.miyuki.Inventory.Management.logistics.live;

import com.miyuki.Inventory.Management.logistics.tracking.LivePositionCodec;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/** Runs on EVERY app instance: receives announcements from Redis and buffers them for the next broadcast. */
@Slf4j
@Component
public class LiveMessageListener implements MessageListener {

    private final LiveBuffer buffer;

    public LiveMessageListener(LiveBuffer buffer) {
        this.buffer = buffer;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            buffer.offer(LivePositionCodec.decode(new String(message.getBody(), StandardCharsets.UTF_8)));
        } catch (IllegalArgumentException e) {
            log.warn("Ignoring malformed live position message: {}", e.getMessage());
        }
    }
}
