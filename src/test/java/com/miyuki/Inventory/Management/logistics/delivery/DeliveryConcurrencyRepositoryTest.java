package com.miyuki.Inventory.Management.logistics.delivery;

import static com.miyuki.Inventory.Management.logistics.TestData.delivery;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.miyuki.Inventory.Management.logistics.AbstractRepositoryTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/** Needs Docker (Testcontainers). Runs the real Flyway migrations, including V3. */
class DeliveryConcurrencyRepositoryTest extends AbstractRepositoryTest {

    @Autowired DeliveryRepository deliveries;
    @Autowired DeliveryEventRepository events;
    @PersistenceContext EntityManager em;

    @Test
    void secondWriterWithStaleVersionLoses() {
        Long id = deliveries.saveAndFlush(delivery(1L)).getId();
        em.clear();

        // Two dispatchers load the same delivery (same version).
        Delivery stale = deliveries.findById(id).orElseThrow();
        em.detach(stale);
        Delivery fresh = deliveries.findById(id).orElseThrow();

        // The first one wins.
        fresh.setStatus(DeliveryStatus.ASSIGNED);
        deliveries.saveAndFlush(fresh);
        em.clear();

        // The second one is rejected instead of silently overwriting.
        stale.setStatus(DeliveryStatus.CANCELLED);
        assertThatThrownBy(() -> deliveries.saveAndFlush(stale))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void versionIncreasesOnEveryUpdate() {
        Delivery d = deliveries.saveAndFlush(delivery(2L));
        Long v0 = d.getVersion();

        d.setStatus(DeliveryStatus.ASSIGNED);
        deliveries.saveAndFlush(d);

        assertThat(d.getVersion()).isGreaterThan(v0);
    }

    @Test
    void actionAndKeyArePersisted() {
        Delivery d = deliveries.saveAndFlush(delivery(3L));
        Long eventId = events.saveAndFlush(DeliveryEvent.of(
                        d, DeliveryStatus.CREATED, DeliveryStatus.ASSIGNED, DeliveryAction.ASSIGN, "dispatcher", null, "k-1"))
                .getId();
        em.clear();

        DeliveryEvent loaded = events.findById(eventId).orElseThrow();
        assertThat(loaded.getAction()).isEqualTo(DeliveryAction.ASSIGN);
        assertThat(loaded.getIdempotencyKey()).isEqualTo("k-1");
        assertThat(events.existsByDeliveryIdAndIdempotencyKey(d.getId(), "k-1")).isTrue();
        assertThat(events.existsByDeliveryIdAndIdempotencyKey(d.getId(), "other")).isFalse();
    }

    @Test
    void sameKeyTwiceOnOneDeliveryIsRejectedByDatabase() {
        Delivery d = deliveries.saveAndFlush(delivery(4L));
        events.saveAndFlush(DeliveryEvent.of(
                d, DeliveryStatus.CREATED, DeliveryStatus.ASSIGNED, DeliveryAction.ASSIGN, "dispatcher", null, "dup"));

        assertThatThrownBy(() -> events.saveAndFlush(DeliveryEvent.of(
                        d, DeliveryStatus.ASSIGNED, DeliveryStatus.PICKED_UP, DeliveryAction.PICK_UP, "driver", null, "dup")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameKeyOnDifferentDeliveriesIsAllowed() {
        Delivery a = deliveries.saveAndFlush(delivery(5L));
        Delivery b = deliveries.saveAndFlush(delivery(6L));

        events.saveAndFlush(DeliveryEvent.of(
                a, DeliveryStatus.CREATED, DeliveryStatus.ASSIGNED, DeliveryAction.ASSIGN, "dispatcher", null, "shared"));
        events.saveAndFlush(DeliveryEvent.of(
                b, DeliveryStatus.CREATED, DeliveryStatus.ASSIGNED, DeliveryAction.ASSIGN, "dispatcher", null, "shared"));

        assertThat(events.existsByDeliveryIdAndIdempotencyKey(a.getId(), "shared")).isTrue();
        assertThat(events.existsByDeliveryIdAndIdempotencyKey(b.getId(), "shared")).isTrue();
    }

    @Test
    void manyEventsWithoutAKeyAreAllowed() {
        Delivery d = deliveries.saveAndFlush(delivery(7L));

        events.saveAndFlush(DeliveryEvent.of(d, null, DeliveryStatus.CREATED, "system", "created"));
        events.saveAndFlush(DeliveryEvent.of(d, DeliveryStatus.CREATED, DeliveryStatus.ASSIGNED, "dispatcher", null));
        events.saveAndFlush(DeliveryEvent.of(d, DeliveryStatus.ASSIGNED, DeliveryStatus.PICKED_UP, "driver", null));

        assertThat(events.findByDeliveryIdOrderByCreatedAtAscIdAsc(d.getId())).hasSize(3);
    }
}
