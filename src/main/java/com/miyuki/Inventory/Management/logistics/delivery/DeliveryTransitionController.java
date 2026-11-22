package com.miyuki.Inventory.Management.logistics.delivery;

import com.miyuki.Inventory.Management.logistics.common.Actors;
import com.miyuki.Inventory.Management.logistics.delivery.DeliveryDtos.DeliveryResponse;
import com.miyuki.Inventory.Management.logistics.delivery.DeliveryTransitionDtos.TransitionRequest;
import jakarta.validation.Valid;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/deliveries/{id}")
@RequiredArgsConstructor
public class DeliveryTransitionController {

    private final DeliveryTransitionService service;

    /**
     * Send an Idempotency-Key header (e.g. a UUID generated per button tap) so a retried request after a network
     * glitch does not apply the action twice.
     */
    @PostMapping("/transitions")
    public DeliveryResponse transition(
            @PathVariable Long id,
            @Valid @RequestBody TransitionRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        return service.transition(id, request, Actors.current(), idempotencyKey);
    }

    /** Lets the frontend enable only the buttons that are legal for the current status. */
    @GetMapping("/allowed-actions")
    public Set<DeliveryAction> allowedActions(@PathVariable Long id) {
        return service.allowedActions(id);
    }
}
