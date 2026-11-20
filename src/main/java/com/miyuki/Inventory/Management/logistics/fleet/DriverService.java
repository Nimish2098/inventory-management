package com.miyuki.Inventory.Management.logistics.fleet;


import com.miyuki.Inventory.Management.logistics.common.ConflictException;
import com.miyuki.Inventory.Management.logistics.common.NotFoundException;
import com.miyuki.Inventory.Management.logistics.fleet.FleetDtos.DriverRequest;
import com.miyuki.Inventory.Management.logistics.fleet.FleetDtos.DriverResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class DriverService {

    private final DriverRepository drivers;

    public DriverResponse create(DriverRequest req) {
        String license = req.licenseNumber().trim();
        if (drivers.existsByLicenseNumber(license)) {
            throw new ConflictException("License already registered: " + license);
        }
        Driver d = new Driver();
        apply(d, req, license);
        return DriverResponse.from(drivers.save(d));
    }

    @Transactional(readOnly = true)
    public DriverResponse get(Long id) {
        return DriverResponse.from(find(id));
    }

    @Transactional(readOnly = true)
    public Page<DriverResponse> list(Pageable pageable) {
        return drivers.findAll(pageable).map(DriverResponse::from);
    }

    public DriverResponse update(Long id, DriverRequest req) {
        Driver d = find(id);
        String license = req.licenseNumber().trim();
        if (drivers.existsByLicenseNumberAndIdNot(license, id)) {
            throw new ConflictException("License already registered: " + license);
        }
        apply(d, req, license);
        return DriverResponse.from(d);
    }

    public void delete(Long id) {
        drivers.delete(find(id)); // still assigned to a vehicle -> FK violation -> 409
    }

    private Driver find(Long id) {
        return drivers.findById(id).orElseThrow(() -> new NotFoundException("Driver", id));
    }

    private void apply(Driver d, DriverRequest req, String license) {
        d.setFullName(req.fullName().trim());
        d.setPhone(req.phone());
        d.setLicenseNumber(license);
        if (req.active() != null) {
            d.setActive(req.active());
        }
    }
}
