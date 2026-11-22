package com.miyuki.Inventory.Management.logistics.tracking;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Stream -> Postgres + cache.
 *
 * Delivery guarantees:
 *  - At-least-once: an entry is acknowledged only after its rows are committed. If the app crashes in between,
 *    the entry is re-read on restart (same consumer name) and the unique index drops the duplicates.
 *  - Poison entries (corrupt data, unknown vehicle, constraint violations) are moved to a dead-letter stream and
 *    acknowledged, so they can never block the pipeline.
 *  - Infrastructure failures (database down) are NOT dead-lettered: the entries stay pending and are retried.
 *
 * Run it on one instance, or give every instance its own tracking.consumer-name.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "tracking.consumer-enabled", havingValue = "true", matchIfMissing = true)
public class PositionStreamConsumer {

    private static final int MAX_LOOPS_PER_DRAIN = 1_000;

    private final StringRedisTemplate redis;
    private final StreamOperations<String, String, String> streams;
    private final PositionBatchWriter writer;
    private final PositionCache cache;
    private final TrackingProperties props;

    private boolean groupReady = false;
    private boolean recoveringPending = true; // first read our own unacknowledged entries (offset "0")

    public PositionStreamConsumer(
            StringRedisTemplate redis, PositionBatchWriter writer, PositionCache cache, TrackingProperties props) {
        this.redis = redis;
        this.streams = redis.opsForStream();
        this.writer = writer;
        this.cache = cache;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "${tracking.poll-interval-ms:200}")
    public void scheduledPoll() {
        try {
            drain();
        } catch (RuntimeException e) {
            // Redis or database trouble: entries stay pending and the next poll retries.
            log.error("Position stream poll failed, will retry: {}", e.toString());
        }
    }

    /** Reads until the stream is empty. @return number of stream entries handled */
    public synchronized int drain() {
        int total = 0;
        int loops = 0;
        int handled;
        do {
            handled = pollOnce();
            total += handled;
        } while (handled >= props.getBatchSize() && ++loops < MAX_LOOPS_PER_DRAIN);
        return total;
    }

    /** One read-process-acknowledge cycle. @return number of stream entries handled */
    public int pollOnce() {
        ensureGroup();
        List<MapRecord<String, String, String>> records;
        try {
            records = read();
            if (recoveringPending && (records == null || records.isEmpty())) {
                recoveringPending = false; // nothing left from a previous run: switch to new entries
                records = read();
            }
        } catch (RuntimeException e) {
            if (hasMessage(e, "NOGROUP")) {
                groupReady = false; // stream/group was deleted underneath us; recreate on the next poll
                return 0;
            }
            throw e;
        }
        if (records == null || records.isEmpty()) {
            return 0;
        }
        try {
            handle(records);
        } catch (RuntimeException e) {
            // Entries we read but could not finish stay pending for this consumer. Redis only hands pending
            // entries out again when asked from offset "0", so go back to recovery mode for the next poll.
            recoveringPending = true;
            throw e;
        }
        return records.size();
    }

    // ---------------------------------------------------------------- processing

    private void handle(List<MapRecord<String, String, String>> records) {
        List<PositionRow> rows = new ArrayList<>();
        List<RecordId> toAck = new ArrayList<>();
        List<RecordId> malformed = new ArrayList<>();

        for (MapRecord<String, String, String> record : records) {
            try {
                rows.addAll(PositionCodec.decode(record.getValue()));
                toAck.add(record.getId());
            } catch (RuntimeException e) {
                deadLetter(record.getId().getValue(), "malformed entry: " + e.getMessage(), record.getValue());
                malformed.add(record.getId());
            }
        }
        acknowledge(malformed);

        List<PositionRow> persisted = persist(rows); // throws on infrastructure errors -> nothing below runs
        updateCache(persisted);
        acknowledge(toAck);
        streams.trim(props.getStreamKey(), props.getStreamMaxLength(), true);
    }

