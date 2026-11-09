package com.hexora.hexora.security;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class PasswordPolicyValidator {

    private static final Set<String> COMMON_PASSWORDS = Set.of(
        "123456789012",
        "1234567890123",
        "password1234",
        "password12345",
        "administrator",
        "admin1234567",
        "qwertyuiopas",
        "qwertyuiopasd",
        "welcome12345",
        "welcome123456",
        "contrasena12",
        "contrasena123",
        "iloveyou1234",
        "supersecret12",
        "changeme1234",
        "changeme12345",
        "letmein12345",
        "monkey123456",
        "dragon123456",
        "sunshine1234",
        "princess1234",
        "football1234",
        "baseball1234",
        "trustno11234",
        "master123456",
        "secret123456",
        "starwars1234",
        "whatever1234",
        "shadow123456",
        "default12345"
    );

    private final TinyHatSecurityProperties properties;

    public PasswordPolicyValidator(TinyHatSecurityProperties properties) {
        this.properties = properties;
    }

    public void validate(String password, String email) {
        if (password == null) {
            throw new PasswordPolicyViolationException("La contraseña es obligatoria");
        }
        int minLength = properties.getPassword().getMinLength();
        int maxBytes = properties.getPassword().getMaxBytes();

        if (password.length() < minLength) {
            throw new PasswordPolicyViolationException(
                "La contraseña debe tener al menos " + minLength + " caracteres"
            );
        }

        byte[] utf8Bytes = password.getBytes(StandardCharsets.UTF_8);
        if (utf8Bytes.length > maxBytes) {
            throw new PasswordPolicyViolationException(
                "La contraseña no puede exceder " + maxBytes + " bytes UTF-8"
            );
        }

        String normalizedPassword = password.toLowerCase(Locale.ROOT).trim();
        if (COMMON_PASSWORDS.contains(normalizedPassword)) {
            throw new PasswordPolicyViolationException(
                "La contraseña elegida es demasiado común y no está permitida"
            );
        }

        if (email != null && !email.isBlank()) {
            String normalizedEmail = email.toLowerCase(Locale.ROOT).trim();
            if (normalizedPassword.equals(normalizedEmail)) {
                throw new PasswordPolicyViolationException(
                    "La contraseña no puede ser igual al correo electrónico"
                );
            }
            int atIndex = normalizedEmail.indexOf('@');
            if (atIndex > 0) {
                String localPart = normalizedEmail.substring(0, atIndex);
                if (localPart.length() >= minLength && normalizedPassword.equals(localPart)) {
                    throw new PasswordPolicyViolationException(
                        "La contraseña no puede ser igual a tu nombre de usuario"
                    );
                }
            }
        }
    }
}

