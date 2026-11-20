package com.miyuki.Inventory.Management;

import com.miyuki.Inventory.Management.test.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(properties = "app.security.jwt-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
class InventoryManagementApplicationTests extends PostgresIntegrationTest {

	@Test
	void contextLoads() {
	}

}
