package com.miyuki.Inventory.Management.logistics.tracking;

import com.miyuki.Inventory.Management.logistics.tracking.TrackingDtos.LatestPosition;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * Latest position per vehicle in Redis, so dashboards never touch Postgres.
 * The update runs as one Lua script, which makes "only keep the newest ping" atomic even with several writers.
 * Phase 4: the same script PUBLISHes the new position on the live channel, so a ping is announced if and only if it
 * became the latest one (a late, older ping is never broadcast).
 */
@Component
public class PositionCache {

    static final String LATEST_PREFIX = "tracking:latest:";
    static final String GEO_KEY = "tracking:geo";
    static final String VEHICLES_KEY = "tracking:vehicles";

    private static final String LUA =
            """
            local cur = redis.call('HGET', KEYS[1], 'recordedAt')
            if cur and tonumber(cur) >= tonumber(ARGV[1]) then return 0 end
            redis.call('HSET', KEYS[1], 'recordedAt', ARGV[1], 'lat', ARGV[2], 'lng', ARGV[3], 'speed', ARGV[4], 'heading', ARGV[5])
            redis.call('GEOADD', KEYS[2], ARGV[3], ARGV[2], ARGV[6])
            redis.call('SADD', KEYS[3], ARGV[6])
            redis.call('EXPIRE', KEYS[1], tonumber(ARGV[7]))
            redis.call('PUBLISH', ARGV[8], ARGV[9])
            return 1
            """;
    private static final DefaultRedisScript<Long> UPDATE_SCRIPT = new DefaultRedisScript<>(LUA, Long.class);

    private final StringRedisTemplate redis;
    private final HashOperations<String, String, String> hash;
    private final TrackingProperties props;

    public PositionCache(StringRedisTemplate redis, TrackingProperties props) {
        this.redis = redis;
        this.hash = redis.opsForHash();
        this.props = props;
    }

    /** @return true if this ping became the vehicle's latest position (and was announced), false if a newer one exists */
    public boolean update(PositionRow r) {
        Long result = redis.execute(
                UPDATE_SCRIPT,
                List.of(LATEST_PREFIX + r.vehicleId(), GEO_KEY, VEHICLES_KEY),
                String.valueOf(r.recordedAt().toEpochMilli()),
                String.valueOf(r.lat()),
                String.valueOf(r.lng()),
                r.speedKmh() == null ? "" : String.valueOf(r.speedKmh()),
                r.heading() == null ? "" : String.valueOf(r.heading()),
                String.valueOf(r.vehicleId()),
                String.valueOf(props.getCacheTtl().toSeconds()),
                props.getLiveChannel(),
                LivePositionCodec.encode(r));
        return Long.valueOf(1L).equals(result);
    }

    public Optional<LatestPosition> latest(Long vehicleId) {
        Map<String, String> fields = hash.entries(LATEST_PREFIX + vehicleId);
        if (fields.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new LatestPosition(
                vehicleId,
                Double.parseDouble(fields.get("lat")),
                Double.parseDouble(fields.get("lng")),
                optionalDouble(fields.get("speed")),
                optionalDouble(fields.get("heading")),
                Instant.ofEpochMilli(Long.parseLong(fields.get("recordedAt")))));
    }

    /** Every vehicle that has reported recently, ordered by vehicle id. */
    public List<LatestPosition> latestAll() {
        Set<String> ids = redis.opsForSet().members(VEHICLES_KEY);
        List<LatestPosition> result = new ArrayList<>();
        if (ids == null) {
            return result;
        }
        for (String id : ids) {
            latest(Long.valueOf(id)).ifPresent(result::add); // expired entries are simply skipped
        }
        result.sort(Comparator.comparing(LatestPosition::vehicleId));
        return result;
    }

    private static Double optionalDouble(String value) {
        return value == null || value.isEmpty() ? null : Double.valueOf(value);
    }
}
