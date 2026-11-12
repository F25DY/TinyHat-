package com.hexora.network.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Shared body for POST /ports (needs {@code port}) and PUT /ports/{port} (needs {@code enabled}).
 * Presence of each field is enforced by the service for the operation that uses it, so that a
 * missing host still answers 404 before the body is interpreted.
 */
public record MonitoredPortRequest(
    @Min(1) @Max(65535) Integer port,
    Boolean enabled
) {
}
