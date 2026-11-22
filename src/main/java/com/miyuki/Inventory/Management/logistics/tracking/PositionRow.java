package com.miyuki.Inventory.Management.logistics.tracking;

import java.time.Instant;

/** One validated GPS ping as it travels from the stream to the database and the cache. */
public record PositionRow(
        long vehicleId,
        double lat,
        double lng,
        Double speedKmh,
        Double heading,
        Instant recordedAt,
        Instant receivedAt) {}
