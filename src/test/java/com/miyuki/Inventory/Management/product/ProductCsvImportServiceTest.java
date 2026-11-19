package com.miyuki.Inventory.Management.product;

import com.miyuki.Inventory.Management.product.dto.ProductImportResponse;
import com.miyuki.Inventory.Management.user.CurrentUser;
import com.miyuki.Inventory.Management.user.UserAccount;
import com.miyuki.Inventory.Management.user.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ProductCsvImportServiceTest
{
    @Test
    void importsOptionalProductLeadTime() {
        ProductRepository productRepository = mock(ProductRepository.class);
        CurrentUser currentUser = mock(CurrentUser.class);
        UserAccountRepository userRepository = mock(UserAccountRepository.class);
        ProductMapper mapper = new ProductMapper();
        UserAccount owner = new UserAccount();
        owner.setId(7L);

        when(currentUser.id()).thenReturn(7L);
        when(userRepository.findById(7L)).thenReturn(Optional.of(owner));
        when(productRepository.findAllByOwner_IdAndSkuIn(eq(7L), anyList()))
                .thenReturn(List.of());
        when(productRepository.saveAll(any())).thenAnswer(invocation -> {
            List<Product> products = invocation.getArgument(0);
            assertEquals(5, products.get(0).getLeadTimeDays());
            return products;
        });

        ProductCsvImportService service = new ProductCsvImportService(
                productRepository, mapper, currentUser, userRepository);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "products.csv",
                "text/csv",
                ("sku,name,category,reorder_level,lead_time_days\n"
                        + "SKU-1,Widget,Tools,2,5\n").getBytes(StandardCharsets.UTF_8));

        ProductImportResponse result = service.importProducts(file);

        assertEquals(1, result.imported());
        assertEquals(0, result.failed());
        verify(productRepository).saveAll(any());
    }
}
