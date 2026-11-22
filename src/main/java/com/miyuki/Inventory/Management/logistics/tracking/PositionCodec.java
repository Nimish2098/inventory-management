package com.miyuki.Inventory.Management.logistics.tracking;

import com.miyuki.Inventory.Management.logistics.tracking.TrackingDtos.PositionDto;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Packs one HTTP batch into ONE stream entry (one Redis write per request, no JSON library needed):
 *   vehicleId  = "42"
 *   receivedAt = "1763719200000"
 *   data       = "recordedAtMillis,lat,lng,speed,heading\n..."   (speed/heading empty when unknown)
 * decode() re-validates everything, so a corrupt entry becomes a dead-letter instead of bad data in the DB.
 */
public final class PositionCodec {

    private PositionCodec() {}

    public static Map<String, String> encode(Long vehicleId, List<PositionDto> positions, Instant receivedAt) {
        StringBuilder data = new StringBuilder(positions.size() * 48);
        for (PositionDto p : positions) {
            data.append(p.recordedAt().toEpochMilli())
                    .append(',')
                    .append(p.lat().toPlainString())
                    .append(',')
                    .append(p.lng().toPlainString())
                    .append(',')
                    .append(p.speedKmh() == null ? "" : p.speedKmh().toPlainString())
                    .append(',')
                    .append(p.heading() == null ? "" : p.heading().toPlainString())
                    .append('\n');
        }
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("vehicleId", String.valueOf(vehicleId));
        fields.put("receivedAt", String.valueOf(receivedAt.toEpochMilli()));
        fields.put("data", data.toString());
        return fields;
    }

    /** @throws IllegalArgumentException if the entry is malformed or contains out-of-range values */
    public static List<PositionRow> decode(Map<String, String> fields) {
        String vehicle = fields.get("vehicleId");
        String received = fields.get("receivedAt");
        String data = fields.get("data");
        if (vehicle == null || received == null || data == null) {
            throw new IllegalArgumentException("entry is missing vehicleId, receivedAt or data");
        }
        long vehicleId = parseLong(vehicle, "vehicleId");
        if (vehicleId <= 0) {
            throw new IllegalArgumentException("vehicleId must be positive");
        }
        Instant receivedAt = Instant.ofEpochMilli(parseLong(received, "receivedAt"));

        List<PositionRow> rows = new ArrayList<>();
        for (String line : data.split("\n")) {
            if (line.isEmpty()) {
                continue;
            }
            String[] f = line.split(",", -1);
            if (f.length != 5) {
                throw new IllegalArgumentException("expected 5 fields per ping but got " + f.length);
            }
            Instant recordedAt = Instant.ofEpochMilli(parseLong(f[0], "recordedAt"));
            double lat = parseDouble(f[1], "lat");
            double lng = parseDouble(f[2], "lng");
            Double speed = f[3].isEmpty() ? null : parseDouble(f[3], "speedKmh");
            Double heading = f[4].isEmpty() ? null : parseDouble(f[4], "heading");
            require(lat >= -90 && lat <= 90, "lat out of range");
            require(lng >= -180 && lng <= 180, "lng out of range");
            require(speed == null || (speed >= 0 && speed <= 300), "speedKmh out of range");
            require(heading == null || (heading >= 0 && heading <= 360), "heading out of range");
            rows.add(new PositionRow(vehicleId, lat, lng, speed, heading, recordedAt, receivedAt));
        }
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("entry contains no positions");
        }
        return rows;
    }

    private static long parseLong(String s, String name) {
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " is not a number: '" + s + "'");
        }
    }

    private static double parseDouble(String s, String name) {
        try {
            double v = new BigDecimal(s.trim()).doubleValue();
            if (!Double.isFinite(v)) {
                throw new IllegalArgumentException(name + " is not finite");
            }
            return v;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " is not a number: '" + s + "'");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
