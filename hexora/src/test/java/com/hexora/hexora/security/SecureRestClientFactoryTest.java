package com.hexora.hexora.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;

class SecureRestClientFactoryTest {

    @Test
    void doesNotFollowRedirectsToAnotherHost() throws IOException {
        AtomicInteger redirectedRequests = new AtomicInteger();
        HttpServer destination = server(exchange -> {
            redirectedRequests.incrementAndGet();
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        HttpServer origin = server(exchange -> {
            exchange.getResponseHeaders().set(
                "Location",
                "http://127.0.0.1:" + destination.getAddress().getPort() + "/redirect-target");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        destination.start();
        origin.start();
        try {
            var client = SecureRestClientFactory.createForLocalTest(
                "http://127.0.0.1:" + origin.getAddress().getPort(),
                "127.0.0.1",
                1024);

            var response = client.get()
                .uri("/")
                .retrieve()
                .toBodilessEntity();

            assertEquals(302, response.getStatusCode().value());
            assertEquals(0, redirectedRequests.get());
        } finally {
            origin.stop(0);
            destination.stop(0);
        }
    }

    @Test
    void rejectsResponsesAboveConfiguredByteLimit() throws IOException {
        HttpServer origin = server(exchange -> {
            byte[] body = "x".repeat(129).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        origin.start();
        try {
            var client = SecureRestClientFactory.createForLocalTest(
                "http://127.0.0.1:" + origin.getAddress().getPort(),
                "127.0.0.1",
                128);

            assertThrows(RestClientException.class, () -> client.get()
                .uri("/")
                .retrieve()
                .body(String.class));
        } finally {
            origin.stop(0);
        }
    }

    private static HttpServer server(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", handler);
        return server;
    }
}
