package com.miyuki.Inventory.Management.logistics.tracking;

import static com.miyuki.Inventory.Management.logistics.TestData.vehicle;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.miyuki.Inventory.Management.logistics.AbstractRepositoryTest;
import com.miyuki.Inventory.Management.logistics.fleet.VehicleRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** Needs Docker (Postgres via AbstractRepositoryTest, real Flyway migrations including V4). */
class PositionBatchWriterTest extends AbstractRepositoryTest {

    static final Instant T0 = Instant.parse("2026-11-21T10:00:00Z");

    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager txManager;
    @Autowired VehicleRepository vehicles;

    JdbcTemplate jdbc;
    PositionBatchWriter writer;
    Long vehicleId;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        writer = new PositionBatchWriter(jdbc, txManager);
        vehicleId = vehicles.saveAndFlush(vehicle("KA01BW0001")).getId();
    }

    private PositionRow row(long vehicle, int secondsAfterT0) {
        return new PositionRow(vehicle, 12.97, 77.59, 30.0, 90.0, T0.plusSeconds(secondsAfterT0), T0);
    }

    private int count() {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM vehicle_position", Integer.class);
        return n == null ? 0 : n;
    }

    @Test
    void insertsAllRows() {
        int inserted = writer.insertAll(List.of(row(vehicleId, 0), row(vehicleId, 5), row(vehicleId, 10)));

        assertThat(inserted).isEqualTo(3);
        assertThat(count()).isEqualTo(3);
    }

    @Test
    void emptyBatchDoesNothing() {
        assertThat(writer.insertAll(List.of())).isZero();
        assertThat(count()).isZero();
    }

    @Test
    void redeliveredRowsAreSkippedInsteadOfDuplicated() {
        writer.insertAll(List.of(row(vehicleId, 0), row(vehicleId, 5)));

        int second = writer.insertAll(List.of(row(vehicleId, 0), row(vehicleId, 5), row(vehicleId, 10)));

        assertThat(second).isEqualTo(1);
        assertThat(count()).isEqualTo(3);
    }

    @Test
    void largeBatchIsInserted() {
        List<PositionRow> rows = new ArrayList<>();
        for (int i = 0; i < 1_200; i++) {
            rows.add(row(vehicleId, i));
        }

        assertThat(writer.insertAll(rows)).isEqualTo(1_200);
        assertThat(count()).isEqualTo(1_200);
    }

    @Test
    void nullSpeedAndHeadingAreStoredAsNull() {
        writer.insertAll(List.of(new PositionRow(vehicleId, 12.9, 77.5, null, null, T0, T0)));

        Integer nulls = jdbc.queryForObject(
                "SELECT count(*) FROM vehicle_position WHERE speed_kmh IS NULL AND heading IS NULL", Integer.class);
        assertThat(nulls).isEqualTo(1);
    }

    @Test
    void timestampsKeepTheirInstant() {
        writer.insertAll(List.of(row(vehicleId, 0)));

        Long millis = jdbc.queryForObject(
                "SELECT (extract(epoch FROM recorded_at) * 1000)::bigint FROM vehicle_position", Long.class);
        assertThat(millis).isEqualTo(T0.toEpochMilli());
    }

    @Test
    void unknownVehicleViolatesTheForeignKey() {
        assertThatThrownBy(() -> writer.insertAll(List.of(row(999_999L, 0))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void outOfRangeCoordinateViolatesTheCheckConstraint() {
        PositionRow bad = new PositionRow(vehicleId, 95.0, 77.5, null, null, T0, T0);

        assertThatThrownBy(() -> writer.insertAll(List.of(bad))).isInstanceOf(DataIntegrityViolationException.class);
    }
}
