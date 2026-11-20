package com.miyuki.Inventory.Management.logistics.fleet;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.miyuki.Inventory.Management.logistics.common.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DriverController.class)
@AutoConfigureMockMvc(addFilters = false)
class DriverControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean DriverService service;

    @Test
    void invalidPhoneReturns400() throws Exception {
        mvc.perform(post("/api/drivers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Ravi\",\"phone\":\"abc\",\"licenseNumber\":\"KA-1\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void blankNameReturns400() throws Exception {
        mvc.perform(post("/api/drivers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\" \",\"phone\":\"9876543210\",\"licenseNumber\":\"KA-1\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getMissingDriverReturns404() throws Exception {
        when(service.get(9L)).thenThrow(new NotFoundException("Driver", 9L));
        mvc.perform(get("/api/drivers/9")).andExpect(status().isNotFound());
    }
}
