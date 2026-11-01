package com.miyuki.Inventory.Management.stock.dto;

public record ReorderRecommendation(
    Long inventoryItemId,
    Long productId,
    String productName,
    Long warehouseId,
    String warehouseName,
    long currentStock,
    Double daysOfCover,
    double avgDailyDemand,
    double demandStdDev,
    int leadTimeDays,
    double serviceLevel,
    int safetyStock,
    int reorderPoint,
    int suggestedOrderQty,
    ReorderStatus status,
    String explanation) {}