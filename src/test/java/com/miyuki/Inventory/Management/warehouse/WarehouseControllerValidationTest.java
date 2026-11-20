package com.miyuki.Inventory.Management.warehouse;

import com.miyuki.Inventory.Management.common.exception.GlobalExceptionHandler;
import com.miyuki.Inventory.Management.user.CurrentUser;
import com.miyuki.Inventory.Management.user.UserAccountRepository;
import com.miyuki.Inventory.Management.warehouses.WarehouseController;
import com.miyuki.Inventory.Management.warehouses.WarehouseService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WarehouseControllerValidationTest {

    @Mock
    private WarehouseService warehouseService;

    @Mock
    private CurrentUser currentUser;

    @Mock
    private UserAccountRepository userRepository;

    private LocalValidatorFactoryBean validator;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        WarehouseController controller = new WarehouseController(
            warehouseService,
            currentUser,
            userRepository
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setValidator(validator)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @AfterEach
    void closeValidatorFactory() {
        validator.close();
    }

    @Test
    void createWarehouse_rejectsZeroCapacity() throws Exception {
        mockMvc.perform(post("/warehouse")
                .contentType(APPLICATION_JSON)
                .content("""
                    {"code":"WH-00001","name":"Warehouse","capacity":0}
                    """))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(warehouseService);
    }

    @Test
    void updateWarehouse_rejectsZeroCapacity() throws Exception {
        mockMvc.perform(put("/warehouse/1")
                .contentType(APPLICATION_JSON)
                .content("""
                    {"code":"WH-00001","name":"Warehouse","capacity":0,"isActive":true}
                    """))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(warehouseService);
    }
}
