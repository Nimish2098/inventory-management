package com.miyuki.Inventory.Management.logistics.fleet;

import com.miyuki.Inventory.Management.logistics.common.ConflictException;import com.miyuki.Inventory.Management.logistics.common.NotFoundException;
import com.miyuki.Inventory.Management.logistics.fleet.FleetDtos.VehicleRequest;
import com.miyuki.Inventory.Management.logistics.fleet.FleetDtos.VehicleResponse;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class VehicleService {

    private final VehicleRepository vehicles;
    private final DriverRepository drivers;

    public VehicleResponse create(VehicleRequest req) {
        String plate = normalize(req.plate());
        if (vehicles.existsByPlate(plate)) {
            throw new ConflictException("Plate already registered: " + plate);
        }
        Vehicle v = new Vehicle();
        apply(v, req, plate, null);
        return VehicleResponse.from(vehicles.save(v));
    }

    @Transactional(readOnly = true)
    public VehicleResponse get(Long id) {
        return VehicleResponse.from(find(id));
    }

    @Transactional(readOnly = true)
    public Page<VehicleResponse> list(VehicleStatus status, Pageable pageable) {
        Page<Vehicle> page = status == null ? vehicles.findAll(pageable) : vehicles.findByStatus(status, pageable);
        return page.map(VehicleResponse::from);
    }

    public VehicleResponse update(Long id, VehicleRequest req) {
        Vehicle v = find(id);
        String plate = normalize(req.plate());
        if (vehicles.existsByPlateAndIdNot(plate, id)) {
            throw new ConflictException("Plate already registered: " + plate);
        }
        apply(v, req, plate, id);
        return VehicleResponse.from(v);
    }

    public void delete(Long id) {
        vehicles.delete(find(id)); // FK violation (routes/positions exist) surfaces as 409 via ApiExceptionHandler
    }

    private Vehicle find(Long id) {
        return vehicles.findById(id).orElseThrow(() -> new NotFoundException("Vehicle", id));
    }

    private void apply(Vehicle v, VehicleRequest req, String plate, Long selfId) {
        v.setPlate(plate);
        v.setCapacityKg(req.capacityKg());
        v.setCapacityM3(req.capacityM3());
        if (req.status() != null) {
            v.setStatus(req.status());
        }
        if (req.driverId() == null) {
            v.setDriver(null);
            return;
        }
        Driver driver = drivers.findById(req.driverId()).orElseThrow(() -> new NotFoundException("Driver", req.driverId()));
        if (!driver.isActive()) {
            throw new ConflictException("Driver " + driver.getId() + " is inactive");
        }
        boolean taken = selfId == null
                ? vehicles.existsByDriverId(driver.getId())
                : vehicles.existsByDriverIdAndIdNot(driver.getId(), selfId);
        if (taken) {
            throw new ConflictException("Driver " + driver.getId() + " is already assigned to another vehicle");
        }
        v.setDriver(driver);
    }

    static String normalize(String plate) {
        return plate.trim().replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }
}
