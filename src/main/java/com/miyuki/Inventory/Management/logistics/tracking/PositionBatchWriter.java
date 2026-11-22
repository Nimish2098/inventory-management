package com.miyuki.Inventory.Management.logistics.tracking;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Bulk insert with plain JDBC batching (JPA cannot batch IDENTITY inserts). One transaction per call, so a batch
 * is all-or-nothing. ON CONFLICT DO NOTHING makes redelivered stream entries harmless.
 */
@Component
public class PositionBatchWriter {

    private static final String SQL =
            """
            INSERT INTO vehicle_position (vehicle_id, lat, lng, speed_kmh, heading, recorded_at, received_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (vehicle_id, recorded_at) DO NOTHING
            """;
    private static final int JDBC_BATCH_SIZE = 500;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public PositionBatchWriter(JdbcTemplate jdbc, PlatformTransactionManager txManager) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txManager);
    }

    /**
     * @return number of rows actually inserted (duplicates are skipped)
     * @throws org.springframework.dao.DataIntegrityViolationException if any row violates a constraint
     *     (nothing is written in that case)
     */
    public int insertAll(List<PositionRow> rows) {
        if (rows.isEmpty()) {
            return 0;
        }
        Integer inserted = tx.execute(status -> {
            int[][] results = jdbc.batchUpdate(SQL, rows, JDBC_BATCH_SIZE, (ps, r) -> {
                ps.setLong(1, r.vehicleId());
                ps.setDouble(2, r.lat());
                ps.setDouble(3, r.lng());
                ps.setObject(4, r.speedKmh(), Types.DOUBLE);
                ps.setObject(5, r.heading(), Types.DOUBLE);
                ps.setObject(6, OffsetDateTime.ofInstant(r.recordedAt(), ZoneOffset.UTC));
                ps.setObject(7, OffsetDateTime.ofInstant(r.receivedAt(), ZoneOffset.UTC));
            });
            int count = 0;
            for (int[] chunk : results) {
                for (int c : chunk) {
                    if (c != 0) {
                        count++; // 1 = inserted, SUCCESS_NO_INFO (-2) = driver does not report, assume inserted
                    }
                }
            }
            return count;
        });
        return inserted == null ? 0 : inserted;
    }
}
