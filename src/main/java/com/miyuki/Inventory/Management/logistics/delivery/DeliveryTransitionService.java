package com.miyuki.Inventory.Management.logistics.delivery;

import static com.miyuki.Inventory.Management.logistics.delivery.DeliveryAction.CANCEL;
import static com.miyuki.Inventory.Management.logistics.delivery.DeliveryAction.FAIL;
import static com.miyuki.Inventory.Management.logistics.delivery.DeliveryAction.PICK_UP;
import static com.miyuki.Inventory.Management.logistics.delivery.DeliveryAction.RETURN;

import com.miyuki.Inventory.Management.logistics.common.BadRequestException;
import com.miyuki.Inventory.Management.logistics.common.NotFoundException;
import com.miyuki.Inventory.Management.logistics.delivery.DeliveryDtos.DeliveryResponse;
import com.miyuki.Inventory.Management.logistics.delivery.DeliveryTransitionDtos.TransitionRequest;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies status changes. Everything in one transaction: validate against the state machine, call stock hooks,
 * update the delivery, append the audit event. If any step throws, none of it is saved.
 * Concurrent changes to the same delivery are caught by the @Version column (-> HTTP 409).
 */
@Slf4j
@Service
@Transactional
public class DeliveryTransitionService {

    private static final int MAX_KEY_LENGTH = 100;

    private final DeliveryRepository deliveries;
    private final DeliveryEventRepository events;
    private final List<StockHook> stockHooks;

    public DeliveryTransitionService(
            DeliveryRepository deliveries, DeliveryEventRepository events, List<StockHook> stockHooks) {
        this.deliveries = deliveries;
        this.events = events;
        this.stockHooks = stockHooks;
        if (stockHooks.isEmpty()) {
            log.warn("No StockHook bean found: PICK_UP and RETURN will NOT change inventory stock.");
        }
    }

    /** Public entry point used by the REST controller. */
    public DeliveryResponse transition(Long id, TransitionRequest request, String actor, String idempotencyKey) {
        Delivery delivery = deliveries.findById(id).orElseThrow(() -> new NotFoundException("Delivery", id));
        String key = normalizeKey(idempotencyKey);

        // Retry of a request we already processed: return the current state, change nothing.
        if (key != null && events.existsByDeliveryIdAndIdempotencyKey(id, key)) {
            return DeliveryResponse.from(delivery);
        }

        apply(delivery, request.action(), actor, request.reason(), key);
        return DeliveryResponse.from(delivery);
    }

    /**
     * Core transition logic. Also used by RouteService (assign/unassign when routes are created/cancelled).
     * MANDATORY: callers must already be inside a transaction.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void apply(Delivery delivery, DeliveryAction action, String actor, String reason, String key) {
        DeliveryStatus from = delivery.getStatus();
        DeliveryStatus to = DeliveryStateMachine.requireNext(from, action);

        if ((action == FAIL || action == CANCEL) && (reason == null || reason.isBlank())) {
            throw new BadRequestException("A reason is required for " + action);
        }

        // Side effects first: if stock is insufficient this throws and nothing below happens.
        if (action == PICK_UP) {
            stockHooks.forEach(hook -> hook.deductFor(delivery));
        } else if (action == RETURN) {
            stockHooks.forEach(hook -> hook.restoreFor(delivery));
        }

        delivery.setStatus(to);
        events.save(DeliveryEvent.of(delivery, from, to, action, actor, reason, key));
        deliveries.saveAndFlush(delivery); // flush now so a stale @Version fails here, inside the transaction
    }

    @Transactional(readOnly = true)
    public Set<DeliveryAction> allowedActions(Long id) {
        Delivery delivery = deliveries.findById(id).orElseThrow(() -> new NotFoundException("Delivery", id));
        return DeliveryStateMachine.allowedActions(delivery.getStatus());
    }

    private static String normalizeKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String trimmed = key.trim();
        if (trimmed.length() > MAX_KEY_LENGTH) {
            throw new BadRequestException("Idempotency-Key must be at most " + MAX_KEY_LENGTH + " characters");
        }
        return trimmed;
    }
}
