package com.miyuki.Inventory.Management.logistics.fleet;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {
    boolean existsByPlate(String plate);

    boolean existsByPlateAndIdNot(String plate, Long id);

    boolean existsByDriverIdAndIdNot(Long driverId, Long id);

    boolean existsByDriverId(Long driverId);

    Page<Vehicle> findByStatus(VehicleStatus status, Pageable pageable);
}
