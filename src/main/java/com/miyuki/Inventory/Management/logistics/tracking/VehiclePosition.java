package com.miyuki.Inventory.Management.logistics.tracking;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One GPS ping. Uses a plain vehicleId (no entity join) because Phase 3 inserts these in high-volume batches. */
@Entity
@Table(name = "vehicle_position")
@Getter
@Setter
@NoArgsConstructor
public class VehiclePosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vehicle_id", nullable = false)
    private Long vehicleId;

    @Column(nullable = false)
    private Double lat;

    @Column(nullable = false)
    private Double lng;

    @Column(name = "speed_kmh")
    private Double speedKmh;

    private Double heading;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt = Instant.now();
}
