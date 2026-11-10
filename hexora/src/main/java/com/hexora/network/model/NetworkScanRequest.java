package com.hexora.network.model;

public record NetworkScanRequest(Long hostId, Integer startPort, Integer endPort, String projectPublicId) {

    public NetworkScanRequest(Long hostId, Integer startPort, Integer endPort) {
        this(hostId, startPort, endPort, null);
    }
}
