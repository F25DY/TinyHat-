package com.hexora.hexora.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class PasswordPolicyViolationException extends IllegalArgumentException {

    public PasswordPolicyViolationException(String message) {
        super(message);
    }
}

