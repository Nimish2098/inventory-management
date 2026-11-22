package com.miyuki.Inventory.Management.logistics.tracking;

import com.miyuki.Inventory.Management.logistics.tracking.TrackingDtos.LatestPosition;
import java.time.Instant;

/**
 * Payload of the Redis pub/sub channel that carries "this ping just became the vehicle's latest position":
 * {@code vehicleId,recordedAtMillis,lat,lng,speedKmh,headingDeg} (speed/heading empty when unknown).
 */
public final class LivePositionCodec {

    private LivePositionCodec() {}

    public static String encode(PositionRow r) {
        return r.vehicleId()
                + ","
                + r.recordedAt().toEpochMilli()
                + ","
                + r.lat()
                + ","
                + r.lng()
                + ","
                + (r.speedKmh() == null ? "" : r.speedKmh())
                + ","
                + (r.heading() == null ? "" : r.heading());
    }

    /** @throws IllegalArgumentException when the payload is malformed */
    public static LatestPosition decode(String payload) {
        if (payload == null) {
            throw new IllegalArgumentException("payload is null");
        }
        String[] f = payload.split(",", -1);
        if (f.length != 6) {
            throw new IllegalArgumentException("expected 6 fields but got " + f.length);
        }
        try {
            return new LatestPosition(
                    Long.valueOf(f[0]),
                    Double.parseDouble(f[2]),
                    Double.parseDouble(f[3]),
                    f[4].isEmpty() ? null : Double.valueOf(f[4]),
                    f[5].isEmpty() ? null : Double.valueOf(f[5]),
                    Instant.ofEpochMilli(Long.parseLong(f[1])));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("payload has a non-numeric field: " + payload);
        }
    }
}
