package com.miyuki.Inventory.Management.stock.dto;


import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.ZoneId;
import java.util.Objects;

@ConfigurationProperties(prefix = "apexstock.reorder")
public record ReorderProperties(
    @DefaultValue("30") int windowDays,
    @DefaultValue("0.95") double serviceLevel,
    @DefaultValue("7") int defaultLeadTimeDays,
    @DefaultValue("7") int coverDays,
    @DefaultValue("5") int minDemandDays,
    @DefaultValue("Asia/Kolkata") ZoneId zone) {

  public ReorderProperties {
    if (windowDays < 1) {
      throw new IllegalArgumentException("Reorder window must be at least one day");
    }
    if (!Double.isFinite(serviceLevel) || serviceLevel <= 0 || serviceLevel >= 1) {
      throw new IllegalArgumentException("Reorder service level must be between zero and one");
    }
    if (defaultLeadTimeDays < 1) {
      throw new IllegalArgumentException("Default lead time must be at least one day");
    }
    if (coverDays < 0) {
      throw new IllegalArgumentException("Reorder cover days cannot be negative");
    }
    if (minDemandDays < 1 || minDemandDays > windowDays) {
      throw new IllegalArgumentException("Minimum demand days must be within the reorder window");
    }
    Objects.requireNonNull(zone, "Reorder time zone is required");
  }
}
