package com.miyuki.Inventory.Management.logistics.delivery;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryRepository extends JpaRepository<Delivery, Long> {
    Page<Delivery> findByStatus(DeliveryStatus status, Pageable pageable);
}
