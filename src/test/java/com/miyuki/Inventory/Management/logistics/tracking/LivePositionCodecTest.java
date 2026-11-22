package com.miyuki.Inventory.Management.logistics.tracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.miyuki.Inventory.Management.logistics.tracking.TrackingDtos.LatestPosition;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LivePositionCodecTest {

    static final Instant T = Instant.parse("2026-11-22T10:00:00.250Z");

    @Test
    void encodesTheDocumentedFormat() {
        PositionRow row = new PositionRow(7, 12.9716, 77.5946, 32.5, 90.0, T, T);

        assertThat(LivePositionCodec.encode(row)).isEqualTo("7," + T.toEpochMilli() + ",12.9716,77.5946,32.5,90.0");
    }

    @Test
    void roundTripKeepsEveryField() {
        PositionRow row = new PositionRow(7, 12.9716, 77.5946, 32.5, 359.9, T, T);

        LatestPosition p = LivePositionCodec.decode(LivePositionCodec.encode(row));

        assertThat(p.vehicleId()).isEqualTo(7L);
        assertThat(p.lat()).isEqualTo(12.9716);
        assertThat(p.lng()).isEqualTo(77.5946);
        assertThat(p.speedKmh()).isEqualTo(32.5);
        assertThat(p.heading()).isEqualTo(359.9);
        assertThat(p.recordedAt()).isEqualTo(T);
    }

    @Test
    void unknownSpeedAndHeadingStayNull() {
        PositionRow row = new PositionRow(7, 12.9, 77.5, null, null, T, T);

        String payload = LivePositionCodec.encode(row);
        LatestPosition p = LivePositionCodec.decode(payload);

        assertThat(payload).endsWith(",,");
        assertThat(p.speedKmh()).isNull();
        assertThat(p.heading()).isNull();
    }

    @Test
    void negativeCoordinatesSurvive() {
        PositionRow row = new PositionRow(1, -33.8688, -151.2093, 0.0, 0.0, T, T);

        LatestPosition p = LivePositionCodec.decode(LivePositionCodec.encode(row));

        assertThat(p.lat()).isEqualTo(-33.8688);
        assertThat(p.lng()).isEqualTo(-151.2093);
    }

    @Test
    void malformedPayloadsAreRejected() {
        assertThatThrownBy(() -> LivePositionCodec.decode(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LivePositionCodec.decode("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LivePositionCodec.decode("1,2,3")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LivePositionCodec.decode("x,1000,12.9,77.5,,")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LivePositionCodec.decode("1,1000,north,77.5,,")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LivePositionCodec.decode("1,1000,12.9,77.5,,,extra")).isInstanceOf(IllegalArgumentException.class);
    }
}
