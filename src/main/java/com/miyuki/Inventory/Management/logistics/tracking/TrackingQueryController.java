package com.miyuki.Inventory.Management.logistics.tracking;

import com.miyuki.Inventory.Management.logistics.tracking.TrackingDtos.LatestPosition;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tracking/vehicles")
@RequiredArgsConstructor
public class TrackingQueryController {

    private final TrackingQueryService service;

    /** Latest known position of every vehicle that reported recently (for the dispatcher map). */
    @GetMapping("/live")
    public List<LatestPosition> live() {
        return service.live();
    }

    @GetMapping("/{id}/latest")
    public LatestPosition latest(@PathVariable Long id) {
        return service.latest(id);
    }

    /** Defaults: last hour, newest 500 pings, returned oldest-first. ISO-8601 instants, e.g. 2026-11-21T10:00:00Z */
    @GetMapping("/{id}/history")
    public List<LatestPosition> history(
            @PathVariable Long id,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "500") int limit) {
        return service.history(id, from, to, limit);
    }
}
