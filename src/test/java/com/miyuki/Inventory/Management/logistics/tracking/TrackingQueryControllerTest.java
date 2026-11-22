package com.miyuki.Inventory.Management.logistics.tracking;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.miyuki.Inventory.Management.logistics.common.BadRequestException;
import com.miyuki.Inventory.Management.logistics.common.NotFoundException;
import com.miyuki.Inventory.Management.logistics.tracking.TrackingDtos.LatestPosition;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TrackingQueryController.class)
@AutoConfigureMockMvc(addFilters = false)
class TrackingQueryControllerTest {

    static final Instant T = Instant.parse("2026-11-21T10:00:00Z");

    @Autowired MockMvc mvc;
    @MockitoBean TrackingQueryService service;

    @Test
    void liveReturnsAllVehicles() throws Exception {
        when(service.live())
                .thenReturn(List.of(
                        new LatestPosition(1L, 12.9, 77.5, 30.0, 90.0, T), new LatestPosition(2L, 13.0, 77.6, null, null, T)));

        mvc.perform(get("/api/tracking/vehicles/live"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].vehicleId").value(1))
                .andExpect(jsonPath("$[1].speedKmh").doesNotExist());
    }

    @Test
    void latestReturnsThePosition() throws Exception {
        when(service.latest(1L)).thenReturn(new LatestPosition(1L, 12.9, 77.5, 30.0, 90.0, T));

        mvc.perform(get("/api/tracking/vehicles/1/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lat").value(12.9));
    }

    @Test
    void latestOfVehicleWithoutPingsReturns404() throws Exception {
        when(service.latest(5L)).thenThrow(new NotFoundException("Position of vehicle", 5L));

        mvc.perform(get("/api/tracking/vehicles/5/latest")).andExpect(status().isNotFound());
    }

    @Test
    void historyPassesParametersAndDefaultLimit() throws Exception {
        when(service.history(eq(1L), eq(T), eq(T.plusSeconds(60)), eq(500))).thenReturn(List.of());

        mvc.perform(get("/api/tracking/vehicles/1/history")
                        .param("from", "2026-11-21T10:00:00Z")
                        .param("to", "2026-11-21T10:01:00Z"))
                .andExpect(status().isOk());

        verify(service).history(eq(1L), eq(T), eq(T.plusSeconds(60)), eq(500));
    }

    @Test
    void historyRangeIsOptional() throws Exception {
        when(service.history(eq(1L), isNull(), isNull(), eq(100))).thenReturn(List.of());

        mvc.perform(get("/api/tracking/vehicles/1/history").param("limit", "100")).andExpect(status().isOk());
    }

    @Test
    void historyWithBadLimitReturns400() throws Exception {
        when(service.history(eq(1L), isNull(), isNull(), eq(0))).thenThrow(new BadRequestException("bad limit"));

        mvc.perform(get("/api/tracking/vehicles/1/history").param("limit", "0")).andExpect(status().isBadRequest());
    }

    @Test
    void historyWithUnparsableDateReturns400() throws Exception {
        mvc.perform(get("/api/tracking/vehicles/1/history").param("from", "yesterday"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonNumericVehicleIdReturns400() throws Exception {
        mvc.perform(get("/api/tracking/vehicles/abc/latest")).andExpect(status().isBadRequest());
    }
}
