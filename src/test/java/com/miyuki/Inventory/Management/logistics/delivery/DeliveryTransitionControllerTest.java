package com.miyuki.Inventory.Management.logistics.delivery;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.miyuki.Inventory.Management.logistics.common.BadRequestException;
import com.miyuki.Inventory.Management.logistics.common.NotFoundException;
import com.miyuki.Inventory.Management.logistics.delivery.DeliveryDtos.DeliveryResponse;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DeliveryTransitionController.class)
@AutoConfigureMockMvc(addFilters = false)
class DeliveryTransitionControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean DeliveryTransitionService service;

    private static DeliveryResponse response(DeliveryStatus status) {
        return new DeliveryResponse(
                5L, 50L, status, 12.9, 77.5, "MG Road", null, null, BigDecimal.TEN, BigDecimal.ONE, null);
    }

    @Test
    void validTransitionReturns200AndPassesIdempotencyKey() throws Exception {
        when(service.transition(eq(5L), any(), anyString(), eq("abc-123")))
                .thenReturn(response(DeliveryStatus.PICKED_UP));

        mvc.perform(post("/api/deliveries/5/transitions")
                        .header("Idempotency-Key", "abc-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"PICK_UP\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PICKED_UP"));
    }

    @Test
    void headerIsOptional() throws Exception {
        when(service.transition(eq(5L), any(), anyString(), isNull())).thenReturn(response(DeliveryStatus.ASSIGNED));

        mvc.perform(post("/api/deliveries/5/transitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"ASSIGN\"}"))
                .andExpect(status().isOk());

        verify(service).transition(eq(5L), any(), anyString(), isNull());
    }

    @Test
    void missingActionReturns400() throws Exception {
        mvc.perform(post("/api/deliveries/5/transitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"because\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void unknownActionReturns400() throws Exception {
        mvc.perform(post("/api/deliveries/5/transitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"TELEPORT\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void tooLongReasonReturns400() throws Exception {
        String reason = "x".repeat(256);
        mvc.perform(post("/api/deliveries/5/transitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"FAIL\",\"reason\":\"" + reason + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingReasonRuleFromServiceReturns400() throws Exception {
        when(service.transition(eq(5L), any(), anyString(), isNull()))
                .thenThrow(new BadRequestException("A reason is required for FAIL"));

        mvc.perform(post("/api/deliveries/5/transitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"FAIL\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("A reason is required for FAIL"));
    }

    @Test
    void illegalTransitionReturns409() throws Exception {
        when(service.transition(eq(5L), any(), anyString(), isNull()))
                .thenThrow(new InvalidTransitionException(DeliveryStatus.DELIVERED, DeliveryAction.PICK_UP));

        mvc.perform(post("/api/deliveries/5/transitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"PICK_UP\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Cannot PICK_UP a delivery that is DELIVERED"));
    }

    @Test
    void concurrentModificationReturns409() throws Exception {
        when(service.transition(eq(5L), any(), anyString(), isNull()))
                .thenThrow(new ObjectOptimisticLockingFailureException(Delivery.class, 5L));

        mvc.perform(post("/api/deliveries/5/transitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"DELIVER\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void unknownDeliveryReturns404() throws Exception {
        when(service.transition(eq(404L), any(), anyString(), isNull()))
                .thenThrow(new NotFoundException("Delivery", 404L));

        mvc.perform(post("/api/deliveries/404/transitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"ASSIGN\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void allowedActionsReturnsTheList() throws Exception {
        when(service.allowedActions(5L)).thenReturn(Set.of(DeliveryAction.ASSIGN));

        mvc.perform(get("/api/deliveries/5/allowed-actions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("ASSIGN"));
    }

    @Test
    void allowedActionsOfMissingDeliveryReturns404() throws Exception {
        when(service.allowedActions(404L)).thenThrow(new NotFoundException("Delivery", 404L));

        mvc.perform(get("/api/deliveries/404/allowed-actions")).andExpect(status().isNotFound());
    }
}
