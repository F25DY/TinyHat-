package com.hexora.network.model;

import java.time.LocalDateTime;

public record AuthorizedNetworkRangeRequest(
    String projectPublicId,
    String cidr,
    String authorizationReference,
    LocalDateTime validUntil
) {
}
