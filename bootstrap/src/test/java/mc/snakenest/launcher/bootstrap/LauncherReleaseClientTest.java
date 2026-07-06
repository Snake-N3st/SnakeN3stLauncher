package mc.snakenest.launcher.bootstrap;

import com.sun.net.httpserver.HttpServer;
import mc.snakenest.launcher.util.HashVerificationException;
import mc.snakenest.launcher.util.Sha256;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Exercises LauncherReleaseClient against a throwaway local HTTP server, not the real site. */
class LauncherReleaseClientTest {

    private static final String JAR_CONTENT = "fake-jar-bytes-for-testing";
    private static final String JAR_HASH = Sha256.hex(JAR_CONTENT.getBytes(StandardCharsets.UTF_8));

    private HttpServer server;
    private LauncherReleaseClient client;
    private URI base;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());

        server.createContext("/api/launcher-auth/releases/latest", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            if (query == null || !query.contains("client_id=test-client")) {
                exchange.sendResponseHeaders(400, -1);
                exchange.close();
                return;
            }
            String json = """
                    {"version":"1.2.0","sha256":"%s","size":%d,"changelog":"test","download_url":"%s/api/launcher-auth/releases/1.2.0/download"}
                    """.formatted(JAR_HASH, JAR_CONTENT.length(), base);
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });

        server.createContext("/api/launcher-auth/releases/1.2.0/download", exchange -> {
            byte[] body = JAR_CONTENT.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });

        server.start();
        client = new LauncherReleaseClient(base, "test-client");
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void fetchLatestParsesTheResponse() throws Exception {
        LauncherReleaseInfo release = client.fetchLatest();

        assertEquals("1.2.0", release.version());
        assertEquals(JAR_HASH, release.sha256());
        assertEquals(JAR_CONTENT.length(), release.size());
    }

    @Test
    void downloadVerifiesAndWritesTheContent(@TempDir Path tempDir) throws Exception {
        LauncherReleaseInfo release = client.fetchLatest();
        Path target = tempDir.resolve("launcher.jar");

        client.download(release, target);

        assertEquals(JAR_CONTENT, Files.readString(target));
    }

    @Test
    void downloadRejectsAMismatchedHashAndLeavesNoFile(@TempDir Path tempDir) throws Exception {
        LauncherReleaseInfo tamperedRelease = new LauncherReleaseInfo("1.2.0", "0".repeat(64), JAR_CONTENT.length(),
                "test", base + "/api/launcher-auth/releases/1.2.0/download");
        Path target = tempDir.resolve("launcher.jar");

        assertThrows(HashVerificationException.class, () -> client.download(tamperedRelease, target));
        assertFalse(Files.exists(target));
    }
}
