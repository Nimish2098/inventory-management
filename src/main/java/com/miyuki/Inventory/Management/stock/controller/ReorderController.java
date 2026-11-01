package com.miyuki.Inventory.Management.stock.controller;

import com.miyuki.Inventory.Management.stock.dto.ReorderRecommendation;
import com.miyuki.Inventory.Management.stock.dto.ReorderStatus;
import com.miyuki.Inventory.Management.stock.service.ReorderPointService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/stock/reorder-recommendations")
public class ReorderController {

  private final ReorderPointService service;

  public ReorderController(ReorderPointService service) { this.service = service; }

  @GetMapping
  public List<ReorderRecommendation> list(
      @RequestParam(required = false) Long warehouseId,
      @RequestParam(required = false) ReorderStatus status) {
    return service.calculate(warehouseId, status);
  }

  @GetMapping("/alerts")
  public List<ReorderRecommendation> alerts(@RequestParam(required = false) Long warehouseId) {
    return service.alerts(warehouseId);
  }
}