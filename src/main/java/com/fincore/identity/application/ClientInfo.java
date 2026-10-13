package com.fincore.identity.application;

/** Desde dónde se inicia sesión: se guarda en la sesión para que el usuario reconozca sus dispositivos. */
public record ClientInfo(String deviceName, String userAgent, String ipAddress) {

    public ClientInfo {
        userAgent = truncate(userAgent, 255);
        deviceName = truncate(deviceName, 100);
    }

    private static String truncate(String value, int max) {
        return value != null && value.length() > max ? value.substring(0, max) : value;
    }
}
