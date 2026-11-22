package com.miyuki.Inventory.Management.logistics.tracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.miyuki.Inventory.Management.logistics.common.BadRequestException;
import com.miyuki.Inventory.Management.logistics.common.NotFoundException;
import com.miyuki.Inventory.Management.logistics.fleet.VehicleRepository;
import com.miyuki.Inventory.Management.logistics.tracking.TrackingDtos.LatestPosition;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class TrackingQueryServiceTest {

    static final Instant T = Instant.parse("2026-11-21T10:00:00Z");

    @Mock PositionCache cache;
    @Mock VehiclePositionRepository positions;
    @Mock VehicleRepository vehicles;
    TrackingQueryService service;

    @BeforeEach
    void setUp() {
        service = new TrackingQueryService(cache, positions, vehicles);
    }

    private static VehiclePosition stored(long vehicleId, double lat, Instant at) {
        VehiclePosition p = new VehiclePosition();
        p.setVehicleId(vehicleId);
        p.setLat(lat);
        p.setLng(77.59);
        p.setRecordedAt(at);
        return p;
    }

    @Test
    void latestPrefersTheCache() {
        LatestPosition cached = new LatestPosition(1L, 12.9, 77.5, 30.0, 90.0, T);
        when(cache.latest(1L)).thenReturn(Optional.of(cached));

        assertThat(service.latest(1L)).isEqualTo(cached);
        verifyNoInteractions(positions);
    }

    @Test
    void latestFallsBackToTheDatabaseWhenTheCacheIsEmpty() {
        when(cache.latest(1L)).thenReturn(Optional.empty());
        when(positions.findFirstByVehicleIdOrderByRecordedAtDesc(1L)).thenReturn(Optional.of(stored(1L, 12.5, T)));

        LatestPosition result = service.latest(1L);

        assertThat(result.lat()).isEqualTo(12.5);
        assertThat(result.recordedAt()).isEqualTo(T);
    }

    @Test
    void latestIsNotFoundWhenNeitherHasIt() {
        when(cache.latest(1L)).thenReturn(Optional.empty());
        when(positions.findFirstByVehicleIdOrderByRecordedAtDesc(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.latest(1L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void historyIsReturnedOldestFirst() {
        when(vehicles.existsById(1L)).thenReturn(true);
        when(positions.findByVehicleIdAndRecordedAtBetweenOrderByRecordedAtDesc(
                        any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(stored(1L, 3, T.plusSeconds(20)), stored(1L, 2, T.plusSeconds(10)), stored(1L, 1, T)));

        List<LatestPosition> trail = service.history(1L, T.minusSeconds(60), T.plusSeconds(60), 10);

        assertThat(trail).extracting(LatestPosition::lat).containsExactly(1.0, 2.0, 3.0);
    }

    @Test
    void historyRejectsReversedRange() {
        assertThatThrownBy(() -> service.history(1L, T, T.minusSeconds(1), 10))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void historyRejectsBadLimits() {
        assertThatThrownBy(() -> service.history(1L, T.minusSeconds(60), T, 0)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.history(1L, T.minusSeconds(60), T, TrackingQueryService.MAX_HISTORY + 1))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void historyOfUnknownVehicleIsNotFound() {
        when(vehicles.existsById(9L)).thenReturn(false);

        assertThatThrownBy(() -> service.history(9L, T.minusSeconds(60), T, 10)).isInstanceOf(NotFoundException.class);
    }
}
