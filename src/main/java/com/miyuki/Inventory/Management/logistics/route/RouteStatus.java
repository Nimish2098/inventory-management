package com.miyuki.Inventory.Management.logistics.route;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public enum RouteStatus {
    PLANNED,
    ACTIVE,
    COMPLETED,
    CANCELLED;

    /** Statuses that block the vehicle from getting another route (mirrors the partial unique index). */
    public static final Set<RouteStatus> OPEN = Collections.unmodifiableSet(EnumSet.of(PLANNED, ACTIVE));
}
