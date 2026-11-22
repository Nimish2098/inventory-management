package com.miyuki.Inventory.Management.logistics.tracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.miyuki.Inventory.Management.logistics.common.NotFoundException;
import com.miyuki.Inventory.Management.logistics.fleet.VehicleRepository;
import com.miyuki.Inventory.Management.logistics.tracking.TrackingDtos.IngestResult;
import com.miyuki.Inventory.Management.logistics.tracking.TrackingDtos.PositionBatchRequest;
import com.miyuki.Inventory.Management.logistics.tracking.TrackingDtos.PositionDto;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PositionIngestionServiceTest {

    static final Instant NOW = Instant.parse("2026-11-21T10:00:00Z");

    @Mock VehicleRepository vehicles;
    @Mock PositionStreamProducer producer;

    TrackingProperties props = new TrackingProperties();
    PositionIngestionService service;

    @BeforeEach
    void setUp() {
        service = new PositionIngestionService(vehicles, producer, props);
    }

    private static PositionDto ping(Instant at) {
        return new PositionDto(new BigDecimal("12.97"), new BigDecimal("77.59"), new BigDecimal("30"), new BigDecimal("90"), at);
    }

    private static PositionBatchRequest batch(PositionDto... pings) {
        return new PositionBatchRequest(1L, List.of(pings));
    }

    @SuppressWarnings("unchecked")
    private List<PositionDto> published() {
        ArgumentCaptor<List<PositionDto>> captor = ArgumentCaptor.forClass(List.class);
        verify(producer).publish(eq(1L), captor.capture(), eq(NOW));
        return captor.getValue();
    }

    @Test
    void freshPingsAreAcceptedAndPublishedOnce() {
        when(vehicles.existsById(1L)).thenReturn(true);
        PositionDto a = ping(NOW.minusSeconds(10));
        PositionDto b = ping(NOW.minusSeconds(5));

        IngestResult result = service.ingest(batch(a, b), NOW);

        assertThat(result).isEqualTo(new IngestResult(2, 0));
        assertThat(published()).containsExactly(a, b);
    }

    @Test
    void unknownVehicleIsNotFoundAndNothingIsPublished() {
        when(vehicles.existsById(1L)).thenReturn(false);

        assertThatThrownBy(() -> service.ingest(batch(ping(NOW)), NOW)).isInstanceOf(NotFoundException.class);

        verifyNoInteractions(producer);
    }

    @Test
    void pingsOlderThanMaxAgeAreRejected() {
        when(vehicles.existsById(1L)).thenReturn(true);

        IngestResult result = service.ingest(batch(ping(NOW.minus(Duration.ofHours(25)))), NOW);

        assertThat(result).isEqualTo(new IngestResult(0, 1));
        verify(producer, never()).publish(any(), any(), any());
    }

    @Test
    void pingsTooFarInTheFutureAreRejected() {
        when(vehicles.existsById(1L)).thenReturn(true);

        IngestResult result = service.ingest(batch(ping(NOW.plus(Duration.ofMinutes(6)))), NOW);

        assertThat(result).isEqualTo(new IngestResult(0, 1));
        verify(producer, never()).publish(any(), any(), any());
    }

    @Test
    void boundariesAreInclusive() {
        when(vehicles.existsById(1L)).thenReturn(true);
        PositionDto oldest = ping(NOW.minus(props.getMaxAge()));
        PositionDto newest = ping(NOW.plus(props.getMaxFutureSkew()));

        IngestResult result = service.ingest(batch(oldest, newest), NOW);

        assertThat(result).isEqualTo(new IngestResult(2, 0));
    }

    @Test
    void mixedBatchKeepsOnlyTheGoodPings() {
        when(vehicles.existsById(1L)).thenReturn(true);
        PositionDto good = ping(NOW.minusSeconds(3));

        IngestResult result = service.ingest(
                batch(ping(NOW.minus(Duration.ofDays(3))), good, ping(NOW.plus(Duration.ofHours(1)))), NOW);

        assertThat(result).isEqualTo(new IngestResult(1, 2));
        assertThat(published()).containsExactly(good);
    }

    @Test
    void limitsFollowConfiguration() {
        when(vehicles.existsById(1L)).thenReturn(true);
        props.setMaxAge(Duration.ofMinutes(1));

        IngestResult result = service.ingest(batch(ping(NOW.minusSeconds(120))), NOW);

        assertThat(result).isEqualTo(new IngestResult(0, 1));
    }
}
