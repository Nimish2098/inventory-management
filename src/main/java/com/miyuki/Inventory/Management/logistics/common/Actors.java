package com.miyuki.Inventory.Management.logistics.common;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Resolves "who did this" for the audit log from the current JWT/security context. */
public final class Actors {
    private static final int MAX = 100;

    private Actors() {}

    public static String current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return "system";
        }
        String name = auth.getName();
        if (name == null || name.isBlank()) {
            return "system";
        }
        return name.length() > MAX ? name.substring(0, MAX) : name;
    }
}
