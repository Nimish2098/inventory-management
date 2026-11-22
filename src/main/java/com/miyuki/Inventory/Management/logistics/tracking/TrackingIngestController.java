package com.miyuki.Inventory.Management.logistics.tracking;

import com.miyuki.Inventory.Management.logistics.tracking.TrackingDtos.IngestResult;
import com.miyuki.Inventory.Management.logistics.tracking.TrackingDtos.PositionBatchRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tracking")
@RequiredArgsConstructor
public class TrackingIngestController {

    private final PositionIngestionService ingestion;

    /** 202: accepted for processing (it is in the stream, not yet necessarily in the database). */
    @PostMapping("/positions")
    public ResponseEntity<IngestResult> ingest(@Valid @RequestBody PositionBatchRequest request) {
        return ResponseEntity.accepted().body(ingestion.ingest(request));
    }
}
