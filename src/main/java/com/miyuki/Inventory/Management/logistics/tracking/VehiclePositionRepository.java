package com.miyuki.Inventory.Management.logistics.tracking;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehiclePositionRepository extends JpaRepository<VehiclePosition, Long> {

    Optional<VehiclePosition> findFirstByVehicleIdOrderByRecordedAtDesc(Long vehicleId);

    /** Newest first; the pageable only limits the number of rows (use PageRequest.of(0, limit)). */
    List<VehiclePosition> findByVehicleIdAndRecordedAtBetweenOrderByRecordedAtDesc(
            Long vehicleId, Instant from, Instant to, Pageable pageable);
}
