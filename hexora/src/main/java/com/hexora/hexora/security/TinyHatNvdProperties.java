package com.hexora.hexora.security;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "tinyhat.nvd")
public class TinyHatNvdProperties {

    private String apiKey = "";
    private String baseUrl = "https://services.nvd.nist.gov/rest/json/cves/2.0";

    @Min(1)
    private int requestsWithoutKey = 4;

    @Min(1)
    private int requestsWithKey = 45;

    @Min(1)
    private int windowSeconds = 30;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public int getRequestsWithoutKey() {
        return requestsWithoutKey;
    }

    public void setRequestsWithoutKey(int requestsWithoutKey) {
        this.requestsWithoutKey = requestsWithoutKey;
    }

    public int getRequestsWithKey() {
        return requestsWithKey;
    }

    public void setRequestsWithKey(int requestsWithKey) {
        this.requestsWithKey = requestsWithKey;
    }

    public int getWindowSeconds() {
        return windowSeconds;
    }

    public void setWindowSeconds(int windowSeconds) {
        this.windowSeconds = windowSeconds;
    }
}
