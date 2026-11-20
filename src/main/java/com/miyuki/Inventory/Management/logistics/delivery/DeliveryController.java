package com.miyuki.Inventory.Management.logistics.delivery;

import com.miyuki.Inventory.Management.logistics.delivery.DeliveryDtos.DeliveryEventResponse;
import com.miyuki.Inventory.Management.logistics.delivery.DeliveryDtos.DeliveryRequest;
import com.miyuki.Inventory.Management.logistics.delivery.DeliveryDtos.DeliveryResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/deliveries")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryService service;

    @PostMapping
    public ResponseEntity<DeliveryResponse> create(@Valid @RequestBody DeliveryRequest req) {
        DeliveryResponse created = service.create(req);
        return ResponseEntity.created(URI.create("/api/deliveries/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    public DeliveryResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping
    public Page<DeliveryResponse> list(
            @RequestParam(required = false) DeliveryStatus status, @PageableDefault(size = 20) Pageable pageable) {
        return service.list(status, pageable);
    }

    @PutMapping("/{id}")
    public DeliveryResponse update(@PathVariable Long id, @Valid @RequestBody DeliveryRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @GetMapping("/{id}/events")
    public List<DeliveryEventResponse> events(@PathVariable Long id) {
        return service.events(id);
    }
}
