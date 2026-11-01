package com.miyuki.Inventory.Management.stock.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StockBatchRepository extends JpaRepository<StockBatch, Long> {

  List<StockBatch> findByInventoryItem_IdOrderByExpiryDateAscIdAsc(Long inventoryItemId);

  Optional<StockBatch> findByInventoryItem_IdAndBatchNumber(Long inventoryItemId, String batchNumber);
}