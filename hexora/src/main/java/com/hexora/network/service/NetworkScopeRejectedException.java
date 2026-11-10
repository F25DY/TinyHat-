package com.hexora.network.service;

public class NetworkScopeRejectedException extends RuntimeException {

    public NetworkScopeRejectedException(String message) {
        super(message);
    }

    public NetworkScopeRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
