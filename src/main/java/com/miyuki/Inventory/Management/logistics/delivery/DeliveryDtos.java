package com.miyuki.Inventory.Management.logistics.delivery;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public final class DeliveryDtos {
    private DeliveryDtos() {}

    public record DeliveryRequest(
            @NotNull Long orderId,
            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal destinationLat,
            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal destinationLng,
            @Size(max = 255) String destinationAddress,
            Instant windowStart,
            Instant windowEnd,
            @NotNull @PositiveOrZero BigDecimal weightKg,
            @NotNull @PositiveOrZero BigDecimal volumeM3) {

        @JsonIgnore
        @AssertTrue(message = "windowEnd must be after windowStart")
        public boolean isWindowValid() {
            return windowStart == null || windowEnd == null || windowEnd.isAfter(windowStart);
        }
    }

    public record DeliveryResponse(
            Long id,
            Long orderId,
            DeliveryStatus status,
            Double destinationLat,
            Double destinationLng,
            String destinationAddress,
            Instant windowStart,
            Instant windowEnd,
            BigDecimal weightKg,
            BigDecimal volumeM3,
            Instant estimatedArrival) {
        public static DeliveryResponse from(Delivery d) {
            return new DeliveryResponse(
                    d.getId(),
                    d.getOrderId(),
                    d.getStatus(),
                    d.getDestinationLat(),
                    d.getDestinationLng(),
                    d.getDestinationAddress(),
                    d.getWindowStart(),
                    d.getWindowEnd(),
                    d.getWeightKg(),
                    d.getVolumeM3(),
                    d.getEstimatedArrival());
        }
    }

    public record DeliveryEventResponse(
            Long id, DeliveryStatus fromStatus, DeliveryStatus toStatus, String actor, String reason, Instant at) {
        public static DeliveryEventResponse from(DeliveryEvent e) {
            return new DeliveryEventResponse(
                    e.getId(), e.getFromStatus(), e.getToStatus(), e.getActor(), e.getReason(), e.getCreatedAt());
        }
    }
}
