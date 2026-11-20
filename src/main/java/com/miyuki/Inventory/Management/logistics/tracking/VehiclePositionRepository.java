package com.miyuki.Inventory.Management.logistics.tracking;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehiclePositionRepository extends JpaRepository<VehiclePosition, Long> {
    Optional<VehiclePosition> findFirstByVehicleIdOrderByRecordedAtDesc(Long vehicleId);
}
