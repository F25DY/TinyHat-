package com.hexora.network.service;

public class NetworkCapabilityDisabledException extends RuntimeException {

    public NetworkCapabilityDisabledException() {
        super("The network module is not enabled for this account");
    }
}
