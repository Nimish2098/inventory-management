package com.miyuki.Inventory.Management.logistics.route;

import com.miyuki.Inventory.Management.logistics.common.BadRequestException;
import com.miyuki.Inventory.Management.logistics.common.ConflictException;import com.miyuki.Inventory.Management.logistics.common.NotFoundException;
import com.miyuki.Inventory.Management.logistics.delivery.Delivery;
import com.miyuki.Inventory.Management.logistics.delivery.DeliveryRepository;
import com.miyuki.Inventory.Management.logistics.fleet.Vehicle;
import com.miyuki.Inventory.Management.logistics.fleet.VehicleRepository;
import com.miyuki.Inventory.Management.logistics.fleet.VehicleStatus;
import com.miyuki.Inventory.Management.logistics.route.RouteDtos.RouteRequest;
import com.miyuki.Inventory.Management.logistics.route.RouteDtos.RouteResponse;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class RouteService {

    private final RouteRepository routes;
    private final VehicleRepository vehicles;
    private final DeliveryRepository deliveries;

    public RouteResponse create(RouteRequest req) {
        Vehicle vehicle = vehicles.findById(req.vehicleId())
                .orElseThrow(() -> new NotFoundException("Vehicle", req.vehicleId()));

        if (vehicle.getStatus() == VehicleStatus.MAINTENANCE || vehicle.getStatus() == VehicleStatus.OFFLINE) {
            throw new ConflictException("Vehicle " + vehicle.getId() + " is " + vehicle.getStatus());
        }
        // Friendly 409 for the normal case. The partial unique index in the DB is the real guarantee under races.
        if (routes.existsByVehicleIdAndStatusIn(vehicle.getId(), RouteStatus.OPEN)) {
            throw new ConflictException("Vehicle " + vehicle.getId() + " already has an active route");
        }

        List<Long> ids = req.deliveryIds() == null ? List.of() : req.deliveryIds();
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new BadRequestException("deliveryIds contains duplicates");
        }
        Map<Long, Delivery> found =
                deliveries.findAllById(ids).stream().collect(Collectors.toMap(Delivery::getId, Function.identity()));

        Route route = new Route();
        route.setVehicle(vehicle);
        route.setStatus(RouteStatus.PLANNED);
        route.setPlannedStart(req.plannedStart());

        int sequence = 1;
        for (Long id : ids) {
            Delivery d = found.get(id);
            if (d == null) {
                throw new NotFoundException("Delivery", id);
            }
            route.addStop(d, sequence++);
        }
        return RouteResponse.from(routes.save(route));
    }

    @Transactional(readOnly = true)
    public RouteResponse get(Long id) {
        return RouteResponse.from(find(id));
    }

    @Transactional(readOnly = true)
    public List<RouteResponse> listForVehicle(Long vehicleId) {
        return routes.findByVehicleIdOrderByCreatedAtDesc(vehicleId).stream()
                .map(RouteResponse::from)
                .toList();
    }

    public RouteResponse cancel(Long id) {
        Route route = find(id);
        if (route.getStatus() == RouteStatus.COMPLETED || route.getStatus() == RouteStatus.CANCELLED) {
            throw new ConflictException("Route " + id + " is already " + route.getStatus());
        }
        route.setStatus(RouteStatus.CANCELLED);
        return RouteResponse.from(route);
    }

    private Route find(Long id) {
        return routes.findWithStopsById(id).orElseThrow(() -> new NotFoundException("Route", id));
    }
}
