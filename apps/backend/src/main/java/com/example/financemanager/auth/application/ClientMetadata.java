package com.example.financemanager.auth.application;

import java.net.InetAddress;
import java.net.UnknownHostException;

public record ClientMetadata(
        String userAgent,
        InetAddress ipAddress
) {
    private static final int MAX_METADATA_LENGTH = 1000;

    public static ClientMetadata of(
            String deviceName,
            String userAgent,
            String remoteAddress
    ) {
        String metadata = sanitize(deviceName, userAgent);
        return new ClientMetadata(metadata, parseAddress(remoteAddress));
    }

    private static String sanitize(String deviceName, String userAgent) {
        String safeDeviceName = clean(deviceName);
        String safeUserAgent = clean(userAgent);
        String combined = safeDeviceName == null
                ? safeUserAgent
                : safeUserAgent == null
                        ? safeDeviceName
                        : safeDeviceName + " | " + safeUserAgent;

        if (combined == null || combined.length() <= MAX_METADATA_LENGTH) {
            return combined;
        }
        return combined.substring(0, MAX_METADATA_LENGTH);
    }

    private static String clean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.replace('\r', ' ').replace('\n', ' ').trim();
    }

    private static InetAddress parseAddress(String remoteAddress) {
        if (remoteAddress == null || remoteAddress.isBlank()) {
            return null;
        }
        try {
            return InetAddress.getByName(remoteAddress);
        } catch (UnknownHostException exception) {
            return null;
        }
    }
}
