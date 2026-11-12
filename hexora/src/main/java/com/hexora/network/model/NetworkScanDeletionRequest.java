package com.hexora.network.model;

import java.util.List;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record NetworkScanDeletionRequest(
    @NotEmpty @Size(max = 100) List<@Positive Long> scanIds
) {
}
