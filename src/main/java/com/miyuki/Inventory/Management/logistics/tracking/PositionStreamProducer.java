package com.miyuki.Inventory.Management.logistics.tracking;

import com.miyuki.Inventory.Management.logistics.tracking.TrackingDtos.PositionDto;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Appends one stream entry per HTTP batch. This is the only thing the request thread does with Redis. */
@Component
public class PositionStreamProducer {

    private final StreamOperations<String, String, String> streams;
    private final TrackingProperties props;

    public PositionStreamProducer(StringRedisTemplate redis, TrackingProperties props) {
        this.streams = redis.opsForStream();
        this.props = props;
    }

    public void publish(Long vehicleId, List<PositionDto> positions, Instant receivedAt) {
        Map<String, String> fields = PositionCodec.encode(vehicleId, positions, receivedAt);
        streams.add(StreamRecords.newRecord().in(props.getStreamKey()).ofMap(fields));
    }
}
