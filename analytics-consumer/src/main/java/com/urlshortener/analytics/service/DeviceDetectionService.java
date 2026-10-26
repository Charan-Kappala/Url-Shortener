package com.urlshortener.analytics.service;

import eu.bitwalker.useragentutils.UserAgent;
import org.springframework.stereotype.Service;

@Service
public class DeviceDetectionService {

    public String detectDeviceType(String userAgentString) {
        if (userAgentString == null || userAgentString.isBlank()) {
            return "UNKNOWN";
        }
        try {
            UserAgent ua = UserAgent.parseUserAgentString(userAgentString);
            eu.bitwalker.useragentutils.DeviceType deviceType =
                    ua.getOperatingSystem().getDeviceType();

            String name = deviceType.getName();
            if (name == null) return "UNKNOWN";

            // Map UserAgentUtils device type names to canonical values
            return switch (name.toLowerCase()) {
                case "mobile"   -> "MOBILE";
                case "tablet"   -> "TABLET";
                case "computer" -> "DESKTOP";
                default         -> "OTHER";
            };
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }
}
