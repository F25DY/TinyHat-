package com.hexora.network.model;

import java.util.List;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;

public record NetworkHostPortsRequest(
    @NotEmpty @Size(max = 32) List<@Positive @Max(65535) Integer> ports
) {
}
