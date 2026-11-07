package com.hexora.network.controller;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class NetworkMethodNotAllowedFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkMethodNotAllowedFilter.class);
    private final ObjectMapper objectMapper;

    public NetworkMethodNotAllowedFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        if (!request.getRequestURI().startsWith(request.getContextPath() + "/api/network/")) {
            filterChain.doFilter(request, response);
            return;
        }

        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        filterChain.doFilter(request, wrappedResponse);
        if (wrappedResponse.getStatus() != HttpServletResponse.SC_METHOD_NOT_ALLOWED) {
            wrappedResponse.copyBodyToResponse();
            return;
        }

        String allowedMethods = wrappedResponse.getHeader("Allow");
        if (allowedMethods == null || allowedMethods.isBlank()) {
            allowedMethods = "Consultar la documentación del endpoint";
        }
        LOGGER.error(
            "Network endpoint {} expected HTTP method(s) [{}], received {}",
            request.getRequestURI(),
            allowedMethods,
            request.getMethod()
        );
        wrappedResponse.resetBuffer();
        wrappedResponse.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        wrappedResponse.setContentType("application/json");
        wrappedResponse.setCharacterEncoding("UTF-8");
        try {
            objectMapper.writeValue(wrappedResponse.getOutputStream(), Map.of(
                "error", "HTTP method not supported for this network endpoint",
                "path", request.getRequestURI(),
                "receivedMethod", request.getMethod(),
                "allowedMethods", allowedMethods
            ));
        } catch (JacksonException exception) {
            LOGGER.error("Could not serialize the 405 diagnostic for {}", request.getRequestURI(), exception);
            throw exception;
        }
        wrappedResponse.copyBodyToResponse();
    }
}
