package com.hexora.network.model;

import java.time.LocalDateTime;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AuthorizedNetworkRangeRequest(
    @NotBlank
    @Size(max = 36)
    String projectPublicId,
    @NotBlank
    @Pattern(regexp = "^(?:\\d{1,3}\\.){3}\\d{1,3}/(?:[0-9]|[12][0-9]|3[0-2])$")
    String cidr,
    @NotBlank
    @Size(max = 255)
    String authorizationReference,
    LocalDateTime validUntil
) {
}
