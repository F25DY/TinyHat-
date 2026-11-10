package com.hexora.hexora.security;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tinyhat.capabilities")
public class TinyHatCapabilityProperties {

    private Map<String, Boolean> defaults = new LinkedHashMap<>(Map.of(
        "modulo-red", true,
        "informes-pdf", true,
        "monitoreo-continuo", true
    ));

    public Map<String, Boolean> getDefaults() {
        return defaults;
    }

    public void setDefaults(Map<String, Boolean> defaults) {
        this.defaults = defaults;
    }
}
