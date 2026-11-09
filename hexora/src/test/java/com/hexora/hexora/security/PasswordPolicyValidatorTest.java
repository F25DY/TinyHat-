package com.hexora.hexora.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PasswordPolicyValidatorTest {

    private PasswordPolicyValidator validator;

    @BeforeEach
    void setUp() {
        TinyHatSecurityProperties properties = new TinyHatSecurityProperties();
        validator = new PasswordPolicyValidator(properties);
    }

    @Test
    void acceptsValidPassword() {
        assertThatCode(() -> validator.validate("CorrectHorseBatteryStaple", "auditor@example.com"))
            .doesNotThrowAnyException();
    }

    @Test
    void rejectsPasswordShorterThan12Characters() {
        assertThatThrownBy(() -> validator.validate("Short12345", "auditor@example.com"))
            .isInstanceOf(PasswordPolicyViolationException.class)
            .hasMessageContaining("al menos 12 caracteres");
    }

    @Test
    void rejectsPasswordExceeding72Bytes() {
        // 73 ASCII characters = 73 bytes
        String seventyThreeBytes = "A".repeat(73);
        assertThat(seventyThreeBytes.getBytes(StandardCharsets.UTF_8).length).isEqualTo(73);

        assertThatThrownBy(() -> validator.validate(seventyThreeBytes, "auditor@example.com"))
            .isInstanceOf(PasswordPolicyViolationException.class)
            .hasMessageContaining("72 bytes");
    }

    @Test
    void rejectsCommonPassword() {
        assertThatThrownBy(() -> validator.validate("password1234", "auditor@example.com"))
            .isInstanceOf(PasswordPolicyViolationException.class)
            .hasMessageContaining("demasiado común");
    }

    @Test
    void rejectsPasswordIdenticalToEmail() {
        assertThatThrownBy(() -> validator.validate("myemail123456@test.com", "myemail123456@test.com"))
            .isInstanceOf(PasswordPolicyViolationException.class)
            .hasMessageContaining("igual al correo");
    }
}
