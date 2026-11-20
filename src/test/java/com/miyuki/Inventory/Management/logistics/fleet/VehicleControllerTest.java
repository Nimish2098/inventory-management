package com.miyuki.Inventory.Management.logistics.fleet;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


import com.miyuki.Inventory.Management.logistics.common.ConflictException;
import com.miyuki.Inventory.Management.logistics.common.NotFoundException;
import com.miyuki.Inventory.Management.logistics.fleet.FleetDtos.VehicleResponse;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

// addFilters = false: skips your JWT/security filter chain so these tests only exercise the web layer.
// Authorization rules get their own tests in Phase 8.
@WebMvcTest(VehicleController.class)
@AutoConfigureMockMvc(addFilters = false)
class VehicleControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean VehicleService service;

    @Test
    void createReturns201WithLocation() throws Exception {
        when(service.create(any()))
                .thenReturn(new VehicleResponse(
                        5L, "KA01AB1234", new BigDecimal("1000"), new BigDecimal("8"), VehicleStatus.AVAILABLE, null));

        mvc.perform(post("/api/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plate\":\"KA01AB1234\",\"capacityKg\":1000,\"capacityM3\":8}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/vehicles/5"))
                .andExpect(jsonPath("$.plate").value("KA01AB1234"));
    }

    @Test
    void blankPlateReturns400() throws Exception {
        mvc.perform(post("/api/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plate\":\"\",\"capacityKg\":1000,\"capacityM3\":8}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void negativeCapacityReturns400() throws Exception {
        mvc.perform(post("/api/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plate\":\"KA01AB1234\",\"capacityKg\":-5,\"capacityM3\":8}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void missingBodyFieldsReturn400() throws Exception {
        mvc.perform(post("/api/vehicles").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mvc.perform(post("/api/vehicles").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownStatusValueReturns400() throws Exception {
        mvc.perform(post("/api/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plate\":\"KA01AB1234\",\"capacityKg\":1,\"capacityM3\":1,\"status\":\"FLYING\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getMissingVehicleReturns404() throws Exception {
        when(service.get(99L)).thenThrow(new NotFoundException("Vehicle", 99L));

        mvc.perform(get("/api/vehicles/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Vehicle 99 not found"));
    }

    @Test
    void nonNumericIdReturns400() throws Exception {
        mvc.perform(get("/api/vehicles/abc")).andExpect(status().isBadRequest());
    }

    @Test
    void duplicatePlateReturns409() throws Exception {
        when(service.create(any())).thenThrow(new ConflictException("Plate already registered: KA01AB1234"));

        mvc.perform(post("/api/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plate\":\"KA01AB1234\",\"capacityKg\":1000,\"capacityM3\":8}"))
                .andExpect(status().isConflict());
    }
}
