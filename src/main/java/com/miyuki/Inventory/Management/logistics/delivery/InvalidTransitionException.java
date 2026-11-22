package com.miyuki.Inventory.Management.logistics.delivery;

import com.miyuki.Inventory.Management.logistics.common.ConflictException;

/** Maps to HTTP 409 through ApiExceptionHandler (it is a ConflictException). */
public class InvalidTransitionException extends ConflictException {
    public InvalidTransitionException(DeliveryStatus from, DeliveryAction action) {
        super("Cannot " + action + " a delivery that is " + from);
    }
}
