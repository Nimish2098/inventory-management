package com.miyuki.Inventory.Management.logistics.delivery;

import static com.miyuki.Inventory.Management.logistics.TestData.delivery;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.miyuki.Inventory.Management.logistics.AbstractRepositoryTest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

class DeliveryRepositoryTest extends AbstractRepositoryTest {

    @Autowired DeliveryRepository deliveries;
    @Autowired DeliveryEventRepository events;
    @Autowired TestEntityManager em;

    @Test
    void savesAndLoadsDelivery() {
        Instant start = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Delivery d = delivery(501L);
        d.setWindowStart(start);
        d.setWindowEnd(start.plus(2, ChronoUnit.HOURS));
        Long id = deliveries.saveAndFlush(d).getId();
        em.clear();

        Delivery loaded = deliveries.findById(id).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(DeliveryStatus.CREATED);
        assertThat(loaded.getOrderId()).isEqualTo(501L);
        assertThat(loaded.getDestinationLat()).isEqualTo(12.9716);
        assertThat(loaded.getWindowEnd()).isAfter(loaded.getWindowStart());
    }

    @Test
    void nullDestinationIsRejected() {
        Delivery d = delivery(502L);
        d.setDestinationLat(null);
        assertThatThrownBy(() -> deliveries.saveAndFlush(d)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void outOfRangeLatitudeIsRejectedByDatabase() {
        Delivery d = delivery(503L);
        d.setDestinationLat(95.0);
        assertThatThrownBy(() -> deliveries.saveAndFlush(d)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void windowEndBeforeStartIsRejectedByDatabase() {
        Delivery d = delivery(504L);
        Instant now = Instant.now();
        d.setWindowStart(now);
        d.setWindowEnd(now.minus(1, ChronoUnit.HOURS));
        assertThatThrownBy(() -> deliveries.saveAndFlush(d)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void eventsAreReturnedInChronologicalOrder() {
        Delivery d = deliveries.saveAndFlush(delivery(505L));
        Instant t0 = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        DeliveryEvent second = DeliveryEvent.of(d, DeliveryStatus.CREATED, DeliveryStatus.ASSIGNED, "dispatcher", null);
        second.setCreatedAt(t0.plusSeconds(10));
        DeliveryEvent first = DeliveryEvent.of(d, null, DeliveryStatus.CREATED, "system", "created");
        first.setCreatedAt(t0);
        events.saveAllAndFlush(List.of(second, first));
        em.clear();

        List<DeliveryEvent> result = events.findByDeliveryIdOrderByCreatedAtAscIdAsc(d.getId());
        assertThat(result)
                .extracting(DeliveryEvent::getToStatus)
                .containsExactly(DeliveryStatus.CREATED, DeliveryStatus.ASSIGNED);
    }

    @Test
    void eventWithoutActorIsRejected() {
        Delivery d = deliveries.saveAndFlush(delivery(506L));
        DeliveryEvent e = DeliveryEvent.of(d, null, DeliveryStatus.CREATED, null, null);
        assertThatThrownBy(() -> events.saveAndFlush(e)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
