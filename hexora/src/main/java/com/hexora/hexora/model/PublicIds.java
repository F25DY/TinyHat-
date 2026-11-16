package com.hexora.hexora.model;

import java.util.UUID;

public final class PublicIds {

    private PublicIds() {
    }

    public static String ensure(String publicId) {
        return publicId == null || publicId.isBlank() ? UUID.randomUUID().toString() : publicId;
    }
}
