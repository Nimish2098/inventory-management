package com.miyuki.Inventory.Management.logistics.tracking;

import static com.miyuki.Inventory.Management.logistics.TestData.vehicle;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.miyuki.Inventory.Management.logistics.AbstractRepositoryTest;
import com.miyuki.Inventory.Management.logistics.fleet.Vehicle;
import com.miyuki.Inventory.Management.logistics.fleet.VehicleRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class VehiclePositionRepositoryTest extends AbstractRepositoryTest {

    @Autowired VehiclePositionRepository positions;
    @Autowired VehicleRepository vehicles;

    private VehiclePosition ping(Long vehicleId, double lat, Instant at) {
        VehiclePosition p = new VehiclePosition();
        p.setVehicleId(vehicleId);
        p.setLat(lat);
        p.setLng(77.59);
        p.setSpeedKmh(32.5);
        p.setHeading(90.0);
        p.setRecordedAt(at);
        return p;
    }

    @Test
    void latestPositionIsTheMostRecentByRecordedAtNotInsertOrder() {
        Vehicle v = vehicles.saveAndFlush(vehicle("KA01GP0001"));
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        positions.saveAndFlush(ping(v.getId(), 12.97, now));
        positions.saveAndFlush(ping(v.getId(), 12.50, now.minusSeconds(60))); // arrives late, but is older

        VehiclePosition latest =
                positions.findFirstByVehicleIdOrderByRecordedAtDesc(v.getId()).orElseThrow();
        assertThat(latest.getLat()).isEqualTo(12.97);
    }

    @Test
    void invalidHeadingIsRejectedByDatabase() {
        Vehicle v = vehicles.saveAndFlush(vehicle("KA01GP0002"));
        VehiclePosition p = ping(v.getId(), 12.9, Instant.now());
        p.setHeading(400.0);
        assertThatThrownBy(() -> positions.saveAndFlush(p)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void positionForUnknownVehicleIsRejected() {
        VehiclePosition p = ping(999_999L, 12.9, Instant.now());
        assertThatThrownBy(() -> positions.saveAndFlush(p)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
