package com.miyuki.Inventory.Management.logistics.tracking;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on @Scheduled so the stream consumer can poll. Harmless if scheduling is already enabled elsewhere. */
@Configuration
@EnableScheduling
public class TrackingConfig {}
