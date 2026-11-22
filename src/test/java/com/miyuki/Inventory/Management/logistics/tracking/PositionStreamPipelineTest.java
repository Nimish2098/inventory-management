package com.miyuki.Inventory.Management.logistics.tracking;

import static com.miyuki.Inventory.Management.logistics.TestData.vehicle;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.miyuki.Inventory.Management.logistics.AbstractRepositoryTest;
import com.miyuki.Inventory.Management.logistics.fleet.VehicleRepository;
import com.miyuki.Inventory.Management.logistics.tracking.TrackingDtos.LatestPosition;
import com.miyuki.Inventory.Management.logistics.tracking.TrackingDtos.PositionDto;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.mockito.Mockito;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * End-to-end test of the pipeline without the web layer: producer -> Redis Stream -> consumer -> Postgres + cache.
 * Needs Docker for both Postgres and Redis. Not transactional on purpose: the consumer commits its own transactions,
 * and failed batches must be able to roll back for real.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PositionStreamPipelineTest extends AbstractRepositoryTest {

    static final Instant T0 = Instant.parse("2026-11-21T10:00:00Z");

    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager txManager;
    @Autowired VehicleRepository vehicles;

    StringRedisTemplate redis;
    StreamOperations<String, String, String> streams;
    JdbcTemplate jdbc;
    TrackingProperties props;
    PositionStreamProducer producer;
    PositionBatchWriter writer;
    PositionCache cache;
    PositionStreamConsumer consumer;
    Long vehicleId;

    @BeforeEach
    void setUp() {
        redis = RedisTestSupport.template();
        RedisTestSupport.flush(redis);
        streams = redis.opsForStream();

        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute(
                "TRUNCATE TABLE vehicle_position, route_stop, route, delivery_event, delivery, vehicle, driver RESTART IDENTITY CASCADE");
        vehicleId = vehicles.saveAndFlush(vehicle("KA01PL0001")).getId();

        props = new TrackingProperties();
        props.setConsumerName("test-consumer");
        producer = new PositionStreamProducer(redis, props);
        writer = new PositionBatchWriter(jdbc, txManager);
        cache = new PositionCache(redis, props);
        consumer = new PositionStreamConsumer(redis, writer, cache, props);
    }

    private static PositionDto ping(double lat, Instant at) {
        return new PositionDto(BigDecimal.valueOf(lat), new BigDecimal("77.59"), new BigDecimal("30"), new BigDecimal("90"), at);
    }

    private int rows() {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM vehicle_position", Integer.class);
        return n == null ? 0 : n;
    }

    private long deadLetters() {
        Long n = streams.size(props.getDeadLetterKey());
        return n == null ? 0 : n;
    }

    @Test
    void positionsFlowFromTheStreamIntoPostgresAndTheCache() {
        producer.publish(vehicleId, List.of(ping(12.90, T0), ping(12.91, T0.plusSeconds(5)), ping(12.92, T0.plusSeconds(10))), T0);

        int handled = consumer.drain();

        assertThat(handled).isEqualTo(1); // one HTTP batch = one stream entry
        assertThat(rows()).isEqualTo(3);
        LatestPosition latest = cache.latest(vehicleId).orElseThrow();
        assertThat(latest.lat()).isEqualTo(12.92);
        assertThat(latest.recordedAt()).isEqualTo(T0.plusSeconds(10));
        assertThat(deadLetters()).isZero();
    }

    @Test
    void acknowledgedEntriesAreNotProcessedAgain() {
        producer.publish(vehicleId, List.of(ping(12.90, T0)), T0);
        consumer.drain();

        assertThat(consumer.drain()).isZero();
        assertThat(rows()).isEqualTo(1);
    }

    @Test
    void redeliveredBatchDoesNotCreateDuplicates() {
        producer.publish(vehicleId, List.of(ping(12.90, T0), ping(12.91, T0.plusSeconds(5))), T0);
        producer.publish(vehicleId, List.of(ping(12.90, T0), ping(12.91, T0.plusSeconds(5))), T0); // same data twice
        consumer.drain();

        assertThat(rows()).isEqualTo(2);
    }

    @Test
    void latePingIsStoredButDoesNotOverwriteTheLatestPosition() {
        producer.publish(vehicleId, List.of(ping(12.95, T0.plusSeconds(60))), T0);
        producer.publish(vehicleId, List.of(ping(12.10, T0)), T0); // offline backlog arrives afterwards
        consumer.drain();

        assertThat(rows()).isEqualTo(2);
        LatestPosition latest = cache.latest(vehicleId).orElseThrow();
        assertThat(latest.lat()).isEqualTo(12.95);
        assertThat(latest.recordedAt()).isEqualTo(T0.plusSeconds(60));
    }

    @Test
    void corruptEntryIsDeadLetteredAndDoesNotBlockValidOnes() {
        streams.add(StreamRecords.newRecord()
                .in(props.getStreamKey())
                .ofMap(Map.of("vehicleId", "abc", "receivedAt", "1", "data", "garbage")));
        producer.publish(vehicleId, List.of(ping(12.90, T0)), T0);

        consumer.drain();

        assertThat(rows()).isEqualTo(1);
        assertThat(deadLetters()).isEqualTo(1);
        assertThat(consumer.drain()).isZero(); // nothing stuck in the pending list
    }

    @Test
    void unknownVehicleIsDeadLetteredWhileTheRestOfTheBatchIsStored() {
        producer.publish(999_999L, List.of(ping(12.50, T0)), T0); // foreign-key violation
        producer.publish(vehicleId, List.of(ping(12.90, T0)), T0);

        consumer.drain();

        assertThat(rows()).isEqualTo(1);
        assertThat(deadLetters()).isEqualTo(1);
        assertThat(cache.latest(vehicleId)).isPresent();
        assertThat(cache.latest(999_999L)).isEmpty(); // never cache rows that were not persisted
        assertThat(consumer.drain()).isZero();
    }

    @Test
    void rowsOfAMixedEntryAreSplitIntoStoredAndDeadLettered() {
        // one entry for the real vehicle plus one for a ghost vehicle land in the same poll -> row-by-row fallback
        producer.publish(vehicleId, List.of(ping(12.90, T0), ping(12.91, T0.plusSeconds(5))), T0);
        producer.publish(999_999L, List.of(ping(12.50, T0)), T0);

        consumer.drain();

        assertThat(rows()).isEqualTo(2);
        assertThat(deadLetters()).isEqualTo(1);
    }

    @Test
    void entriesReadButNeverAcknowledgedAreRecoveredAfterARestart() {
        consumer.pollOnce(); // creates the consumer group on the empty stream
        producer.publish(vehicleId, List.of(ping(12.90, T0)), T0);

        // simulate a worker that read the entry and crashed before committing/acknowledging
        streams.read(
                Consumer.from(props.getGroup(), props.getConsumerName()),
                StreamReadOptions.empty().count(10),
                StreamOffset.create(props.getStreamKey(), ReadOffset.lastConsumed()));
        assertThat(rows()).isZero();

        PositionStreamConsumer restarted = new PositionStreamConsumer(redis, writer, cache, props);
        restarted.drain();

        assertThat(rows()).isEqualTo(1);
        assertThat(restarted.drain()).isZero();
    }

    @Test
    void drainKeepsReadingUntilTheStreamIsEmpty() {
        props.setBatchSize(2);
        for (int i = 0; i < 5; i++) {
            producer.publish(vehicleId, List.of(ping(12.90 + i / 100.0, T0.plusSeconds(i))), T0);
        }

        int handled = consumer.drain();

        assertThat(handled).isEqualTo(5);
        assertThat(rows()).isEqualTo(5);
    }

    @Test
    void consumerWorksWhenTheStreamDoesNotExistYet() {
        assertThat(consumer.drain()).isZero();
        assertThat(consumer.drain()).isZero();
    }

    @Test
    void entriesStayPendingWhenTheDatabaseIsDownAndAreRetriedOnTheNextPoll() {
        PositionBatchWriter flaky = Mockito.spy(writer);
        Mockito.doThrow(new DataAccessResourceFailureException("database is down"))
                .doCallRealMethod()
                .when(flaky)
                .insertAll(Mockito.anyList());
        PositionStreamConsumer c = new PositionStreamConsumer(redis, flaky, cache, props);
        producer.publish(vehicleId, List.of(ping(12.90, T0)), T0);

        assertThatThrownBy(c::drain).isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(rows()).isZero();
        assertThat(deadLetters()).isZero(); // an outage is not poison data

        c.drain(); // database is back: the same entry is delivered again and committed

        assertThat(rows()).isEqualTo(1);
        assertThat(deadLetters()).isZero();
        assertThat(c.drain()).isZero();
    }
}
