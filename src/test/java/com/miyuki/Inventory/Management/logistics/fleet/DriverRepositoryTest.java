package com.miyuki.Inventory.Management.logistics.fleet;

import static com.miyuki.Inventory.Management.logistics.TestData.driver;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.miyuki.Inventory.Management.logistics.AbstractRepositoryTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class DriverRepositoryTest extends AbstractRepositoryTest {

    @Autowired DriverRepository drivers;

    @Test
    void savesAndLoadsDriver() {
        Long id = drivers.saveAndFlush(driver("KA-LIC-100")).getId();
        Driver loaded = drivers.findById(id).orElseThrow();
        assertThat(loaded.getLicenseNumber()).isEqualTo("KA-LIC-100");
        assertThat(loaded.isActive()).isTrue();
    }

    @Test
    void duplicateLicenseIsRejected() {
        drivers.saveAndFlush(driver("KA-LIC-100"));
        assertThatThrownBy(() -> drivers.saveAndFlush(driver("KA-LIC-100")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
