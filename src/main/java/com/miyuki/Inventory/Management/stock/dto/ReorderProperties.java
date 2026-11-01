package com.miyuki.Inventory.Management.stock.dto;


import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.ZoneId;

@ConfigurationProperties(prefix = "apexstock.reorder")
public record ReorderProperties(
    @DefaultValue("30") int windowDays,
    @DefaultValue("0.95") double serviceLevel,
    @DefaultValue("7") int defaultLeadTimeDays,
    @DefaultValue("7") int coverDays,
    @DefaultValue("5") int minDemandDays,
    @DefaultValue("Asia/Kolkata") ZoneId zone) {}
