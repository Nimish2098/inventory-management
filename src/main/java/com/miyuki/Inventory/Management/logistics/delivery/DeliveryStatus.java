package com.miyuki.Inventory.Management.logistics.delivery;

import java.util.EnumSet;
import java.util.Set;

public enum DeliveryStatus {
    CREATED,
    ASSIGNED,
    PICKED_UP,
    IN_TRANSIT,
    ARRIVED,
    DELIVERED,
    FAILED,
    RETURNED,
    CANCELLED;

    public Set<DeliveryStatus> allowedNextStatuses() {
        return switch (this) {
            case CREATED -> EnumSet.of(ASSIGNED, CANCELLED);
            case ASSIGNED -> EnumSet.of(PICKED_UP, CANCELLED);
            case PICKED_UP -> EnumSet.of(IN_TRANSIT, FAILED);
            case IN_TRANSIT -> EnumSet.of(ARRIVED, FAILED);
            case ARRIVED -> EnumSet.of(DELIVERED, FAILED);
            case FAILED -> EnumSet.of(RETURNED);
            case DELIVERED, RETURNED, CANCELLED -> EnumSet.noneOf(DeliveryStatus.class);
        };
    }

    public boolean canTransitionTo(DeliveryStatus target) {
        return target != null && allowedNextStatuses().contains(target);
    }

}
