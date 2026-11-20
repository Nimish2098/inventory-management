package com.miyuki.Inventory.Management.warehouse;

import com.miyuki.Inventory.Management.warehouses.dto.CreateWarehouseRequest;
import com.miyuki.Inventory.Management.warehouses.dto.UpdateWarehouseRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarehouseRequestValidationTest {

    @Test
    void createRequestRequiresPositiveCapacity() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();

            assertCapacityViolations(
                validator.validate(new CreateWarehouseRequest("WH-00001", "Warehouse", null)),
                Set.of("capacity")
            );
            assertCapacityViolations(
                validator.validate(new CreateWarehouseRequest("WH-00001", "Warehouse", 0)),
                Set.of("capacity")
            );
            assertTrue(
                validator.validate(new CreateWarehouseRequest("WH-00001", "Warehouse", 1))
                    .isEmpty()
            );
        }
    }

    @Test
    void updateRequestRequiresPositiveCapacity() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();

            assertCapacityViolations(
                validator.validate(new UpdateWarehouseRequest("WH-00001", "Warehouse", null, true)),
                Set.of("capacity")
            );
            assertCapacityViolations(
                validator.validate(new UpdateWarehouseRequest("WH-00001", "Warehouse", 0, true)),
                Set.of("capacity")
            );
            assertTrue(
                validator.validate(new UpdateWarehouseRequest("WH-00001", "Warehouse", 1, true))
                    .isEmpty()
            );
        }
    }

    private static void assertCapacityViolations(
        Set<? extends ConstraintViolation<?>> violations,
        Set<String> expectedProperties
    ) {
        assertEquals(
            expectedProperties,
            violations.stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet())
        );
    }
}
