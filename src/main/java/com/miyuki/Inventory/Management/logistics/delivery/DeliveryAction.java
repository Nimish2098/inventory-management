package com.miyuki.Inventory.Management.logistics.delivery;

/** What someone wants to do to a delivery. The state machine decides whether it is allowed right now. */
public enum DeliveryAction {
    ASSIGN,
    UNASSIGN,
    PICK_UP,
    START_TRANSIT,
    ARRIVE,
    DELIVER,
    FAIL,
    RETRY,
    RETURN,
    CANCEL
}
