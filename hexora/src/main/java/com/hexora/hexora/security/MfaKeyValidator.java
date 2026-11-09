package com.hexora.hexora.security;

import java.util.Base64;

public final class MfaKeyValidator {

    private MfaKeyValidator() {
    }

    public static boolean isValid(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        try {
            return Base64.getDecoder().decode(key).length == 32;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
