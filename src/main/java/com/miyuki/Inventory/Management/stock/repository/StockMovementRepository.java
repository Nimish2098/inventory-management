package com.miyuki.Inventory.Management.stock.repository;

import com.miyuki.Inventory.Management.stock.dto.DemandRow;
import com.miyuki.Inventory.Management.stock.model.StockMovement;
import com.miyuki.Inventory.Management.stock.model.StockMovementType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface StockMovementRepository extends JpaRepository<StockMovement,Long> {

    @Query("select movement from StockMovement movement where movement.inventoryItem.product.id = :productId " +
	    "and movement.inventoryItem.warehouse.id = :warehouseId order by movement.id desc")
    List<StockMovement> findByProductAndWarehouse(@Param("productId") Long productId,
						   @Param("warehouseId") Long warehouseId);

    @Query("""
        select new com.miyuki.Inventory.Management.stock.dto.DemandRow(
            item.id, movement.createdAt, movement.quantityChange)
        from StockMovement movement
        join movement.inventoryItem item
        where item.product.owner.id = :ownerId
          and item.warehouse.owner.id = :ownerId
          and movement.type = :type
          and movement.createdAt >= :from
          and movement.createdAt < :to
        """)
    List<DemandRow> findDemand(@Param("ownerId") Long ownerId,
                               @Param("type") StockMovementType type,
                               @Param("from") Instant from,
                               @Param("to") Instant to);
}
