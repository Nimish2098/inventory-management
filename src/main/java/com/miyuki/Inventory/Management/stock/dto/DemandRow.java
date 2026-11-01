package com.miyuki.Inventory.Management.stock.dto;

import java.time.Instant;

public record DemandRow(Long inventoryItemId, Instant createdAt, Long quantityChange) {}