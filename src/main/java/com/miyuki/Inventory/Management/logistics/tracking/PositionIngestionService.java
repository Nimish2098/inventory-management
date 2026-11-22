package com.miyuki.Inventory.Management.logistics.tracking;

import com.miyuki.Inventory.Management.logistics.common.NotFoundException;
import com.miyuki.Inventory.Management.logistics.fleet.VehicleRepository;
import com.miyuki.Inventory.Management.logistics.tracking.TrackingDtos.IngestResult;
import com.miyuki.Inventory.Management.logistics.tracking.TrackingDtos.PositionBatchRequest;
import com.miyuki.Inventory.Management.logistics.tracking.TrackingDtos.PositionDto;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Request-side of the pipeline: cheap checks, then hand over to the stream and return. No database write here.
 * Structural problems (bad coordinates, empty batch) are rejected earlier by bean validation with a 400.
 * Timestamp problems only drop the affected pings so one stale ping does not discard an entire offline backlog.
 */
@Service
public class PositionIngestionService {

    private final VehicleRepository vehicles;
    private final PositionStreamProducer producer;
    private final TrackingProperties props;

    public PositionIngestionService(
            VehicleRepository vehicles, PositionStreamProducer producer, TrackingProperties props) {
        this.vehicles = vehicles;
        this.producer = producer;
        this.props = props;
    }

    public IngestResult ingest(PositionBatchRequest request) {
        return ingest(request, Instant.now());
    }

    /** Package-private so tests can control "now". */
    IngestResult ingest(PositionBatchRequest request, Instant now) {
        // A bad vehicle id must fail here: it would otherwise be a foreign-key violation in the writer.
        // Primary-key lookup, cheap; cache it later if profiling shows it matters.
        if (!vehicles.existsById(request.vehicleId())) {
            throw new NotFoundException("Vehicle", request.vehicleId());
        }

        Instant newest = now.plus(props.getMaxFutureSkew());
        Instant oldest = now.minus(props.getMaxAge());
        List<PositionDto> accepted = request.positions().stream()
                .filter(p -> !p.recordedAt().isAfter(newest) && !p.recordedAt().isBefore(oldest))
                .toList();

        if (!accepted.isEmpty()) {
            producer.publish(request.vehicleId(), accepted, now);
        }
        return new IngestResult(accepted.size(), request.positions().size() - accepted.size());
    }
}
