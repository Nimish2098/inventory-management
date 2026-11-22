package com.miyuki.Inventory.Management.logistics.live;

import com.miyuki.Inventory.Management.logistics.tracking.TrackingDtos.LatestPosition;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Coalesces updates between two broadcasts: at most ONE (the newest) position per vehicle is kept.
 * With 1,000 vehicles this turns hundreds of tiny messages per second into one array twice a second.
 */
@Component
public class LiveBuffer {

    private final ConcurrentHashMap<Long, LatestPosition> latest = new ConcurrentHashMap<>();

    public void offer(LatestPosition position) {
        latest.merge(
                position.vehicleId(),
                position,
                (current, incoming) -> incoming.recordedAt().isAfter(current.recordedAt()) ? incoming : current);
    }

    /** Removes and returns everything buffered so far, ordered by vehicle id. */
    public List<LatestPosition> drain() {
        List<LatestPosition> out = new ArrayList<>();
        for (Long id : List.copyOf(latest.keySet())) {
            LatestPosition p = latest.remove(id);
            if (p != null) {
                out.add(p);
            }
        }
        out.sort(Comparator.comparing(LatestPosition::vehicleId));
        return out;
    }

    public int size() {
        return latest.size();
    }
}
