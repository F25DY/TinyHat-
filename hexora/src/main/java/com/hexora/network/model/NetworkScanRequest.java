package com.hexora.network.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record NetworkScanRequest(
    @NotNull @Positive Long hostId,
    @Min(1) @Max(65535) Integer startPort,
    @Min(1) @Max(65535) Integer endPort,
    @NotBlank @Size(max = 36) String projectPublicId
) {

    public NetworkScanRequest(Long hostId, Integer startPort, Integer endPort) {
        this(hostId, startPort, endPort, null);
    }
}
