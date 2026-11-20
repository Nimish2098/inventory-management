package com.miyuki.Inventory.Management.logistics.fleet;

import com.miyuki.Inventory.Management.logistics.fleet.FleetDtos.VehicleRequest;
import com.miyuki.Inventory.Management.logistics.fleet.FleetDtos.VehicleResponse;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/vehicles")
@RequiredArgsConstructor
public class VehicleController {

    private final VehicleService service;

    @PostMapping
    public ResponseEntity<VehicleResponse> create(@Valid @RequestBody VehicleRequest req) {
        VehicleResponse created = service.create(req);
        return ResponseEntity.created(URI.create("/api/vehicles/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    public VehicleResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping
    public Page<VehicleResponse> list(
            @RequestParam(required = false) VehicleStatus status, @PageableDefault(size = 20) Pageable pageable) {
        return service.list(status, pageable);
    }

    @PutMapping("/{id}")
    public VehicleResponse update(@PathVariable Long id, @Valid @RequestBody VehicleRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
