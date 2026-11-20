package com.miyuki.Inventory.Management.logistics.route;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RouteRepository extends JpaRepository<Route, Long> {

    boolean existsByVehicleIdAndStatusIn(Long vehicleId, Collection<RouteStatus> statuses);

    @Query("select distinct r from Route r left join fetch r.stops where r.id = :id")
    Optional<Route> findWithStopsById(@Param("id") Long id);

    @EntityGraph(attributePaths = "stops")
    List<Route> findByVehicleIdOrderByCreatedAtDesc(Long vehicleId);
}
