package mc.snakenest.launcher.net;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises HttpJsonClient against a throwaway local HTTP server rather than
 * a real Azuriom instance, so this test is deterministic and doesn't depend
 * on any particular machine's dev environment being up.
 */
class HttpJsonClientTest {

    private HttpServer server;
    private HttpJsonClient client;

    record Ping(String message) {}

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

        server.createContext("/echo-get", exchange -> {
            byte[] body = "{\"message\":\"hi\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });

        server.createContext("/not-found", exchange -> {
            byte[] body = "{\"message\":\"Unknown\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(404, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });

        server.createContext("/echo-post", exchange -> {
            byte[] received = exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(200, received.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(received);
            }
        });

        server.createContext("/raw", exchange -> {
            byte[] body = "raw-bytes".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });

        server.start();
        client = new HttpJsonClient();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
    }

    @Test
    void getParsesAJsonBody() throws Exception {
        JsonResponse response = client.get(uri("/echo-get"));

        assertTrue(response.isSuccess());
        assertEquals(200, response.statusCode());
        assertEquals("hi", response.as(Ping.class).message());
    }

    @Test
    void nonSuccessStatusIsStillParseable() throws Exception {
        JsonResponse response = client.get(uri("/not-found"));

        assertEquals(404, response.statusCode());
        assertFalse(response.isSuccess());
        assertEquals("Unknown", response.as(Ping.class).message());
    }

    @Test
    void postJsonSendsASerializedBody() throws Exception {
        JsonResponse response = client.postJson(uri("/echo-post"), new Ping("sent"));

        assertEquals("sent", response.as(Ping.class).message());
    }

    @Test
    void getRawReturnsTheStreamedBytes() throws Exception {
        try (RawResponse response = client.getRaw(uri("/raw"))) {
            assertTrue(response.isSuccess());
            byte[] bytes = response.body().readAllBytes();
            assertEquals("raw-bytes", new String(bytes, StandardCharsets.UTF_8));
        }
    }
}
