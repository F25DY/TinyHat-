package com.hexora.network.service;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hexora.network")
public class NetworkMonitorProperties {

    private List<AllowedHost> allowedHosts = new ArrayList<>();
    private List<Integer> defaultPorts = new ArrayList<>(List.of(21, 22, 25, 53, 80, 110, 143, 443, 3306, 5432));
    private int connectTimeoutMillis = 500;
    private long monitorIntervalMs = 60_000;
    private long discoveryIntervalMs = 30_000;
    private boolean discoveryEnabled = true;
    private int historyRetentionDays = 90;

    public List<AllowedHost> getAllowedHosts() {
        return allowedHosts;
    }

    public void setAllowedHosts(List<AllowedHost> allowedHosts) {
        this.allowedHosts = allowedHosts;
    }

    public List<Integer> getDefaultPorts() {
        return defaultPorts;
    }

    public void setDefaultPorts(List<Integer> defaultPorts) {
        this.defaultPorts = defaultPorts;
    }

    public int getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public void setConnectTimeoutMillis(int connectTimeoutMillis) {
        this.connectTimeoutMillis = connectTimeoutMillis;
    }

    public long getMonitorIntervalMs() {
        return monitorIntervalMs;
    }

    public void setMonitorIntervalMs(long monitorIntervalMs) {
        this.monitorIntervalMs = monitorIntervalMs;
    }

    public long getDiscoveryIntervalMs() {
        return discoveryIntervalMs;
    }

    public void setDiscoveryIntervalMs(long discoveryIntervalMs) {
        this.discoveryIntervalMs = discoveryIntervalMs;
    }

    public boolean isDiscoveryEnabled() {
        return discoveryEnabled;
    }

    public void setDiscoveryEnabled(boolean discoveryEnabled) {
        this.discoveryEnabled = discoveryEnabled;
    }

    public int getHistoryRetentionDays() {
        return historyRetentionDays;
    }

    public void setHistoryRetentionDays(int historyRetentionDays) {
        this.historyRetentionDays = historyRetentionDays;
    }

    public static class AllowedHost {

        private String name;
        private String address;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }
    }
}
