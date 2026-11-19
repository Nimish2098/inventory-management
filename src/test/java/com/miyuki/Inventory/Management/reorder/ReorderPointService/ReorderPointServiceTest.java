package com.miyuki.Inventory.Management.reorder.ReorderPointService;

import com.miyuki.Inventory.Management.product.Product;
import com.miyuki.Inventory.Management.stock.dto.DemandRow;
import com.miyuki.Inventory.Management.stock.dto.ReorderProperties;
import com.miyuki.Inventory.Management.stock.dto.ReorderRecommendation;
import com.miyuki.Inventory.Management.stock.dto.ReorderStatus;
import com.miyuki.Inventory.Management.stock.model.InventoryItem;
import com.miyuki.Inventory.Management.stock.model.StockMovementType;
import com.miyuki.Inventory.Management.stock.repository.InventoryitemRepository;
import com.miyuki.Inventory.Management.stock.repository.StockMovementRepository;
import com.miyuki.Inventory.Management.stock.service.ReorderPointService;
import com.miyuki.Inventory.Management.user.CurrentUser;
import com.miyuki.Inventory.Management.warehouses.Warehouse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReorderPointServiceTest {

  private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
  private static final Long OWNER = 7L;

  private final InventoryitemRepository items = mock(InventoryitemRepository.class);
  private final StockMovementRepository movements = mock(StockMovementRepository.class);
  private final CurrentUser currentUser = mock(CurrentUser.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-11-01T10:00:00Z"), ZoneOffset.UTC);
  private final ReorderProperties props = new ReorderProperties(30, 0.95, 7, 7, 5, ZONE);

  private final ReorderPointService service =
      new ReorderPointService(items, movements, currentUser, props, clock);

  private InventoryItem item(long id, String name, long stock) {
    Product p = mock(Product.class);
    when(p.getId()).thenReturn(id);
    when(p.getName()).thenReturn(name);
    when(p.getLeadTimeDays()).thenReturn(null);
    Warehouse w = mock(Warehouse.class);
    when(w.getId()).thenReturn(id);
    when(w.getName()).thenReturn("WH-" + id);
    InventoryItem i = mock(InventoryItem.class);
    when(i.getId()).thenReturn(id);
    when(i.getProduct()).thenReturn(p);
    when(i.getWarehouse()).thenReturn(w);
    when(i.getQuantity()).thenReturn(stock);
    return i;
  }

  /** 5 units out every day for the 30 complete days: Oct 2 .. Oct 31 (IST). */
  private List<DemandRow> steadyDemand(long itemId) {
    LocalDate start = LocalDate.of(2026, 10, 2);
    List<DemandRow> rows = new ArrayList<>();
    for (int i = 0; i < 30; i++) {
      rows.add(new DemandRow(itemId,
                             start.plusDays(i).atTime(12, 0).atZone(ZONE).toInstant(), -5L));
    }
    return rows;
  }

  @Test
  void sortsByStatusThenDaysOfCover() {
    // build mocks BEFORE the when(...) call
    InventoryItem a = item(1, "A", 100);   // OK
    InventoryItem b = item(2, "B", 10);    // CRITICAL, 2 days of cover
    InventoryItem c = item(3, "C", 20);    // CRITICAL, 4 days of cover
    InventoryItem d = item(4, "D", 50);    // no demand -> NO_DATA

    List<DemandRow> demand = new ArrayList<>();
    demand.addAll(steadyDemand(1));
    demand.addAll(steadyDemand(2));
    demand.addAll(steadyDemand(3));

    when(currentUser.id()).thenReturn(OWNER);
    when(items.findAllByOwnerId(OWNER)).thenReturn(List.of(a, b, c, d));
    when(movements.findDemand(eq(OWNER), eq(StockMovementType.OUTBOUND), any(), any()))
        .thenReturn(demand);

    List<ReorderRecommendation> result = service.calculate(null, null);

    assertEquals(List.of("B", "C", "A", "D"),
                 result.stream().map(ReorderRecommendation::productName).toList());
    assertEquals(ReorderStatus.CRITICAL, result.get(0).status());
    assertEquals(7, result.get(0).leadTimeDays());
    assertEquals(2.0, result.get(0).daysOfCover(), 0.001);
    assertEquals(ReorderStatus.OK, result.get(2).status());
    assertEquals(ReorderStatus.NO_DATA, result.get(3).status());
    assertNull(result.get(3).daysOfCover());
  }

  @Test
  void usesProductLeadTimeWhenConfigured() {
    InventoryItem inventoryItem = item(2, "B", 10);
    when(inventoryItem.getProduct().getLeadTimeDays()).thenReturn(14);
    when(currentUser.id()).thenReturn(OWNER);
    when(items.findAllByOwnerId(OWNER)).thenReturn(List.of(inventoryItem));
    when(movements.findDemand(eq(OWNER), eq(StockMovementType.OUTBOUND), any(), any()))
        .thenReturn(steadyDemand(2));

    ReorderRecommendation recommendation = service.calculate(null, null).get(0);

    assertEquals(14, recommendation.leadTimeDays());
    assertEquals(70, recommendation.reorderPoint());
  }

  @Test
  void queriesExactlyTheLast30CompleteDaysForTheCurrentOwner() {
    when(currentUser.id()).thenReturn(OWNER);
    when(items.findAllByOwnerId(OWNER)).thenReturn(List.of());
    when(movements.findDemand(any(), any(), any(), any())).thenReturn(List.of());

    service.calculate(null, null);

    ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
    ArgumentCaptor<Instant> to = ArgumentCaptor.forClass(Instant.class);
    verify(movements).findDemand(eq(OWNER), eq(StockMovementType.OUTBOUND),
                                 from.capture(), to.capture());

    // Today (Nov 1, IST) is excluded; window is Oct 2 00:00 IST up to Nov 1 00:00 IST
    assertEquals(Instant.parse("2026-10-01T18:30:00Z"), from.getValue());
    assertEquals(Instant.parse("2026-10-31T18:30:00Z"), to.getValue());
    verify(items).findAllByOwnerId(OWNER);
  }
  @Test
  void statusFilterAndWarehouseFilter() {
    InventoryItem b = item(2, "B", 10);    // built first

    when(currentUser.id()).thenReturn(OWNER);
    when(items.findByWarehouse_IdAndProduct_Owner_IdAndWarehouse_Owner_Id(2L, OWNER, OWNER))
        .thenReturn(List.of(b));
    when(movements.findDemand(any(), any(), any(), any())).thenReturn(steadyDemand(2));

    assertEquals(1, service.calculate(2L, ReorderStatus.CRITICAL).size());
    assertTrue(service.calculate(2L, ReorderStatus.OK).isEmpty());
    assertEquals(1, service.alerts(2L).size());
  }
}
