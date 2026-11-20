package com.miyuki.Inventory.Management.logistics.route;

import static com.miyuki.Inventory.Management.logistics.TestData.delivery;
import static com.miyuki.Inventory.Management.logistics.TestData.vehicle;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miyuki.Inventory.Management.logistics.common.BadRequestException;
import com.miyuki.Inventory.Management.logistics.common.ConflictException;import com.miyuki.Inventory.Management.logistics.common.NotFoundException;
import com.miyuki.Inventory.Management.logistics.delivery.Delivery;
import com.miyuki.Inventory.Management.logistics.delivery.DeliveryRepository;
import com.miyuki.Inventory.Management.logistics.fleet.Vehicle;
import com.miyuki.Inventory.Management.logistics.fleet.VehicleRepository;
import com.miyuki.Inventory.Management.logistics.fleet.VehicleStatus;
import com.miyuki.Inventory.Management.logistics.route.RouteDtos.RouteRequest;
import com.miyuki.Inventory.Management.logistics.route.RouteDtos.RouteResponse;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RouteServiceTest {

    @Mock RouteRepository routes;
    @Mock VehicleRepository vehicles;
    @Mock DeliveryRepository deliveries;
    @InjectMocks RouteService service;

    Vehicle truck;

    @BeforeEach
    void setUp() {
        truck = vehicle("KA01RS0001");
        truck.setId(1L);
    }

    private Delivery deliveryWithId(long id) {
        Delivery d = delivery(id * 100);
        d.setId(id);
        return d;
    }

    @Test
    void rejectsSecondOpenRouteForSameVehicle() {
        when(vehicles.findById(1L)).thenReturn(Optional.of(truck));
        when(routes.existsByVehicleIdAndStatusIn(1L, RouteStatus.OPEN)).thenReturn(true);

        assertThatThrownBy(() -> service.create(new RouteRequest(1L, null, List.of())))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already has an active route");
        verify(routes, never()).save(any());
    }

    @Test
    void rejectsVehicleUnderMaintenance() {
        truck.setStatus(VehicleStatus.MAINTENANCE);
        when(vehicles.findById(1L)).thenReturn(Optional.of(truck));

        assertThatThrownBy(() -> service.create(new RouteRequest(1L, null, List.of())))
                .isInstanceOf(ConflictException.class);
        verify(routes, never()).save(any());
    }

    @Test
    void unknownVehicleIsNotFound() {
        when(vehicles.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new RouteRequest(99L, null, List.of())))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void unknownDeliveryIsNotFound() {
        when(vehicles.findById(1L)).thenReturn(Optional.of(truck));
        when(routes.existsByVehicleIdAndStatusIn(1L, RouteStatus.OPEN)).thenReturn(false);
        when(deliveries.findAllById(List.of(10L))).thenReturn(List.of());

        assertThatThrownBy(() -> service.create(new RouteRequest(1L, null, List.of(10L))))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("10");
        verify(routes, never()).save(any());
    }

    @Test
    void duplicateDeliveryIdsAreRejected() {
        when(vehicles.findById(1L)).thenReturn(Optional.of(truck));
        when(routes.existsByVehicleIdAndStatusIn(1L, RouteStatus.OPEN)).thenReturn(false);

        assertThatThrownBy(() -> service.create(new RouteRequest(1L, null, List.of(10L, 10L))))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void createsPlannedRouteWithStopsInRequestedOrder() {
        when(vehicles.findById(1L)).thenReturn(Optional.of(truck));
        when(routes.existsByVehicleIdAndStatusIn(1L, RouteStatus.OPEN)).thenReturn(false);
        // repository returns them in a different order than requested: the service must restore request order
        when(deliveries.findAllById(List.of(10L, 20L))).thenReturn(List.of(deliveryWithId(20L), deliveryWithId(10L)));
        when(routes.save(any(Route.class))).thenAnswer(inv -> inv.getArgument(0));

        RouteResponse response = service.create(new RouteRequest(1L, null, List.of(10L, 20L)));

        assertThat(response.status()).isEqualTo(RouteStatus.PLANNED);
        assertThat(response.vehicleId()).isEqualTo(1L);
        assertThat(response.stops()).extracting(RouteDtos.StopResponse::deliveryId).containsExactly(10L, 20L);
        assertThat(response.stops()).extracting(RouteDtos.StopResponse::sequence).containsExactly(1, 2);
    }

    @Test
    void cancelFinishedRouteIsRejected() {
        Route done = new Route();
        done.setVehicle(truck);
        done.setStatus(RouteStatus.COMPLETED);
        when(routes.findWithStopsById(5L)).thenReturn(Optional.of(done));

        assertThatThrownBy(() -> service.cancel(5L)).isInstanceOf(ConflictException.class);
    }

    @Test
    void cancelOpenRouteMarksItCancelled() {
        Route open = new Route();
        open.setVehicle(truck);
        open.setStatus(RouteStatus.PLANNED);
        when(routes.findWithStopsById(5L)).thenReturn(Optional.of(open));

        assertThat(service.cancel(5L).status()).isEqualTo(RouteStatus.CANCELLED);
    }
}
