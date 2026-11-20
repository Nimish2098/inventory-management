package com.miyuki.Inventory.Management.logistics.route;

import static com.miyuki.Inventory.Management.logistics.TestData.delivery;
import static com.miyuki.Inventory.Management.logistics.TestData.route;
import static com.miyuki.Inventory.Management.logistics.TestData.vehicle;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.miyuki.Inventory.Management.logistics.AbstractRepositoryTest;
import com.miyuki.Inventory.Management.logistics.delivery.Delivery;
import com.miyuki.Inventory.Management.logistics.delivery.DeliveryRepository;
import com.miyuki.Inventory.Management.logistics.fleet.Vehicle;
import com.miyuki.Inventory.Management.logistics.fleet.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

class RouteRepositoryTest extends AbstractRepositoryTest {

    @Autowired RouteRepository routes;
    @Autowired VehicleRepository vehicles;
    @Autowired DeliveryRepository deliveries;
    @Autowired TestEntityManager em;

    @Test
    void savesRouteWithStopsAndLoadsThemInSequenceOrder() {
        Vehicle v = vehicles.saveAndFlush(vehicle("KA01RT0001"));
        Delivery d1 = deliveries.saveAndFlush(delivery(1L));
        Delivery d2 = deliveries.saveAndFlush(delivery(2L));

        Route r = route(v, RouteStatus.PLANNED);
        r.addStop(d1, 2);
        r.addStop(d2, 1);
        Long id = routes.saveAndFlush(r).getId();
        em.clear();

        Route loaded = routes.findWithStopsById(id).orElseThrow();
        assertThat(loaded.getStops()).extracting(RouteStop::getStopSequence).containsExactly(1, 2);
        assertThat(loaded.getStops()).extracting(s -> s.getDelivery().getId()).containsExactly(d2.getId(), d1.getId());
        assertThat(loaded.getStops()).allMatch(s -> s.getStatus() == StopStatus.PENDING);
    }

    @Test
    void vehicleCannotHaveTwoOpenRoutes() {
        Vehicle v = vehicles.saveAndFlush(vehicle("KA01RT0002"));
        routes.saveAndFlush(route(v, RouteStatus.ACTIVE));

        assertThatThrownBy(() -> routes.saveAndFlush(route(v, RouteStatus.PLANNED)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void vehicleCanGetNewRouteOnceThePreviousOneIsFinished() {
        Vehicle v = vehicles.saveAndFlush(vehicle("KA01RT0003"));
        routes.saveAndFlush(route(v, RouteStatus.COMPLETED));
        routes.saveAndFlush(route(v, RouteStatus.CANCELLED));

        Route open = routes.saveAndFlush(route(v, RouteStatus.PLANNED));

        assertThat(open.getId()).isNotNull();
        assertThat(routes.existsByVehicleIdAndStatusIn(v.getId(), RouteStatus.OPEN)).isTrue();
    }

    @Test
    void differentVehiclesCanEachHaveAnOpenRoute() {
        Vehicle a = vehicles.saveAndFlush(vehicle("KA01RT0004"));
        Vehicle b = vehicles.saveAndFlush(vehicle("KA01RT0005"));

        routes.saveAndFlush(route(a, RouteStatus.ACTIVE));
        routes.saveAndFlush(route(b, RouteStatus.ACTIVE));

        assertThat(routes.existsByVehicleIdAndStatusIn(a.getId(), RouteStatus.OPEN)).isTrue();
        assertThat(routes.existsByVehicleIdAndStatusIn(b.getId(), RouteStatus.OPEN)).isTrue();
    }

    @Test
    void existsCheckIgnoresFinishedRoutes() {
        Vehicle v = vehicles.saveAndFlush(vehicle("KA01RT0006"));
        routes.saveAndFlush(route(v, RouteStatus.COMPLETED));
        assertThat(routes.existsByVehicleIdAndStatusIn(v.getId(), RouteStatus.OPEN)).isFalse();
    }

    @Test
    void duplicateStopSequenceInOneRouteIsRejected() {
        Vehicle v = vehicles.saveAndFlush(vehicle("KA01RT0007"));
        Delivery d1 = deliveries.saveAndFlush(delivery(10L));
        Delivery d2 = deliveries.saveAndFlush(delivery(11L));
        Route r = route(v, RouteStatus.PLANNED);
        r.addStop(d1, 1);
        r.addStop(d2, 1);

        assertThatThrownBy(() -> routes.saveAndFlush(r)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameDeliveryTwiceInOneRouteIsRejected() {
        Vehicle v = vehicles.saveAndFlush(vehicle("KA01RT0008"));
        Delivery d = deliveries.saveAndFlush(delivery(12L));
        Route r = route(v, RouteStatus.PLANNED);
        r.addStop(d, 1);
        r.addStop(d, 2);

        assertThatThrownBy(() -> routes.saveAndFlush(r)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
