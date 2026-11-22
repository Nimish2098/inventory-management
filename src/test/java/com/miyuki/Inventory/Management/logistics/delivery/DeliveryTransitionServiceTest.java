package com.miyuki.Inventory.Management.logistics.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.miyuki.Inventory.Management.logistics.common.BadRequestException;
import com.miyuki.Inventory.Management.logistics.common.ConflictException;
import com.miyuki.Inventory.Management.logistics.common.NotFoundException;
import com.miyuki.Inventory.Management.logistics.delivery.DeliveryDtos.DeliveryResponse;
import com.miyuki.Inventory.Management.logistics.delivery.DeliveryTransitionDtos.TransitionRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeliveryTransitionServiceTest {

    @Mock DeliveryRepository deliveries;
    @Mock DeliveryEventRepository events;
    @Mock StockHook stock;

    DeliveryTransitionService service;

    @BeforeEach
    void setUp() {
        service = new DeliveryTransitionService(deliveries, events, List.of(stock));
    }

    private Delivery delivery(long id, DeliveryStatus status) {
        Delivery d = new Delivery();
        d.setId(id);
        d.setOrderId(id * 10);
        d.setStatus(status);
        d.setDestinationLat(12.97);
        d.setDestinationLng(77.59);
        d.setWeightKg(new BigDecimal("5"));
        d.setVolumeM3(new BigDecimal("1"));
        return d;
    }

    private Delivery found(long id, DeliveryStatus status) {
        Delivery d = delivery(id, status);
        when(deliveries.findById(id)).thenReturn(Optional.of(d));
        return d;
    }

    private DeliveryEvent savedEvent() {
        ArgumentCaptor<DeliveryEvent> captor = ArgumentCaptor.forClass(DeliveryEvent.class);
        verify(events).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void pickUpMovesToPickedUpDeductsStockAndRecordsEvent() {
        Delivery d = found(1L, DeliveryStatus.ASSIGNED);

        DeliveryResponse response = service.transition(1L, new TransitionRequest(DeliveryAction.PICK_UP, null), "driver-7", null);

        assertThat(response.status()).isEqualTo(DeliveryStatus.PICKED_UP);
        verify(stock).deductFor(d);
        verify(stock, never()).restoreFor(any());
        DeliveryEvent event = savedEvent();
        assertThat(event.getFromStatus()).isEqualTo(DeliveryStatus.ASSIGNED);
        assertThat(event.getToStatus()).isEqualTo(DeliveryStatus.PICKED_UP);
        assertThat(event.getAction()).isEqualTo(DeliveryAction.PICK_UP);
        assertThat(event.getActor()).isEqualTo("driver-7");
        verify(deliveries).saveAndFlush(d);
    }

    @Test
    void returnRestoresStock() {
        Delivery d = found(1L, DeliveryStatus.FAILED);

        DeliveryResponse response = service.transition(1L, new TransitionRequest(DeliveryAction.RETURN, null), "dispatcher", null);

        assertThat(response.status()).isEqualTo(DeliveryStatus.RETURNED);
        verify(stock).restoreFor(d);
        verify(stock, never()).deductFor(any());
    }

    @Test
    void actionsThatDoNotMoveGoodsLeaveStockAlone() {
        found(1L, DeliveryStatus.ARRIVED);

        service.transition(1L, new TransitionRequest(DeliveryAction.DELIVER, null), "driver-7", null);

        verifyNoInteractions(stock);
    }

    @Test
    void invalidTransitionIsRejectedAndNothingIsChanged() {
        Delivery d = found(1L, DeliveryStatus.DELIVERED);

        assertThatThrownBy(() -> service.transition(
                        1L, new TransitionRequest(DeliveryAction.CANCEL, "customer changed mind"), "dispatcher", null))
                .isInstanceOf(InvalidTransitionException.class);

        assertThat(d.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        verify(events, never()).save(any());
        verify(deliveries, never()).saveAndFlush(any());
        verifyNoInteractions(stock);
    }

    @Test
    void failRequiresAReason() {
        Delivery d = found(1L, DeliveryStatus.IN_TRANSIT);

        assertThatThrownBy(() -> service.transition(1L, new TransitionRequest(DeliveryAction.FAIL, "  "), "driver-7", null))
                .isInstanceOf(BadRequestException.class);

        assertThat(d.getStatus()).isEqualTo(DeliveryStatus.IN_TRANSIT);
        verify(events, never()).save(any());
    }

    @Test
    void cancelRequiresAReason() {
        found(1L, DeliveryStatus.CREATED);

        assertThatThrownBy(() -> service.transition(1L, new TransitionRequest(DeliveryAction.CANCEL, null), "dispatcher", null))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void failWithReasonIsRecorded() {
        found(1L, DeliveryStatus.ARRIVED);

        DeliveryResponse response =
                service.transition(1L, new TransitionRequest(DeliveryAction.FAIL, "customer not home"), "driver-7", null);

        assertThat(response.status()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(savedEvent().getReason()).isEqualTo("customer not home");
    }

    @Test
    void retriedRequestWithSameIdempotencyKeyChangesNothing() {
        Delivery d = found(1L, DeliveryStatus.PICKED_UP);
        when(events.existsByDeliveryIdAndIdempotencyKey(1L, "tap-1")).thenReturn(true);

        DeliveryResponse response =
                service.transition(1L, new TransitionRequest(DeliveryAction.PICK_UP, null), "driver-7", "tap-1");

        assertThat(response.status()).isEqualTo(DeliveryStatus.PICKED_UP);
        assertThat(d.getStatus()).isEqualTo(DeliveryStatus.PICKED_UP);
        verify(events, never()).save(any());
        verify(deliveries, never()).saveAndFlush(any());
        verifyNoInteractions(stock);
    }

    @Test
    void firstRequestStoresTheIdempotencyKeyOnTheEvent() {
        found(1L, DeliveryStatus.ASSIGNED);
        when(events.existsByDeliveryIdAndIdempotencyKey(1L, "tap-1")).thenReturn(false);

        service.transition(1L, new TransitionRequest(DeliveryAction.PICK_UP, null), "driver-7", "  tap-1  ");

        assertThat(savedEvent().getIdempotencyKey()).isEqualTo("tap-1");
    }

    @Test
    void overlongIdempotencyKeyIsRejected() {
        found(1L, DeliveryStatus.ASSIGNED);

        assertThatThrownBy(() -> service.transition(
                        1L, new TransitionRequest(DeliveryAction.PICK_UP, null), "driver-7", "x".repeat(101)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void stockFailureLeavesDeliveryUntouched() {
        Delivery d = found(1L, DeliveryStatus.ASSIGNED);
        doThrow(new ConflictException("Insufficient stock")).when(stock).deductFor(d);

        assertThatThrownBy(() -> service.transition(1L, new TransitionRequest(DeliveryAction.PICK_UP, null), "driver-7", null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Insufficient stock");

        assertThat(d.getStatus()).isEqualTo(DeliveryStatus.ASSIGNED);
        verify(events, never()).save(any());
        verify(deliveries, never()).saveAndFlush(any());
    }

    @Test
    void worksWithoutAnyStockHookConfigured() {
        service = new DeliveryTransitionService(deliveries, events, List.of());
        found(1L, DeliveryStatus.ASSIGNED);

        DeliveryResponse response = service.transition(1L, new TransitionRequest(DeliveryAction.PICK_UP, null), "driver-7", null);

        assertThat(response.status()).isEqualTo(DeliveryStatus.PICKED_UP);
    }

    @Test
    void unknownDeliveryIsNotFound() {
        when(deliveries.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.transition(99L, new TransitionRequest(DeliveryAction.ASSIGN, null), "x", null))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void allowedActionsFollowTheCurrentStatus() {
        found(1L, DeliveryStatus.CREATED);
        found(2L, DeliveryStatus.DELIVERED);

        assertThat(service.allowedActions(1L)).containsExactly(DeliveryAction.ASSIGN, DeliveryAction.CANCEL);
        assertThat(service.allowedActions(2L)).isEmpty();
    }

    @Test
    void applyUsedByRoutesAssignsAndAudits() {
        Delivery d = delivery(5L, DeliveryStatus.CREATED);

        service.apply(d, DeliveryAction.ASSIGN, "dispatcher", "assigned to route 3", null);

        assertThat(d.getStatus()).isEqualTo(DeliveryStatus.ASSIGNED);
        assertThat(savedEvent().getAction()).isEqualTo(DeliveryAction.ASSIGN);
        verifyNoInteractions(stock);
    }
}
