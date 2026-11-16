package com.hexora.network.service;

import com.hexora.hexora.security.TinyHatSecurityProperties;
import org.springframework.stereotype.Component;

@Component
public class TinyHatNetworkPolicy {

    private final TinyHatSecurityProperties properties;

    public TinyHatNetworkPolicy(TinyHatSecurityProperties properties) {
        this.properties = properties;
    }

    int maxPrefix() {
        return properties.getNetwork().getMaxPrefix();
    }

    boolean allowPublicRanges() {
        return properties.getNetwork().isAllowPublicRanges();
    }

    int scanCooldownSeconds() {
        return properties.getNetwork().getScanCooldownSeconds();
    }
}
