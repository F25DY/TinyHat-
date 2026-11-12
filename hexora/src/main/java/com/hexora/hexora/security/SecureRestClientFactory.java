package com.hexora.hexora.security;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

public final class SecureRestClientFactory {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private SecureRestClientFactory() {
    }

    public static RestClient create(String baseUrl, String allowedHost, int maximumResponseBytes) {
        return create(baseUrl, allowedHost, maximumResponseBytes, false);
    }

    static RestClient createForLocalTest(String baseUrl, String allowedHost, int maximumResponseBytes) {
        return create(baseUrl, allowedHost, maximumResponseBytes, true);
    }

    private static RestClient create(
        String baseUrl,
        String allowedHost,
        int maximumResponseBytes,
        boolean allowLoopbackHttp
    ) {
        URI uri = URI.create(baseUrl);
        boolean secureScheme = "https".equalsIgnoreCase(uri.getScheme());
        boolean localTestScheme = allowLoopbackHttp
            && "http".equalsIgnoreCase(uri.getScheme())
            && ("localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost()));
        if ((!secureScheme && !localTestScheme)
            || !allowedHost.equalsIgnoreCase(uri.getHost())
            || uri.getUserInfo() != null
            || uri.getQuery() != null
            || uri.getFragment() != null
            || maximumResponseBytes < 1) {
            throw new IllegalArgumentException("Invalid outbound service configuration");
        }

        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return RestClient.builder()
            .baseUrl(uri.toString())
            .requestFactory(boundedResponseFactory(requestFactory, maximumResponseBytes))
            .build();
    }

    private static ClientHttpRequestFactory boundedResponseFactory(
        ClientHttpRequestFactory delegate,
        int maximumResponseBytes
    ) {
        return (uri, method) -> {
            ClientHttpRequest clientRequest = delegate.createRequest(uri, method);
            return new ClientHttpRequest() {
                @Override
                public HttpMethod getMethod() {
                    return clientRequest.getMethod();
                }

                @Override
                public URI getURI() {
                    return clientRequest.getURI();
                }

                @Override
                public Map<String, Object> getAttributes() {
                    return clientRequest.getAttributes();
                }

                @Override
                public HttpHeaders getHeaders() {
                    return clientRequest.getHeaders();
                }

                @Override
                public OutputStream getBody() throws IOException {
                    return clientRequest.getBody();
                }

                @Override
                public ClientHttpResponse execute() throws IOException {
                    return bounded(clientRequest.execute(), maximumResponseBytes);
                }
            };
        };
    }

    private static ClientHttpResponse bounded(ClientHttpResponse response, int maximumResponseBytes)
        throws IOException {
        try {
            byte[] body;
            try (InputStream input = response.getBody()) {
                body = input.readNBytes(maximumResponseBytes + 1);
            }
            if (body.length > maximumResponseBytes) {
                throw new IOException("Outbound response exceeded the configured size limit");
            }
            return new BoundedResponse(response, body);
        } catch (IOException | RuntimeException exception) {
            response.close();
            throw exception;
        }
    }

    private static final class BoundedResponse implements ClientHttpResponse {

        private final ClientHttpResponse delegate;
        private final byte[] body;

        private BoundedResponse(ClientHttpResponse delegate, byte[] body) {
            this.delegate = delegate;
            this.body = body;
        }

        @Override
        public HttpStatusCode getStatusCode() throws IOException {
            return delegate.getStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return delegate.getStatusText();
        }

        @Override
        public HttpHeaders getHeaders() {
            return delegate.getHeaders();
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(body);
        }

        @Override
        public void close() {
            delegate.close();
        }
    }
}
