package com.miyuki.Inventory.Management.logistics.live;

import static org.assertj.core.api.Assertions.assertThat;

import com.miyuki.Inventory.Management.logistics.tracking.TrackingDtos.LatestPosition;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class LiveBufferTest {

    static final Instant T0 = Instant.parse("2026-11-22T10:00:00Z");

    private static LatestPosition pos(long vehicleId, double lat, long secondsAfterT0) {
        return new LatestPosition(vehicleId, lat, 77.5, 30.0, 90.0, T0.plusSeconds(secondsAfterT0));
    }

    @Test
    void keepsOnlyTheNewestPositionPerVehicle() {
        LiveBuffer buffer = new LiveBuffer();
        buffer.offer(pos(1, 12.1, 0));
        buffer.offer(pos(1, 12.3, 20));
        buffer.offer(pos(1, 12.2, 10));

        List<LatestPosition> out = buffer.drain();

        assertThat(out).hasSize(1);
        assertThat(out.get(0).lat()).isEqualTo(12.3);
    }

    @Test
    void equalTimestampKeepsTheFirstOne() {
        LiveBuffer buffer = new LiveBuffer();
        buffer.offer(pos(1, 12.1, 5));
        buffer.offer(pos(1, 99.0, 5));

        assertThat(buffer.drain().get(0).lat()).isEqualTo(12.1);
    }

    @Test
    void drainReturnsEveryVehicleOrderedByIdAndEmptiesTheBuffer() {
        LiveBuffer buffer = new LiveBuffer();
        buffer.offer(pos(3, 12.3, 0));
        buffer.offer(pos(1, 12.1, 0));
        buffer.offer(pos(2, 12.2, 0));

        assertThat(buffer.size()).isEqualTo(3);
        assertThat(buffer.drain()).extracting(LatestPosition::vehicleId).containsExactly(1L, 2L, 3L);
        assertThat(buffer.size()).isZero();
        assertThat(buffer.drain()).isEmpty();
    }

    @Test
    void positionsOfDifferentVehiclesDoNotInterfere() {
        LiveBuffer buffer = new LiveBuffer();
        buffer.offer(pos(1, 12.1, 100));
        buffer.offer(pos(2, 12.2, 0)); // older timestamp but another vehicle

        assertThat(buffer.drain()).hasSize(2);
    }

    @Test
    void anOlderPingAfterADrainStartsANewEntry() {
        LiveBuffer buffer = new LiveBuffer();
        buffer.offer(pos(1, 12.5, 50));
        buffer.drain();

        buffer.offer(pos(1, 12.4, 40)); // the client merges by timestamp, the buffer only coalesces between flushes

        assertThat(buffer.drain()).hasSize(1);
    }

    @Test
    void concurrentOffersKeepTheNewestPerVehicle() throws Exception {
        LiveBuffer buffer = new LiveBuffer();
        int threads = 8;
        int perThread = 1_000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch done = new CountDownLatch(threads);
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                for (int i = 0; i < perThread; i++) {
                    for (long vehicle = 1; vehicle <= 5; vehicle++) {
                        buffer.offer(pos(vehicle, 12.0, i));
                    }
                }
                done.countDown();
            });
        }
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        List<LatestPosition> out = buffer.drain();

        assertThat(out).hasSize(5);
        assertThat(out).allMatch(p -> p.recordedAt().equals(T0.plusSeconds(perThread - 1)));
    }
}
