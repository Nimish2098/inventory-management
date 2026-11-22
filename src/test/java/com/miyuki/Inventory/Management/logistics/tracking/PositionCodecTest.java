package com.miyuki.Inventory.Management.logistics.tracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.miyuki.Inventory.Management.logistics.tracking.TrackingDtos.PositionDto;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PositionCodecTest {

    private static final Instant RECEIVED = Instant.parse("2026-11-21T10:00:00Z");

    private static PositionDto ping(String lat, String lng, String speed, String heading, String at) {
        return new PositionDto(
                new BigDecimal(lat),
                new BigDecimal(lng),
                speed == null ? null : new BigDecimal(speed),
                heading == null ? null : new BigDecimal(heading),
                Instant.parse(at));
    }

    private static Map<String, String> entry(String data) {
        return Map.of("vehicleId", "7", "receivedAt", "1000", "data", data);
    }

    @Test
    void roundTripPreservesEveryField() {
        List<PositionDto> in = List.of(
                ping("12.971600", "77.594600", "32.5", "90", "2026-11-21T09:59:50Z"),
                ping("12.972000", "77.595000", "0", "359.9", "2026-11-21T09:59:55.123Z"));

        List<PositionRow> out = PositionCodec.decode(PositionCodec.encode(7L, in, RECEIVED));

        assertThat(out).hasSize(2);
        assertThat(out.get(0).vehicleId()).isEqualTo(7L);
        assertThat(out.get(0).lat()).isEqualTo(12.9716);
        assertThat(out.get(0).lng()).isEqualTo(77.5946);
        assertThat(out.get(0).speedKmh()).isEqualTo(32.5);
        assertThat(out.get(0).heading()).isEqualTo(90.0);
        assertThat(out.get(0).recordedAt()).isEqualTo(Instant.parse("2026-11-21T09:59:50Z"));
        assertThat(out.get(0).receivedAt()).isEqualTo(RECEIVED);
        assertThat(out.get(1).recordedAt()).isEqualTo(Instant.parse("2026-11-21T09:59:55.123Z"));
        assertThat(out.get(1).heading()).isEqualTo(359.9);
    }

    @Test
    void missingSpeedAndHeadingStayNull() {
        List<PositionRow> out = PositionCodec.decode(
                PositionCodec.encode(7L, List.of(ping("12.9", "77.5", null, null, "2026-11-21T09:59:50Z")), RECEIVED));

        assertThat(out.get(0).speedKmh()).isNull();
        assertThat(out.get(0).heading()).isNull();
    }

    @Test
    void orderOfPingsIsPreserved() {
        List<PositionDto> in = List.of(
                ping("12.1", "77.1", null, null, "2026-11-21T09:00:03Z"),
                ping("12.2", "77.2", null, null, "2026-11-21T09:00:01Z"),
                ping("12.3", "77.3", null, null, "2026-11-21T09:00:02Z"));

        List<PositionRow> out = PositionCodec.decode(PositionCodec.encode(7L, in, RECEIVED));

        assertThat(out).extracting(PositionRow::lat).containsExactly(12.1, 12.2, 12.3);
    }

    @Test
    void rejectsMissingFields() {
        assertThatThrownBy(() -> PositionCodec.decode(Map.of("vehicleId", "7", "receivedAt", "1")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PositionCodec.decode(Map.of("data", "1,1,1,,", "receivedAt", "1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonNumericVehicleId() {
        assertThatThrownBy(() -> PositionCodec.decode(Map.of("vehicleId", "abc", "receivedAt", "1", "data", "1,1,1,,")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vehicleId");
    }

    @Test
    void rejectsNonPositiveVehicleId() {
        assertThatThrownBy(() -> PositionCodec.decode(Map.of("vehicleId", "0", "receivedAt", "1", "data", "1,1,1,,")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsGarbageData() {
        assertThatThrownBy(() -> PositionCodec.decode(entry("garbage")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5 fields");
    }

    @Test
    void rejectsEmptyData() {
        assertThatThrownBy(() -> PositionCodec.decode(entry("")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no positions");
        assertThatThrownBy(() -> PositionCodec.decode(entry("\n\n")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonNumericCoordinates() {
        assertThatThrownBy(() -> PositionCodec.decode(entry("1000,north,77.5,,")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lat");
    }

    @Test
    void rejectsOutOfRangeValues() {
        assertThatThrownBy(() -> PositionCodec.decode(entry("1000,91,77.5,,")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lat");
        assertThatThrownBy(() -> PositionCodec.decode(entry("1000,12,181,,")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lng");
        assertThatThrownBy(() -> PositionCodec.decode(entry("1000,12,77,-1,")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("speed");
        assertThatThrownBy(() -> PositionCodec.decode(entry("1000,12,77,,361")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("heading");
    }

    @Test
    void oneBadLineRejectsTheWholeEntry() {
        assertThatThrownBy(() -> PositionCodec.decode(entry("1000,12,77,,\n2000,999,77,,\n")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
