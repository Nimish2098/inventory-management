package com.miyuki.Inventory.Management.logistics.fleet;

import static com.miyuki.Inventory.Management.logistics.TestData.driver;
import static com.miyuki.Inventory.Management.logistics.TestData.vehicle;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miyuki.Inventory.Management.logistics.common.ConflictException;import com.miyuki.Inventory.Management.logistics.common.NotFoundException;
import com.miyuki.Inventory.Management.logistics.fleet.FleetDtos.VehicleRequest;
import com.miyuki.Inventory.Management.logistics.fleet.FleetDtos.VehicleResponse;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VehicleServiceTest {

    @Mock VehicleRepository vehicles;
    @Mock DriverRepository drivers;
    @InjectMocks VehicleService service;

    private static VehicleRequest request(String plate, Long driverId) {
        return new VehicleRequest(plate, new BigDecimal("1000"), new BigDecimal("8"), null, driverId);
    }

    @Test
    void normalizesPlateBeforeSaving() {
        when(vehicles.existsByPlate("KA01AB1234")).thenReturn(false);
        when(vehicles.save(any(Vehicle.class))).thenAnswer(inv -> inv.getArgument(0));

        VehicleResponse response = service.create(request("  ka01 ab1234 ", null));

        assertThat(response.plate()).isEqualTo("KA01AB1234");
        assertThat(response.status()).isEqualTo(VehicleStatus.AVAILABLE);
    }

    @Test
    void duplicatePlateIsConflict() {
        when(vehicles.existsByPlate("KA01AB1234")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request("ka01ab1234", null)))
                .isInstanceOf(ConflictException.class);
        verify(vehicles, never()).save(any());
    }

    @Test
    void getMissingVehicleIsNotFound() {
        when(vehicles.findById(42L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(42L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void unknownDriverIsNotFound() {
        when(vehicles.existsByPlate("KA01AB1234")).thenReturn(false);
        when(drivers.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request("KA01AB1234", 7L))).isInstanceOf(NotFoundException.class);
    }

    @Test
    void driverAlreadyOnAnotherVehicleIsConflict() {
        Driver d = driver("LIC-1");
        d.setId(7L);
        when(vehicles.existsByPlate("KA01AB1234")).thenReturn(false);
        when(drivers.findById(7L)).thenReturn(Optional.of(d));
        when(vehicles.existsByDriverId(7L)).thenReturn(true);

        assertThatThrownBy(() -> service.create(request("KA01AB1234", 7L))).isInstanceOf(ConflictException.class);
    }

    @Test
    void inactiveDriverIsConflict() {
        Driver d = driver("LIC-2");
        d.setId(8L);
        d.setActive(false);
        when(vehicles.existsByPlate("KA01AB1234")).thenReturn(false);
        when(drivers.findById(8L)).thenReturn(Optional.of(d));

        assertThatThrownBy(() -> service.create(request("KA01AB1234", 8L))).isInstanceOf(ConflictException.class);
    }

    @Test
    void updateMayKeepItsOwnPlate() {
        Vehicle existing = vehicle("KA01AB1234");
        existing.setId(3L);
        when(vehicles.findById(3L)).thenReturn(Optional.of(existing));
        when(vehicles.existsByPlateAndIdNot("KA01AB1234", 3L)).thenReturn(false);

        VehicleResponse response = service.update(3L, request("KA01AB1234", null));

        assertThat(response.id()).isEqualTo(3L);
    }
}
