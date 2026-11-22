package com.miyuki.Inventory.Management.logistics.tracking;

import com.miyuki.Inventory.Management.logistics.common.BadRequestException;
import com.miyuki.Inventory.Management.logistics.common.NotFoundException;
import com.miyuki.Inventory.Management.logistics.fleet.VehicleRepository;
import com.miyuki.Inventory.Management.logistics.tracking.TrackingDtos.LatestPosition;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrackingQueryService {

    static final int MAX_HISTORY = 5_000;

    private final PositionCache cache;
    private final VehiclePositionRepository positions;
    private final VehicleRepository vehicles;

    public TrackingQueryService(
            PositionCache cache, VehiclePositionRepository positions, VehicleRepository vehicles) {
        this.cache = cache;
        this.positions = positions;
        this.vehicles = vehicles;
    }

    /** Served from Redis only. Empty if nothing reported recently (or Redis was restarted). */
    public List<LatestPosition> live() {
        return cache.latestAll();
    }

    /** Redis first; falls back to the database so a Redis restart does not make vehicles disappear. */
    @Transactional(readOnly = true)
    public LatestPosition latest(Long vehicleId) {
        return cache.latest(vehicleId)
                .or(() -> positions.findFirstByVehicleIdOrderByRecordedAtDesc(vehicleId)
                        .map(LatestPosition::from))
                .orElseThrow(() -> new NotFoundException("Position of vehicle", vehicleId));
    }

    /** Oldest-first list of the newest {@code limit} pings in the window (ready to draw as a trail). */
    @Transactional(readOnly = true)
    public List<LatestPosition> history(Long vehicleId, Instant from, Instant to, int limit) {
        Instant end = to == null ? Instant.now() : to;
        Instant start = from == null ? end.minus(Duration.ofHours(1)) : from;
        if (start.isAfter(end)) {
            throw new BadRequestException("'from' must not be after 'to'");
        }
        if (limit < 1 || limit > MAX_HISTORY) {
            throw new BadRequestException("limit must be between 1 and " + MAX_HISTORY);
        }
        if (!vehicles.existsById(vehicleId)) {
            throw new NotFoundException("Vehicle", vehicleId);
        }
        List<LatestPosition> trail = new ArrayList<>(
                positions.findByVehicleIdAndRecordedAtBetweenOrderByRecordedAtDesc(
                                vehicleId, start, end, PageRequest.of(0, limit))
                        .stream()
                        .map(LatestPosition::from)
                        .toList());
        Collections.reverse(trail);
        return trail;
    }
}
