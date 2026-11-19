package com.miyuki.Inventory.Management.stock.config;

import com.miyuki.Inventory.Management.stock.dto.ReorderProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(ReorderProperties.class)
public class ReorderConfiguration {

  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }
}
