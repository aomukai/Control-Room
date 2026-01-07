package com.miniide;

import java.util.Locale;

public final class RoleKey {

    private RoleKey() {
    }

    public static String canonicalize(String role) {
        if (role == null) {
            return null;
        }
        String trimmed = role.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String lowered = trimmed.toLowerCase(Locale.ROOT);
        return lowered.replaceAll("\\s+", " ");
    }
}
