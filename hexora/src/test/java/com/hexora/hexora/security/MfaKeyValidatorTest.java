package com.hexora.hexora.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MfaKeyValidatorTest {

    @Test
    void acceptsOnlyBase64Encoded256BitKeys() {
        assertTrue(MfaKeyValidator.isValid("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="));
        assertFalse(MfaKeyValidator.isValid(""));
        assertFalse(MfaKeyValidator.isValid("not-base64"));
        assertFalse(MfaKeyValidator.isValid("YWJj"));
    }
}
