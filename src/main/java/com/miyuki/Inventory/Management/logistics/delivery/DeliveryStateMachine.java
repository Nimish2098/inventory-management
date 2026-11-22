package com.miyuki.Inventory.Management.logistics.delivery;

import static com.miyuki.Inventory.Management.logistics.delivery.DeliveryAction.*;
import static com.miyuki.Inventory.Management.logistics.delivery.DeliveryStatus.*;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The single source of truth for which status changes are legal.
 *
 * <pre>
 * CREATED ──ASSIGN──▶ ASSIGNED ──PICK_UP──▶ PICKED_UP ──START_TRANSIT──▶ IN_TRANSIT ──ARRIVE──▶ ARRIVED ──DELIVER──▶ DELIVERED
 *    ▲  └─CANCEL─▶ CANCELLED ◀─CANCEL─┘ │                     │                  │                  │
 *    └────UNASSIGN──────────────────────┘                  FAIL                FAIL               FAIL
 *                                                             └────────────┬─────┴──────────────────┘
 *                                                                          ▼
 *                                                       FAILED ──RETURN──▶ RETURNED
 *                                                          └────RETRY────▶ IN_TRANSIT
 * </pre>
 *
 * Terminal states: DELIVERED, RETURNED, CANCELLED.
 */
public final class DeliveryStateMachine {

    private static final Map<DeliveryStatus, Map<DeliveryAction, DeliveryStatus>> TABLE =
            new EnumMap<>(DeliveryStatus.class);

    static {
        allow(CREATED, ASSIGN, ASSIGNED);
        allow(CREATED, CANCEL, CANCELLED);

        allow(ASSIGNED, UNASSIGN, CREATED);
        allow(ASSIGNED, PICK_UP, PICKED_UP);
        allow(ASSIGNED, CANCEL, CANCELLED);

        allow(PICKED_UP, START_TRANSIT, IN_TRANSIT);
        allow(PICKED_UP, FAIL, FAILED);

        allow(IN_TRANSIT, ARRIVE, ARRIVED);
        allow(IN_TRANSIT, FAIL, FAILED);

        allow(ARRIVED, DELIVER, DELIVERED);
        allow(ARRIVED, FAIL, FAILED);

        allow(FAILED, RETRY, IN_TRANSIT); // goods are still on the vehicle
        allow(FAILED, RETURN, RETURNED); // goods come back to the warehouse (stock restored)
    }

    private DeliveryStateMachine() {}

    private static void allow(DeliveryStatus from, DeliveryAction action, DeliveryStatus to) {
        TABLE.computeIfAbsent(from, k -> new EnumMap<>(DeliveryAction.class)).put(action, to);
    }

    public static Optional<DeliveryStatus> next(DeliveryStatus from, DeliveryAction action) {
        return Optional.ofNullable(TABLE.getOrDefault(from, Map.of()).get(action));
    }

    public static DeliveryStatus requireNext(DeliveryStatus from, DeliveryAction action) {
        return next(from, action).orElseThrow(() -> new InvalidTransitionException(from, action));
    }

    /** Actions that are legal from this status, in enum order (handy for enabling/disabling UI buttons). */
    public static Set<DeliveryAction> allowedActions(DeliveryStatus from) {
        Map<DeliveryAction, DeliveryStatus> row = TABLE.get(from);
        if (row == null) {
            return Set.of();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(row.keySet()));
    }

    public static boolean isTerminal(DeliveryStatus status) {
        return allowedActions(status).isEmpty();
    }
}
