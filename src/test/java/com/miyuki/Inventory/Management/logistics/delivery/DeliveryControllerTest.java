package com.miyuki.Inventory.Management.logistics.delivery;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.miyuki.Inventory.Management.logistics.common.ConflictException;
import com.miyuki.Inventory.Management.logistics.common.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DeliveryController.class)
@AutoConfigureMockMvc(addFilters = false)
class DeliveryControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean DeliveryService service;

    private static String body(String lat, String lng, String extra) {
        return "{\"orderId\":1,\"destinationLat\":" + lat + ",\"destinationLng\":" + lng
                + ",\"weightKg\":10,\"volumeM3\":1" + extra + "}";
    }

    @Test
    void missingDestinationReturns400() throws Exception {
        mvc.perform(post("/api/deliveries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":1,\"weightKg\":10,\"volumeM3\":1}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void latitudeOutOfRangeReturns400() throws Exception {
        mvc.perform(post("/api/deliveries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("91.5", "77.5", "")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void longitudeOutOfRangeReturns400() throws Exception {
        mvc.perform(post("/api/deliveries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("12.9", "-181", "")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void negativeWeightReturns400() throws Exception {
        mvc.perform(post("/api/deliveries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":1,\"destinationLat\":12.9,\"destinationLng\":77.5,\"weightKg\":-1,\"volumeM3\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void windowEndBeforeStartReturns400() throws Exception {
        String window = ",\"windowStart\":\"2026-11-20T10:00:00Z\",\"windowEnd\":\"2026-11-20T09:00:00Z\"";
        mvc.perform(post("/api/deliveries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("12.9", "77.5", window)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void getMissingDeliveryReturns404() throws Exception {
        when(service.get(404L)).thenThrow(new NotFoundException("Delivery", 404L));
        mvc.perform(get("/api/deliveries/404")).andExpect(status().isNotFound());
    }

    @Test
    void eventsOfMissingDeliveryReturns404() throws Exception {
        when(service.events(404L)).thenThrow(new NotFoundException("Delivery", 404L));
        mvc.perform(get("/api/deliveries/404/events")).andExpect(status().isNotFound());
    }

    @Test
    void deletingNonCreatedDeliveryReturns409() throws Exception {
        org.mockito.Mockito.doThrow(new ConflictException("already IN_TRANSIT"))
                .when(service)
                .delete(3L);
        mvc.perform(delete("/api/deliveries/3")).andExpect(status().isConflict());
    }
}
