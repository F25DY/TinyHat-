package com.hexora.network.service;

public record NetworkProbeResult(boolean open, int latencyMs) {
}
