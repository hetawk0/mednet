package com.mednet.auth.email;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EkdSendEmailServiceTests {

    private HttpServer server;
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> apiKeyHeader = new AtomicReference<>();
    private final AtomicReference<String> authorizationHeader = new AtomicReference<>();
    private final AtomicReference<Integer> responseStatus = new AtomicReference<>(200);
    private final AtomicReference<String> responseBody =
            new AtomicReference<>("{\"success\":true,\"messageId\":\"test-message\"}");

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/send", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            apiKeyHeader.set(exchange.getRequestHeaders().getFirst("x-api-key"));
            authorizationHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = responseBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus.get(), body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void sendsEkdSendCompatiblePayloadWithBothSupportedAuthenticationHeaders() {
        EkdSendEmailService service = service("configured@example.test");

        boolean sent = service.send(
                "patient@example.test",
                "Verify your MedNet email",
                "<p>Verify your email</p>",
                "Verify your email");

        assertThat(sent).isTrue();
        assertThat(apiKeyHeader.get()).isEqualTo("test-api-key");
        assertThat(authorizationHeader.get()).isEqualTo("Bearer test-api-key");
        assertThat(requestBody.get())
                .contains("\"to\":\"patient@example.test\"")
                .contains("\"from\":\"configured@example.test\"")
                .contains("\"html\":\"<p>Verify your email</p>\"")
                .contains("\"body\":\"<p>Verify your email</p>\"");
    }

    @Test
    void usesTheKnownSenderFallbackWhenNoFromAddressIsConfigured() {
        EkdSendEmailService service = service(" ");

        assertThat(service.send("patient@example.test", "subject", "<p>hello</p>", "hello")).isTrue();
        assertThat(requestBody.get()).contains("\"from\":\"support@ekddigital.com\"");
    }

    @Test
    void reportsProviderRejectionAsDeliveryFailure() {
        responseStatus.set(401);
        EkdSendEmailService service = service("configured@example.test");

        assertThat(service.send("patient@example.test", "subject", "<p>hello</p>", "hello"))
                .isFalse();
    }

    @Test
    void reportsProviderApplicationFailureEvenWhenHttpStatusIsSuccessful() {
        responseBody.set("{\"success\":false,\"message\":\"sender rejected\"}");
        EkdSendEmailService service = service("configured@example.test");

        assertThat(service.send("patient@example.test", "subject", "<p>hello</p>", "hello"))
                .isFalse();
    }

    private EkdSendEmailService service(String from) {
        return new EkdSendEmailService(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1/",
                "test-api-key",
                from);
    }
}
