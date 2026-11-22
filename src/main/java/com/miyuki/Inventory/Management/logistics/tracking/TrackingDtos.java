package com.miyuki.Inventory.Management.logistics.tracking;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class TrackingDtos {
    private TrackingDtos() {}

    public static final int MAX_BATCH = 500;

    public record PositionDto(
            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal lat,
            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal lng,
            @DecimalMin("0.0") @DecimalMax("300.0") BigDecimal speedKmh,
            @DecimalMin("0.0") @DecimalMax("360.0") BigDecimal heading,
            @NotNull Instant recordedAt) {}

    /** One device sends the buffered pings of ONE vehicle. */
    public record PositionBatchRequest(
            @NotNull Long vehicleId,
            @NotEmpty @Size(max = MAX_BATCH) List<@NotNull @Valid PositionDto> positions) {}

    /** rejected = pings dropped because their timestamp was too old or too far in the future. */
    public record IngestResult(int accepted, int rejected) {}

    public record LatestPosition(
            Long vehicleId, double lat, double lng, Double speedKmh, Double heading, Instant recordedAt) {
        public static LatestPosition from(VehiclePosition p) {
            return new LatestPosition(
                    p.getVehicleId(), p.getLat(), p.getLng(), p.getSpeedKmh(), p.getHeading(), p.getRecordedAt());
        }
    }
}
