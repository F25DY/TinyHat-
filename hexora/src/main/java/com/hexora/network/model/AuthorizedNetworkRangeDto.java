package com.hexora.network.model;

import java.time.LocalDateTime;

public record AuthorizedNetworkRangeDto(
    String projectPublicId,
    String cidr,
    String authorizationReference,
    LocalDateTime declaredAt,
    LocalDateTime validUntil
) {
}
