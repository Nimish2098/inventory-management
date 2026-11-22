package com.miyuki.Inventory.Management.logistics.delivery;

/**
 * Bridge between deliveries and your existing inventory module.
 * Implement it as a Spring bean (see PHASE2_CHECKLIST.md). It runs inside the same database transaction as the
 * status change, so if it throws (e.g. insufficient stock) the delivery stays in its previous status.
 * If no bean exists, PICK_UP and RETURN simply do not touch stock (a warning is logged at startup).
 */
public interface StockHook {

    /** Called on PICK_UP: goods leave the warehouse. */
    void deductFor(Delivery delivery);

    /** Called on RETURN: goods are back in the warehouse. */
    void restoreFor(Delivery delivery);
}
