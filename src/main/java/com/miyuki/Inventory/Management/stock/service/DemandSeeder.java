package com.miyuki.Inventory.Management.stock.service;

import com.miyuki.Inventory.Management.stock.model.InventoryItem;
import com.miyuki.Inventory.Management.stock.model.StockMovement;
import com.miyuki.Inventory.Management.stock.model.StockMovementType;
import com.miyuki.Inventory.Management.stock.repository.InventoryitemRepository;
import com.miyuki.Inventory.Management.stock.repository.StockMovementRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Component
@Profile("dev")
@ConditionalOnProperty(name = "apexstock.seed-demand", havingValue = "true")

class DemandSeeder implements CommandLineRunner
{

  private final InventoryitemRepository items;
  private final StockMovementRepository movements;

  DemandSeeder(InventoryitemRepository items, StockMovementRepository movements) {
    this.items = items;
    this.movements = movements;
  }
  @Override
  public void run(String... args) {
    boolean alreadySeeded = movements.findAll().stream()
                                     .anyMatch(m -> m.getCreatedAt().isBefore(Instant.now().minus(2, ChronoUnit.DAYS)));
    if (alreadySeeded) return;

    Random rnd = new Random(42);
    double[] factors = {2.0, 2.0, 2.0, 1.1, 0.6};      // ~60% OK, 20% REORDER, 20% CRITICAL
    int leadTime = 7;
    List<StockMovement> batch = new ArrayList<>();

    for (InventoryItem item : items.findAll()) {
      double factor = factors[rnd.nextInt(factors.length)];
      double base = Math.max(1, item.getQuantity() / (leadTime * factor));

      for (int d = 1; d <= 60; d++) {
        long qty = Math.round(base + rnd.nextGaussian() * base * 0.3);
        if (qty <= 0) continue;
        StockMovement m = new StockMovement();
        m.setInventoryItem(item);
        m.setQuantityChange(-qty);
        m.setType(StockMovementType.OUTBOUND);
        m.setCreatedAt(Instant.now().minus(d, ChronoUnit.DAYS));
        batch.add(m);
      }
    }
    movements.saveAll(batch);
  }
}