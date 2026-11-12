package com.hexora.network.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hexora.network.service.ScanRateLimitException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

class NetworkApiExceptionHandlerTest {

    @Test
    void respondsWithTooManyRequestsWhenAProjectScanLockIsBusy() {
        NetworkApiExceptionHandler handler = new NetworkApiExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/network/scan");

        var response = handler.scanRateLimited(new ScanRateLimitException("scan in progress"), request);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
    }
}
