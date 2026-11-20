package com.miyuki.Inventory.Management.logistics.common;

public class NotFoundException extends RuntimeException {
    public NotFoundException(String entity, Object id) {
        super(entity + " " + id + " not found");
    }
}
