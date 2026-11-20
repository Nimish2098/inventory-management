package com.miyuki.Inventory.Management.logistics.route;

import com.miyuki.Inventory.Management.logistics.route.RouteDtos.RouteRequest;
import com.miyuki.Inventory.Management.logistics.route.RouteDtos.RouteResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/routes")
@RequiredArgsConstructor
public class RouteController {

    private final RouteService service;

    @PostMapping
    public ResponseEntity<RouteResponse> create(@Valid @RequestBody RouteRequest req) {
        RouteResponse created = service.create(req);
        return ResponseEntity.created(URI.create("/api/routes/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    public RouteResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping
    public List<RouteResponse> listForVehicle(@RequestParam Long vehicleId) {
        return service.listForVehicle(vehicleId);
    }

    @PostMapping("/{id}/cancel")
    public RouteResponse cancel(@PathVariable Long id) {
        return service.cancel(id);
    }
}
