package com.hexora.hexora.security;

public class ScopedNotFoundException extends RuntimeException {

    public ScopedNotFoundException() {
        super("Resource not found");
    }
}
