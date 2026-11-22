package com.miyuki.Inventory.Management.logistics.tracking;

import static org.assertj.core.api.Assertions.assertThat;

import com.miyuki.Inventory.Management.logistics.live.LiveBuffer;
import com.miyuki.Inventory.Management.logistics.live.LiveMessageListener;
import com.miyuki.Inventory.Management.logistics.tracking.TrackingDtos.LatestPosition;
import java.time.Instant;
import java.util.List;
import java.util.function.IntFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Needs Docker. Real Redis, real Lua script, real RedisMessageListenerContainer: proves that a position becoming the
 * latest one reaches subscribers (every app instance) and that a late, older ping does not.
 */
class PositionCachePublishTest {

    static final Instant T0 = Instant.parse("2026-11-22T10:00:00Z");

    StringRedisTemplate redis;
    TrackingProperties props;
    PositionCache cache;
    LiveBuffer buffer;
    RedisMessageListenerContainer container;

    @BeforeEach
    void setUp() {
        redis = RedisTestSupport.template();
        RedisTestSupport.flush(redis);
        props = new TrackingProperties();
        cache = new PositionCache(redis, props);
        buffer = new LiveBuffer();

        container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redis.getConnectionFactory());
        container.addMessageListener(new LiveMessageListener(buffer), new ChannelTopic(props.getLiveChannel()));
        container.afterPropertiesSet();
        container.start();
    }

    @AfterEach
    void tearDown() throws Exception{
        container.stop();
        container.destroy();
    }

    private static PositionRow row(long vehicleId, double lat, Instant at) {
        return new PositionRow(vehicleId, lat, 77.59, 30.5, 180.0, at, T0);
    }

    /** The subscription is established asynchronously, so keep publishing newer pings until one arrives. */
    private List<LatestPosition> publishUntilReceived(IntFunction<PositionRow> rowForAttempt) throws InterruptedException {
        for (int attempt = 0; attempt < 50; attempt++) {
            cache.update(rowForAttempt.apply(attempt));
            Thread.sleep(100);
            List<LatestPosition> received = buffer.drain();
            if (!received.isEmpty()) {
                return received;
            }
        }
        return List.of();
    }

    @Test
    void aNewLatestPositionIsAnnouncedToSubscribers() throws Exception {
        List<LatestPosition> received = publishUntilReceived(i -> row(1, 12.90 + i / 1000.0, T0.plusSeconds(i)));

        assertThat(received).isNotEmpty();
        assertThat(received.get(0).vehicleId()).isEqualTo(1L);
        assertThat(received.get(0).lng()).isEqualTo(77.59);
        assertThat(received.get(0).speedKmh()).isEqualTo(30.5);
    }

    @Test
    void anOlderPingIsStoredNowhereAndNeverBroadcast() throws Exception {
        publishUntilReceived(i -> row(1, 12.90, T0.plusSeconds(i)));
        buffer.drain(); // start from a clean slate

        boolean becameLatest = cache.update(row(1, 12.00, T0.minusSeconds(3600)));
        Thread.sleep(500);

        assertThat(becameLatest).isFalse();
        assertThat(buffer.drain()).isEmpty();
    }

    @Test
    void missingSpeedAndHeadingSurviveTheTripThroughRedis() throws Exception {
        List<LatestPosition> received =
                publishUntilReceived(i -> new PositionRow(2, 12.9, 77.5, null, null, T0.plusSeconds(i), T0));

        assertThat(received.get(0).vehicleId()).isEqualTo(2L);
        assertThat(received.get(0).speedKmh()).isNull();
        assertThat(received.get(0).heading()).isNull();
    }

    @Test
    void updatingWithoutAnySubscriberIsHarmless() {
        container.stop();

        assertThat(cache.update(row(3, 12.9, T0))).isTrue();
        assertThat(cache.latest(3L)).isPresent();
    }
}