    /** Whole batch first (fast path). If a constraint rejects it, isolate the bad rows one by one. */
    private List<PositionRow> persist(List<PositionRow> rows) {
        if (rows.isEmpty()) {
            return rows;
        }
        try {
            writer.insertAll(rows);
            return rows;
        } catch (DataIntegrityViolationException batchFailure) {
            log.warn("Batch insert rejected ({}), retrying row by row", batchFailure.getMostSpecificCause().getMessage());
        }
        List<PositionRow> good = new ArrayList<>();
        for (PositionRow row : rows) {
            try {
                writer.insertAll(List.of(row));
                good.add(row);
            } catch (DataIntegrityViolationException e) {
                deadLetter("row", e.getMostSpecificCause().getMessage(), describe(row));
            }
        }
        return good;
    }

    private void updateCache(List<PositionRow> persisted) {
        Map<Long, PositionRow> newest = new HashMap<>();
        for (PositionRow r : persisted) {
            newest.merge(r.vehicleId(), r, (a, b) -> b.recordedAt().isAfter(a.recordedAt()) ? b : a);
        }
        for (PositionRow r : newest.values()) {
            try {
                cache.update(r);
            } catch (RuntimeException e) {
                // The cache is derived data. The row is safely in Postgres, so never fail the batch for it.
                log.warn("Could not update latest-position cache for vehicle {}: {}", r.vehicleId(), e.toString());
            }
        }
    }

    // ---------------------------------------------------------------- redis plumbing

    private List<MapRecord<String, String, String>> read() {
        ReadOffset offset = recoveringPending ? ReadOffset.from("0") : ReadOffset.lastConsumed();
        return streams.read(
                Consumer.from(props.getGroup(), props.getConsumerName()),
                StreamReadOptions.empty().count(props.getBatchSize()),
                StreamOffset.create(props.getStreamKey(), offset));
    }

    private void acknowledge(List<RecordId> ids) {
        if (!ids.isEmpty()) {
            streams.acknowledge(props.getStreamKey(), props.getGroup(), ids.toArray(new RecordId[0]));
        }
    }

    /** XGROUP CREATE ... MKSTREAM, starting at the beginning so nothing published before the group is lost. */
    void ensureGroup() {
        if (groupReady) {
            return;
        }
        try {
            redis.execute((RedisCallback<String>) connection -> connection
                    .streamCommands()
                    .xGroupCreate(
                            props.getStreamKey().getBytes(StandardCharsets.UTF_8),
                            props.getGroup(),
                            ReadOffset.from("0"),
                            true));
        } catch (RuntimeException e) {
            if (!hasMessage(e, "BUSYGROUP")) { // BUSYGROUP = already exists, which is fine
                throw e;
            }
        }
        groupReady = true;
    }

    private void deadLetter(String sourceId, String reason, Map<String, String> payload) {
        Map<String, String> fields = new LinkedHashMap<>(payload);
        fields.put("sourceId", sourceId);
        fields.put("reason", abbreviate(reason, 500));
        fields.put("failedAt", String.valueOf(System.currentTimeMillis()));
        streams.add(StreamRecords.newRecord().in(props.getDeadLetterKey()).ofMap(fields));
        streams.trim(props.getDeadLetterKey(), props.getDeadLetterMaxLength(), true);
        log.warn("Dead-lettered position data ({}): {}", sourceId, abbreviate(reason, 200));
    }

    private static Map<String, String> describe(PositionRow r) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("vehicleId", String.valueOf(r.vehicleId()));
        m.put("recordedAt", String.valueOf(r.recordedAt().toEpochMilli()));
        m.put("lat", String.valueOf(r.lat()));
        m.put("lng", String.valueOf(r.lng()));
        return m;
    }

    private static boolean hasMessage(Throwable t, String needle) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c.getMessage() != null && c.getMessage().contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String abbreviate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
