package com.miyuki.Inventory.Management.logistics.delivery;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Append-only audit record of every status change of a delivery. */
@Entity
@Table(name = "delivery_event")
@Getter
@Setter
@NoArgsConstructor
public class DeliveryEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delivery_id", nullable = false)
    private Delivery delivery;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private DeliveryStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private DeliveryStatus toStatus;

    @Column(nullable = false, length = 100)
    private String actor;

    @Column(length = 255)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public static DeliveryEvent of(
            Delivery delivery, DeliveryStatus from, DeliveryStatus to, String actor, String reason) {
        DeliveryEvent e = new DeliveryEvent();
        e.setDelivery(delivery);
        e.setFromStatus(from);
        e.setToStatus(to);
        e.setActor(actor);
        e.setReason(reason);
        return e;
    }
}
