package com.miyuki.Inventory.Management.logistics.fleet;

import static com.miyuki.Inventory.Management.logistics.TestData.driver;
import static com.miyuki.Inventory.Management.logistics.TestData.vehicle;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.miyuki.Inventory.Management.logistics.AbstractRepositoryTest;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

class VehicleRepositoryTest extends AbstractRepositoryTest {

    @Autowired VehicleRepository vehicles;
    @Autowired DriverRepository drivers;
    @Autowired TestEntityManager em;

    @Test
    void savesAndLoadsVehicleWithDriver() {
        Driver d = drivers.saveAndFlush(driver("KA-LIC-001"));
        Vehicle v = vehicle("KA01AB1234");
        v.setDriver(d);
        Long id = vehicles.saveAndFlush(v).getId();
        em.clear();

        Vehicle loaded = vehicles.findById(id).orElseThrow();
        assertThat(loaded.getPlate()).isEqualTo("KA01AB1234");
        assertThat(loaded.getStatus()).isEqualTo(VehicleStatus.AVAILABLE);
        assertThat(loaded.getCapacityKg()).isEqualByComparingTo("1500.00");
        assertThat(loaded.getDriver().getId()).isEqualTo(d.getId());
        assertThat(loaded.getVersion()).isNotNull();
    }

    @Test
    void duplicatePlateIsRejected() {
        vehicles.saveAndFlush(vehicle("KA01AB1234"));
        assertThatThrownBy(() -> vehicles.saveAndFlush(vehicle("KA01AB1234")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void nonPositiveCapacityIsRejectedByDatabase() {
        Vehicle v = vehicle("KA01ZZ0001");
        v.setCapacityKg(BigDecimal.ZERO);
        assertThatThrownBy(() -> vehicles.saveAndFlush(v)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameDriverCannotBeAssignedToTwoVehicles() {
        Driver d = drivers.saveAndFlush(driver("KA-LIC-002"));
        Vehicle first = vehicle("KA01AA0001");
        first.setDriver(d);
        vehicles.saveAndFlush(first);

        Vehicle second = vehicle("KA01AA0002");
        second.setDriver(d);
        assertThatThrownBy(() -> vehicles.saveAndFlush(second)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void existsByPlateAndIdNotIgnoresSelf() {
        Vehicle v = vehicles.saveAndFlush(vehicle("KA01AB1234"));
        assertThat(vehicles.existsByPlate("KA01AB1234")).isTrue();
        assertThat(vehicles.existsByPlateAndIdNot("KA01AB1234", v.getId())).isFalse();
        assertThat(vehicles.existsByPlateAndIdNot("KA01AB1234", v.getId() + 1)).isTrue();
    }
}
