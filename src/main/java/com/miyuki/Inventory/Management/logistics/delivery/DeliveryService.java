package com.miyuki.Inventory.Management.logistics.delivery;

import com.miyuki.Inventory.Management.logistics.common.ConflictException;import com.miyuki.Inventory.Management.logistics.common.NotFoundException;
import com.miyuki.Inventory.Management.logistics.delivery.DeliveryDtos.DeliveryEventResponse;
import com.miyuki.Inventory.Management.logistics.delivery.DeliveryDtos.DeliveryRequest;
import com.miyuki.Inventory.Management.logistics.delivery.DeliveryDtos.DeliveryResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 1: plain CRUD. Status changes (ASSIGNED, PICKED_UP, ...) arrive in Phase 2 with the state machine,
 * so details can only be edited/deleted while the delivery is still CREATED.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class DeliveryService {

    private static final String SYSTEM = "system";

    private final DeliveryRepository deliveries;
    private final DeliveryEventRepository events;

    public DeliveryResponse create(DeliveryRequest req) {
        Delivery d = new Delivery();
        apply(d, req);
        d.setStatus(DeliveryStatus.CREATED);
        Delivery saved = deliveries.save(d);
        events.save(DeliveryEvent.of(saved, null, DeliveryStatus.CREATED, SYSTEM, "delivery created"));
        return DeliveryResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public DeliveryResponse get(Long id) {
        return DeliveryResponse.from(find(id));
    }

    @Transactional(readOnly = true)
    public Page<DeliveryResponse> list(DeliveryStatus status, Pageable pageable) {
        Page<Delivery> page = status == null ? deliveries.findAll(pageable) : deliveries.findByStatus(status, pageable);
        return page.map(DeliveryResponse::from);
    }

    public DeliveryResponse update(Long id, DeliveryRequest req) {
        Delivery d = find(id);
        requireCreated(d, "edited");
        apply(d, req);
        return DeliveryResponse.from(d);
    }

    public void delete(Long id) {
        Delivery d = find(id);
        requireCreated(d, "deleted");
        events.deleteByDeliveryId(id);
        deliveries.delete(d); // referenced by a route stop -> FK violation -> 409
    }

    @Transactional(readOnly = true)
    public List<DeliveryEventResponse> events(Long id) {
        find(id);
        return events.findByDeliveryIdOrderByCreatedAtAscIdAsc(id).stream()
                .map(DeliveryEventResponse::from)
                .toList();
    }

    private Delivery find(Long id) {
        return deliveries.findById(id).orElseThrow(() -> new NotFoundException("Delivery", id));
    }

    private static void requireCreated(Delivery d, String verb) {
        if (d.getStatus() != DeliveryStatus.CREATED) {
            throw new ConflictException(
                    "Delivery " + d.getId() + " is " + d.getStatus() + " and can no longer be " + verb);
        }
    }

    private static void apply(Delivery d, DeliveryRequest r) {
        d.setOrderId(r.orderId());
        d.setDestinationLat(r.destinationLat().doubleValue());
        d.setDestinationLng(r.destinationLng().doubleValue());
        d.setDestinationAddress(r.destinationAddress());
        d.setWindowStart(r.windowStart());
        d.setWindowEnd(r.windowEnd());
        d.setWeightKg(r.weightKg());
        d.setVolumeM3(r.volumeM3());
    }
}
