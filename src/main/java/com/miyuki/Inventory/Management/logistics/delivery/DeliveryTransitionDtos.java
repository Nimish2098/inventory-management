package com.miyuki.Inventory.Management.logistics.delivery;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class DeliveryTransitionDtos {
    private DeliveryTransitionDtos() {}

    /** A reason is mandatory for FAIL and CANCEL (checked in the service). */
    public record TransitionRequest(@NotNull DeliveryAction action, @Size(max = 255) String reason) {}
}
