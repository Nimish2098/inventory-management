package com.miyuki.Inventory.Management.logistics.fleet;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public final class FleetDtos {
    private FleetDtos() {}

    public record VehicleRequest(
            @NotBlank @Size(max = 20) String plate,
            @NotNull @Positive BigDecimal capacityKg,
            @NotNull @Positive BigDecimal capacityM3,
            VehicleStatus status,
            Long driverId) {}

    public record VehicleResponse(
            Long id,
            String plate,
            BigDecimal capacityKg,
            BigDecimal capacityM3,
            VehicleStatus status,
            Long driverId) {
        public static VehicleResponse from(Vehicle v) {
            return new VehicleResponse(
                    v.getId(),
                    v.getPlate(),
                    v.getCapacityKg(),
                    v.getCapacityM3(),
                    v.getStatus(),
                    v.getDriver() == null ? null : v.getDriver().getId());
        }
    }

    public record DriverRequest(
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "must be 7-15 digits, optional leading +")
                    String phone,
            @NotBlank @Size(max = 40) String licenseNumber,
            Boolean active) {}

    public record DriverResponse(Long id, String fullName, String phone, String licenseNumber, boolean active) {
        public static DriverResponse from(Driver d) {
            return new DriverResponse(d.getId(), d.getFullName(), d.getPhone(), d.getLicenseNumber(), d.isActive());
        }
    }
}
