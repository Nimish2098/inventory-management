package com.miyuki.Inventory.Management.stock.service;

import com.miyuki.Inventory.Management.stock.dto.ReorderStatus;
import org.apache.commons.math3.distribution.NormalDistribution;

import java.util.Arrays;

public final class ReorderCalculator {

  private static final NormalDistribution NORMAL = new NormalDistribution();

  public record Result(double mean, double stdDev, double z, double leadTimeDemand,
                       int safetyStock, int reorderPoint, int suggestedQty,
                       ReorderStatus status) {
    static Result noData() {
      return new Result(0, 0, 0, 0, 0, 0, 0, ReorderStatus.NO_DATA);
    }
  }

  private ReorderCalculator() {}

  /** daily[] holds one entry per day in the window, including zero-demand days. */
  public static Result compute(double[] daily, int leadTimeDays, double serviceLevel,
                               long stock, int coverDays, int minDemandDays) {

    long demandDays = Arrays.stream(daily).filter(d -> d > 0).count();
    if (demandDays < minDemandDays) return Result.noData();

    double mean = Arrays.stream(daily).average().orElse(0);
    double variance = daily.length > 1
                      ? Arrays.stream(daily).map(d -> (d - mean) * (d - mean)).sum() / (daily.length - 1)
                      : 0;
    double stdDev = Math.sqrt(variance);

    double z = NORMAL.inverseCumulativeProbability(serviceLevel);
    double leadTimeDemand = mean * leadTimeDays;
    double safetyRaw = z * stdDev * Math.sqrt(leadTimeDays);

    int safetyStock = (int) Math.ceil(safetyRaw);
    int reorderPoint = (int) Math.ceil(leadTimeDemand + safetyRaw);

    ReorderStatus status;
    int suggested = 0;
    if (stock < leadTimeDemand) {
      status = ReorderStatus.CRITICAL;     // likely to run out before a restock arrives
    } else if (stock <= reorderPoint) {
      status = ReorderStatus.REORDER;
    } else {
      status = ReorderStatus.OK;
    }
    if (status != ReorderStatus.OK) {
      suggested = Math.max(0, (int) Math.ceil(reorderPoint + mean * coverDays - stock));
    }

    return new Result(mean, stdDev, z, leadTimeDemand, safetyStock, reorderPoint,
                      suggested, status);
  }
}