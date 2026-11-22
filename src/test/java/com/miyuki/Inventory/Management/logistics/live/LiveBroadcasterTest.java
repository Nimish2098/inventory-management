package com.miyuki.Inventory.Management.logistics.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.miyuki.Inventory.Management.logistics.tracking.TrackingDtos.LatestPosition;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessageSendingOperations;

@ExtendWith(MockitoExtension.class)
class LiveBroadcasterTest {

    static final Instant T0 = Instant.parse("2026-11-22T10:00:00Z");

    @Mock SimpMessageSendingOperations messaging;
    LiveBuffer buffer;
    LiveBroadcaster broadcaster;

    @BeforeEach
    void setUp() {
        buffer = new LiveBuffer();
        broadcaster = new LiveBroadcaster(buffer, messaging);
    }

    private static LatestPosition pos(long vehicleId) {
        return new LatestPosition(vehicleId, 12.9, 77.5, 30.0, 90.0, T0);
    }

    @Test
    void nothingIsSentWhenNothingMoved() {
        broadcaster.flush();

        verifyNoInteractions(messaging);
    }

    @Test
    void oneMessageCarriesEveryVehicleThatMoved() {
        buffer.offer(pos(2));
        buffer.offer(pos(1));
        buffer.offer(pos(3));

        broadcaster.flush();

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messaging, times(1)).convertAndSend(eq(LiveBroadcaster.TOPIC), payload.capture());
        @SuppressWarnings("unchecked")
        List<LatestPosition> sent = (List<LatestPosition>) payload.getValue();
        assertThat(sent).extracting(LatestPosition::vehicleId).containsExactly(1L, 2L, 3L);
    }

    @Test
    void theSamePositionsAreNotSentTwice() {
        buffer.offer(pos(1));

        broadcaster.flush();
        broadcaster.flush();

        verify(messaging, times(1)).convertAndSend(eq(LiveBroadcaster.TOPIC), any(Object.class));
    }

    @Test
    void aFailedSendIsLoggedNotThrown() {
        buffer.offer(pos(1));
        doThrow(new MessageDeliveryException("channel full"))
                .when(messaging)
                .convertAndSend(anyString(), any(Object.class));

        assertThatCode(() -> broadcaster.flush()).doesNotThrowAnyException();
        assertThat(buffer.size()).isZero(); // dropped on purpose: the next ping replaces it
    }

    @Test
    void topicIsTheOneTheInterceptorAllows() {
        assertThat(StompJwtAuthInterceptor.ALLOWED_DESTINATIONS).containsExactly(LiveBroadcaster.TOPIC);
    }
}
