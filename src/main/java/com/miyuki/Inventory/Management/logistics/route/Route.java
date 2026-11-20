package com.miyuki.Inventory.Management.logistics.route;

import com.miyuki.Inventory.Management.logistics.delivery.Delivery;
import com.miyuki.Inventory.Management.logistics.fleet.Vehicle;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "route")
@Getter
@Setter
@NoArgsConstructor
public class Route {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RouteStatus status = RouteStatus.PLANNED;

    @Column(name = "planned_start")
    private Instant plannedStart;

    @Column(name = "total_distance_m")
    private Long totalDistanceM;

    @Column(name = "total_duration_s")
    private Long totalDurationS;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "route", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stopSequence ASC")
    private List<RouteStop> stops = new ArrayList<>();

    public RouteStop addStop(Delivery delivery, int sequence) {
        RouteStop stop = new RouteStop();
        stop.setRoute(this);
        stop.setDelivery(delivery);
        stop.setStopSequence(sequence);
        stops.add(stop);
        return stop;
    }
}
