package com.hexora.network.model;

import java.util.List;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record NetworkHostRegistrationRequest(
    @NotBlank
    @Size(max = 100)
    String name,
    @NotBlank
    @Size(max = 45)
    String address,
    @Size(max = 32)
    List<@Positive @Max(65535) Integer> ports,
    @NotBlank
    @Size(max = 36)
    String projectPublicId
) {

    public NetworkHostRegistrationRequest(String name, String address, List<Integer> ports) {
        this(name, address, ports, null);
    }
}
