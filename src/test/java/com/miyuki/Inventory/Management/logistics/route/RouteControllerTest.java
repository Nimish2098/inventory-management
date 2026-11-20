package com.miyuki.Inventory.Management.logistics.route;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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

@WebMvcTest(RouteController.class)
@AutoConfigureMockMvc(addFilters = false)
class RouteControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean RouteService service;

    @Test
    void missingVehicleIdReturns400() throws Exception {
        mvc.perform(post("/api/routes").contentType(MediaType.APPLICATION_JSON).content("{\"deliveryIds\":[1]}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void vehicleWithOpenRouteReturns409() throws Exception {
        when(service.create(any())).thenThrow(new ConflictException("Vehicle 1 already has an active route"));

        mvc.perform(post("/api/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehicleId\":1,\"deliveryIds\":[1,2]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Vehicle 1 already has an active route"));
    }

    @Test
    void getMissingRouteReturns404() throws Exception {
        when(service.get(77L)).thenThrow(new NotFoundException("Route", 77L));
        mvc.perform(get("/api/routes/77")).andExpect(status().isNotFound());
    }

    @Test
    void listWithoutVehicleIdReturns400() throws Exception {
        mvc.perform(get("/api/routes")).andExpect(status().isBadRequest());
    }
}
