package com.miyuki.Inventory.Management.reorder;

import com.miyuki.Inventory.Management.stock.dto.ReorderStatus;
import com.miyuki.Inventory.Management.stock.service.ReorderCalculator;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReorderCalculatorTest {

  private static double[] alternating() {
    double[] d = new double[30];
    for (int i = 0; i < 30; i++) d[i] = i % 2 == 0 ? 10 : 14;
    return d;
  }

  @Test
  void constantDemand_hasNoSafetyStock() {
    double[] d = new double[30];
    Arrays.fill(d, 10);
    var r = ReorderCalculator.compute(d, 5, 0.95, 80, 7, 5);

    assertEquals(0, r.safetyStock());
    assertEquals(50, r.reorderPoint());
    assertEquals(ReorderStatus.OK, r.status());
    assertEquals(0, r.suggestedQty());
  }

  @Test
  void variableDemand_belowRop_isReorder() {
    var r = ReorderCalculator.compute(alternating(), 4, 0.95, 50, 7, 5);

    assertEquals(12.0, r.mean(), 0.001);
    assertEquals(7, r.safetyStock());
    assertEquals(55, r.reorderPoint());
    assertEquals(ReorderStatus.REORDER, r.status());     // 50 >= 48 lead-time demand
    assertEquals(89, r.suggestedQty());                  // 55 + 12*7 - 50
  }

  @Test
  void stockBelowLeadTimeDemand_isCritical() {
    var r = ReorderCalculator.compute(alternating(), 4, 0.95, 40, 7, 5);
    assertEquals(ReorderStatus.CRITICAL, r.status());    // 40 < 48
  }

  @Test
  void tooFewSalesDays_isNoData() {
    double[] d = new double[30];
    d[3] = 10; d[9] = 12;
    var r = ReorderCalculator.compute(d, 5, 0.95, 50, 7, 5);
    assertEquals(ReorderStatus.NO_DATA, r.status());
  }
}