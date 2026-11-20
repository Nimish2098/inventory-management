package com.miyuki.Inventory.Management.logistics.route;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

public final class RouteDtos {
    private RouteDtos() {}

    /** deliveryIds are visited in the order given; the optimizer (Phase 5) will reorder them later. */
    public record RouteRequest(@NotNull Long vehicleId, Instant plannedStart, List<@NotNull Long> deliveryIds) {}

    public record StopResponse(
            Long id, Long deliveryId, int sequence, StopStatus status, Instant plannedArrival) {
        public static StopResponse from(RouteStop s) {
            return new StopResponse(
                    s.getId(), s.getDelivery().getId(), s.getStopSequence(), s.getStatus(), s.getPlannedArrival());
        }
    }

    public record RouteResponse(
            Long id,
            Long vehicleId,
            RouteStatus status,
            Instant plannedStart,
            Long totalDistanceM,
            Long totalDurationS,
            List<StopResponse> stops) {
        public static RouteResponse from(Route r) {
            return new RouteResponse(
                    r.getId(),
                    r.getVehicle().getId(),
                    r.getStatus(),
                    r.getPlannedStart(),
                    r.getTotalDistanceM(),
                    r.getTotalDurationS(),
                    r.getStops().stream().map(StopResponse::from).toList());
        }
    }
}
