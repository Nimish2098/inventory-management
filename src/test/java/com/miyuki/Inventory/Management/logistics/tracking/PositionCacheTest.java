package com.miyuki.Inventory.Management.logistics.tracking;

import static org.assertj.core.api.Assertions.assertThat;

import com.miyuki.Inventory.Management.logistics.tracking.TrackingDtos.LatestPosition;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Needs Docker. Exercises the real Lua script against a real Redis. */
class PositionCacheTest {

    static final Instant T0 = Instant.parse("2026-11-21T10:00:00Z");

    StringRedisTemplate redis;
    TrackingProperties props;
    PositionCache cache;

    @BeforeEach
    void setUp() {
        redis = RedisTestSupport.template();
        RedisTestSupport.flush(redis);
        props = new TrackingProperties();
        cache = new PositionCache(redis, props);
    }

    private static PositionRow row(long vehicleId, double lat, Instant at) {
        return new PositionRow(vehicleId, lat, 77.59, 30.5, 180.0, at, T0);
    }

    @Test
    void firstPingBecomesTheLatestPosition() {
        assertThat(cache.update(row(1, 12.97, T0))).isTrue();

        LatestPosition latest = cache.latest(1L).orElseThrow();
        assertThat(latest.lat()).isEqualTo(12.97);
        assertThat(latest.lng()).isEqualTo(77.59);
        assertThat(latest.speedKmh()).isEqualTo(30.5);
        assertThat(latest.heading()).isEqualTo(180.0);
        assertThat(latest.recordedAt()).isEqualTo(T0);
    }

    @Test
    void newerPingReplacesOlderOne() {
        cache.update(row(1, 12.90, T0));

        assertThat(cache.update(row(1, 12.95, T0.plusSeconds(5)))).isTrue();

        assertThat(cache.latest(1L).orElseThrow().lat()).isEqualTo(12.95);
    }

    @Test
    void olderPingArrivingLateIsIgnored() {
        cache.update(row(1, 12.95, T0.plusSeconds(30)));

        assertThat(cache.update(row(1, 12.00, T0))).isFalse();

        LatestPosition latest = cache.latest(1L).orElseThrow();
        assertThat(latest.lat()).isEqualTo(12.95);
        assertThat(latest.recordedAt()).isEqualTo(T0.plusSeconds(30));
    }

    @Test
    void pingWithTheSameTimestampIsIgnored() {
        cache.update(row(1, 12.95, T0));

        assertThat(cache.update(row(1, 13.50, T0))).isFalse();

        assertThat(cache.latest(1L).orElseThrow().lat()).isEqualTo(12.95);
    }

    @Test
    void missingSpeedAndHeadingComeBackAsNull() {
        cache.update(new PositionRow(1, 12.9, 77.5, null, null, T0, T0));

        LatestPosition latest = cache.latest(1L).orElseThrow();
        assertThat(latest.speedKmh()).isNull();
        assertThat(latest.heading()).isNull();
    }

    @Test
    void unknownVehicleHasNoPosition() {
        assertThat(cache.latest(42L)).isEmpty();
        assertThat(cache.latestAll()).isEmpty();
    }

    @Test
    void latestAllReturnsEveryVehicleOrderedById() {
        cache.update(row(3, 12.3, T0));
        cache.update(row(1, 12.1, T0));
        cache.update(row(2, 12.2, T0));

        List<LatestPosition> all = cache.latestAll();

        assertThat(all).extracting(LatestPosition::vehicleId).containsExactly(1L, 2L, 3L);
        assertThat(all).extracting(LatestPosition::lat).containsExactly(12.1, 12.2, 12.3);
    }

    @Test
    void vehiclesAreIndependent() {
        cache.update(row(1, 12.1, T0.plusSeconds(60)));
        cache.update(row(2, 12.2, T0)); // older timestamp, but a different vehicle: must still be stored

        assertThat(cache.latest(2L).orElseThrow().lat()).isEqualTo(12.2);
    }

    @Test
    void entriesExpireAfterTheConfiguredTtl() {
        props.setCacheTtl(Duration.ofHours(2));
        cache.update(row(1, 12.1, T0));

        Long ttl = redis.getExpire(PositionCache.LATEST_PREFIX + 1);

        assertThat(ttl).isNotNull().isBetween(1L, 7200L);
    }

    @Test
    void positionIsAddedToTheGeoIndexForNearestVehicleQueries() {
        cache.update(row(1, 12.97, T0));

        assertThat(redis.opsForGeo().position(PositionCache.GEO_KEY, "1")).hasSize(1).doesNotContainNull();
    }
}
