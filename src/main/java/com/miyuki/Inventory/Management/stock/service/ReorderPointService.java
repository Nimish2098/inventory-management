package com.miyuki.Inventory.Management.stock.service;

import com.miyuki.Inventory.Management.product.Product;
import com.miyuki.Inventory.Management.stock.dto.DemandRow;
import com.miyuki.Inventory.Management.stock.dto.ReorderProperties;
import com.miyuki.Inventory.Management.stock.dto.ReorderRecommendation;
import com.miyuki.Inventory.Management.stock.dto.ReorderStatus;
import com.miyuki.Inventory.Management.stock.model.InventoryItem;
import com.miyuki.Inventory.Management.stock.model.StockMovementType;
import com.miyuki.Inventory.Management.stock.repository.InventoryitemRepository;
import com.miyuki.Inventory.Management.stock.repository.StockMovementRepository;
import com.miyuki.Inventory.Management.user.CurrentUser;
import com.miyuki.Inventory.Management.warehouses.Warehouse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReorderPointService {

  private final InventoryitemRepository inventoryitemRepository;
  private final StockMovementRepository stockMovementRepository;
  private final CurrentUser currentUser;
  private final ReorderProperties props;
  private final Clock clock;

  public ReorderPointService(InventoryitemRepository inventoryitemRepository,
                             StockMovementRepository stockMovementRepository,
                             CurrentUser currentUser,
                             ReorderProperties props,
                             Clock clock) {
    this.inventoryitemRepository = inventoryitemRepository;
    this.stockMovementRepository = stockMovementRepository;
    this.currentUser = currentUser;
    this.props = props;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public List<ReorderRecommendation> calculate(Long warehouseId, ReorderStatus statusFilter) {
    Long ownerId = currentUser.id();
    int window = props.windowDays();
    ZoneId zone = props.zone();

    LocalDate today = LocalDate.now(clock.withZone(zone));
    LocalDate start = today.minusDays(window);               // window = last N complete days
    Instant from = start.atStartOfDay(zone).toInstant();
    Instant to = today.atStartOfDay(zone).toInstant();

    // Bucket OUTBOUND movements into per-item daily arrays
    Map<Long, double[]> dailyByItem = new HashMap<>();
    for (DemandRow r : stockMovementRepository
        .findDemand(ownerId, StockMovementType.OUTBOUND, from, to)) {
      int idx = (int) ChronoUnit.DAYS.between(
          start, r.createdAt().atZone(zone).toLocalDate());
      if (idx < 0 || idx >= window) continue;
      dailyByItem.computeIfAbsent(r.inventoryItemId(), k -> new double[window])[idx]
          += Math.abs(r.quantityChange());              // outbound is stored negative
    }

    List<InventoryItem> items = warehouseId == null
                                ? inventoryitemRepository.findAllByOwnerId(ownerId)
                                : inventoryitemRepository.findByWarehouse_IdAndProduct_Owner_IdAndWarehouse_Owner_Id(
                                    warehouseId, ownerId, ownerId);

    return items.stream()
                .map(item -> toRecommendation(item,
                                              dailyByItem.getOrDefault(item.getId(), new double[window])))
                .filter(r -> statusFilter == null || r.status() == statusFilter)
                .sorted(Comparator.comparing(ReorderRecommendation::status)
                                  .thenComparing(r -> r.daysOfCover() == null ? Double.MAX_VALUE : r.daysOfCover())
                                  .thenComparing(ReorderRecommendation::productName)).toList();
  }

  private ReorderRecommendation toRecommendation(InventoryItem item, double[] daily) {
    Product product = item.getProduct();
    Warehouse warehouse = item.getWarehouse();
    int lead = product.getLeadTimeDays() != null
               ? product.getLeadTimeDays() : props.defaultLeadTimeDays();
    long stock = item.getQuantity();

    var r = ReorderCalculator.compute(daily, lead, props.serviceLevel(), stock,
                                      props.coverDays(), props.minDemandDays());

    String explanation = r.status() == ReorderStatus.NO_DATA
                         ? "Fewer than " + props.minDemandDays() + " days with sales in the last "
                           + props.windowDays() + " days, so no demand-based threshold yet."
                         : String.format(
                             "Avg demand %.1f/day (σ %.1f) over %d days, lead time %d days, %.0f%% service "
                             + "level (Z=%.2f). Lead-time demand %.0f + safety stock %d = reorder point %d. "
                             + "Current stock %d is %s.",
                             r.mean(), r.stdDev(), props.windowDays(), lead, props.serviceLevel() * 100,
                             r.z(), r.leadTimeDemand(), r.safetyStock(), r.reorderPoint(), stock,
                             stock > r.reorderPoint() ? "above it" : "at or below it");

    Double cover = r.mean() > 0 ? round(stock / r.mean()) : null;

    return new ReorderRecommendation(item.getId(), product.getId(), product.getName(),
                                     warehouse.getId(), warehouse.getName(), stock, cover,
                                     round(r.mean()), round(r.stdDev()), lead, props.serviceLevel(),
                                     r.safetyStock(), r.reorderPoint(), r.suggestedQty(), r.status(), explanation);
  }
  @Transactional(readOnly = true)
  public List<ReorderRecommendation> alerts(Long warehouseId) {
    return calculate(warehouseId, null).stream()
                                       .filter(r -> r.status() == ReorderStatus.CRITICAL
                                                    || r.status() == ReorderStatus.REORDER)
                                       .toList();
  }
  private static double round(double v) { return Math.round(v * 100.0) / 100.0; }
}