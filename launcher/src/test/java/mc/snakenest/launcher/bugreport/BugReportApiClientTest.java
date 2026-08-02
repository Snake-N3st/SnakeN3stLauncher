package mc.snakenest.launcher.bugreport;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import mc.snakenest.launcher.net.HttpJsonClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BugReportApiClientTest {

    private HttpServer server;
    private AtomicReference<String> lastRequestBody;
    private AtomicReference<Integer> responseStatus;
    private BugReportApiClient client;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        lastRequestBody = new AtomicReference<>();
        responseStatus = new AtomicReference<>(201);

        server.createContext("/api/launcher-auth/bug-reports", exchange -> {
            byte[] requestBytes = exchange.getRequestBody().readAllBytes();
            lastRequestBody.set(new String(requestBytes, StandardCharsets.UTF_8));
            byte[] body = "{\"message\":\"Report received\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(responseStatus.get(), body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });

        server.start();
        URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        client = new BugReportApiClient(new HttpJsonClient(), base, "test-client-id");
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void submitSendsEveryField() {
        boolean ok = client.submit(BugReportType.MANUAL, "Le jeu plante", "Ca arrive en minant",
                "stack trace ici", "aventure-ultime");

        assertTrue(ok);
        JsonObject sent = new Gson().fromJson(lastRequestBody.get(), JsonObject.class);
        assertEquals("test-client-id", sent.get("client_id").getAsString());
        assertEquals("manual", sent.get("type").getAsString());
        assertEquals("Le jeu plante", sent.get("summary").getAsString());
        assertEquals("Ca arrive en minant", sent.get("description").getAsString());
        assertEquals("stack trace ici", sent.get("details").getAsString());
        assertEquals("aventure-ultime", sent.get("modpack_slug").getAsString());
        assertTrue(sent.has("launcher_version"));
        assertTrue(sent.has("system_info"));
    }

    @Test
    void submitOmitsBlankOptionalFields() {
        client.submit(BugReportType.LAUNCHER_CRASH, "Erreur au demarrage", null, null, null);

        JsonObject sent = new Gson().fromJson(lastRequestBody.get(), JsonObject.class);
        assertFalse(sent.has("description"));
        assertFalse(sent.has("details"));
        assertFalse(sent.has("modpack_slug"));
    }

    @Test
    void submitTruncatesFieldsToTheServersColumnSizes() {
        client.submit(BugReportType.MANUAL, "x".repeat(300), null, null, null);

        JsonObject sent = new Gson().fromJson(lastRequestBody.get(), JsonObject.class);
        assertEquals(255, sent.get("summary").getAsString().length());
    }

    @Test
    void submitReturnsFalseOnServerRejection() {
        responseStatus.set(422);

        boolean ok = client.submit(BugReportType.MANUAL, "resume", null, null, null);

        assertFalse(ok);
    }

    @Test
    void submitReturnsFalseRatherThanThrowingWhenTheServerIsUnreachable() {
        server.stop(0);
        BugReportApiClient unreachable = new BugReportApiClient(new HttpJsonClient(),
                URI.create("http://127.0.0.1:1"), "test-client-id");

        boolean ok = unreachable.submit(BugReportType.GAME_CRASH, "resume", null, null, null);

        assertFalse(ok);
    }
}
