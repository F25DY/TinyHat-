package com.hexora.network.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProjectNetworkRequest(@NotBlank @Size(max = 36) String projectPublicId) {
}
