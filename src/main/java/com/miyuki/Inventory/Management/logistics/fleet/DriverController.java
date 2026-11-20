package com.miyuki.Inventory.Management.logistics.fleet;

import com.miyuki.Inventory.Management.logistics.fleet.FleetDtos.DriverRequest;
import com.miyuki.Inventory.Management.logistics.fleet.FleetDtos.DriverResponse;
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
@RequestMapping("/api/drivers")
@RequiredArgsConstructor
public class DriverController {

    private final DriverService service;

    @PostMapping
    public ResponseEntity<DriverResponse> create(@Valid @RequestBody DriverRequest req) {
        DriverResponse created = service.create(req);
        return ResponseEntity.created(URI.create("/api/drivers/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    public DriverResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping
    public Page<DriverResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @PutMapping("/{id}")
    public DriverResponse update(@PathVariable Long id, @Valid @RequestBody DriverRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
