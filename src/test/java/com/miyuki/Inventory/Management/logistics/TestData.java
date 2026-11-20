package com.miyuki.Inventory.Management.logistics;

import com.miyuki.Inventory.Management.logistics.delivery.Delivery;
import com.miyuki.Inventory.Management.logistics.fleet.Driver;
import com.miyuki.Inventory.Management.logistics.fleet.Vehicle;
import com.miyuki.Inventory.Management.logistics.route.Route;
import com.miyuki.Inventory.Management.logistics.route.RouteStatus;
import java.math.BigDecimal;

public final class TestData {
    private TestData() {}

    public static Driver driver(String license) {
        Driver d = new Driver();
        d.setFullName("Test Driver " + license);
        d.setPhone("9876543210");
        d.setLicenseNumber(license);
        return d;
    }

    public static Vehicle vehicle(String plate) {
        Vehicle v = new Vehicle();
        v.setPlate(plate);
        v.setCapacityKg(new BigDecimal("1500.00"));
        v.setCapacityM3(new BigDecimal("12.50"));
        return v;
    }

    public static Delivery delivery(long orderId) {
        Delivery d = new Delivery();
        d.setOrderId(orderId);
        d.setDestinationLat(12.9716);
        d.setDestinationLng(77.5946);
        d.setDestinationAddress("MG Road, Bengaluru");
        d.setWeightKg(new BigDecimal("40.00"));
        d.setVolumeM3(new BigDecimal("0.50"));
        return d;
    }

    public static Route route(Vehicle vehicle, RouteStatus status) {
        Route r = new Route();
        r.setVehicle(vehicle);
        r.setStatus(status);
        return r;
    }
}
