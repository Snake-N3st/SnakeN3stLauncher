package mc.snakenest.launcher.bootstrap;

import com.sun.net.httpserver.HttpServer;
import mc.snakenest.launcher.auth.SignedParams;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Exercises LauncherReleaseClient against a throwaway local HTTP server, not the real site. */
class LauncherReleaseClientTest {

    private static final String JAR_CONTENT = "fake-jar-bytes-for-testing";
    private static final String JAR_HASH = Sha256.hex(JAR_CONTENT.getBytes(StandardCharsets.UTF_8));

    private static final String DEB_CONTENT = "fake-debian-package-bytes";
    private static final String DEB_HASH = Sha256.hex(DEB_CONTENT.getBytes(StandardCharsets.UTF_8));

    private HttpServer server;
    private LauncherReleaseClient client;
    private URI base;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());

        server.createContext("/api/launcher-auth/releases/launcher/latest", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            if (query == null || !query.contains("client_id=test-client")) {
                exchange.sendResponseHeaders(400, -1);
                exchange.close();
                return;
            }
            // Mirrors the real API: a "restricted" channel needs a signature (playerId/publicKey/
            // timestamp/signature all present) or it's a 401; any channel value gets a 200.
            if (query.contains("channel=restricted") && !query.contains("signature=")) {
                exchange.sendResponseHeaders(401, -1);
                exchange.close();
                return;
            }
            String json = """
                    {"version":"1.2.0","sha256":"%s","size":%d,"changelog":"test","download_url":"%s/api/launcher-auth/releases/launcher/1.2.0/download"}
                    """.formatted(JAR_HASH, JAR_CONTENT.length(), base);
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });

        server.createContext("/api/launcher-auth/releases/launcher/1.2.0/download", exchange -> {
            byte[] body = JAR_CONTENT.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });

        // The bootstrap endpoint, which is the only one that ever carries a "deb" block.
        server.createContext("/api/launcher-auth/releases/bootstrap/latest", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            // A channel is meaningless for a bootstrap release (always single-stream). The real API
            // ignores one; this refuses it outright, so a client that started sending one anyway
            // fails a test instead of quietly working until the server stops being so forgiving.
            boolean accepted = query != null && query.contains("client_id=test-client") && !query.contains("channel=");
            if (!accepted) {
                exchange.sendResponseHeaders(400, -1);
                exchange.close();
                return;
            }
            String json = """
                    {"version":"2.0.0","sha256":"%s","size":%d,"changelog":"test",\
                    "download_url":"%s/api/launcher-auth/releases/bootstrap/2.0.0/download",\
                    "deb":{"sha256":"%s","size":%d,"download_url":"%s/launcher-download/deb"}}
                    """.formatted(JAR_HASH, JAR_CONTENT.length(), base, DEB_HASH, DEB_CONTENT.length(), base);
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });

        server.createContext("/launcher-download/deb", exchange -> {
            byte[] body = DEB_CONTENT.getBytes(StandardCharsets.UTF_8);
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
        LauncherReleaseInfo release = client.fetchLatest(ReleaseType.LAUNCHER, null, null);

        assertEquals("1.2.0", release.version());
        assertEquals(JAR_HASH, release.sha256());
        assertEquals(JAR_CONTENT.length(), release.size());
    }

    @Test
    void downloadVerifiesAndWritesTheContent(@TempDir Path tempDir) throws Exception {
        LauncherReleaseInfo release = client.fetchLatest(ReleaseType.LAUNCHER, null, null);
        Path target = tempDir.resolve("launcher.jar");

        client.download(release, target, null);

        assertEquals(JAR_CONTENT, Files.readString(target));
    }

    @Test
    void fetchLatestParsesTheDebBlockOfABootstrapRelease() throws Exception {
        LauncherReleaseInfo release = client.fetchLatest(ReleaseType.BOOTSTRAP, null, null);

        assertNotNull(release.deb(), "the site sends a deb block for a bootstrap release");
        assertEquals(DEB_HASH, release.deb().sha256());
        assertEquals(DEB_CONTENT.length(), release.deb().size());
        assertEquals(base + "/launcher-download/deb", release.deb().downloadUrl());
    }

    /**
     * The site omits the block entirely when no package has been generated yet, and AptSelfUpdater
     * reads that as "nothing to install" - so it has to arrive as null rather than blow up parsing.
     */
    @Test
    void aReleaseWithoutADebBlockParsesWithANullOne() throws Exception {
        LauncherReleaseInfo release = client.fetchLatest(ReleaseType.LAUNCHER, null, null);

        assertNull(release.deb());
    }

    @Test
    void downloadDebVerifiesAndWritesThePackage(@TempDir Path tempDir) throws Exception {
        LauncherReleaseInfo release = client.fetchLatest(ReleaseType.BOOTSTRAP, null, null);
        Path target = tempDir.resolve("launcher.deb");

        client.downloadDeb(release.deb(), target);

        assertEquals(DEB_CONTENT, Files.readString(target));
    }

    /**
     * These bytes are about to be handed to a package manager running as root, so a mismatch has to
     * leave nothing behind for it to find.
     */
    @Test
    void downloadDebRejectsAMismatchedHashAndLeavesNoFile(@TempDir Path tempDir) {
        var tampered = new LauncherReleaseInfo.DebPackage("0".repeat(64), DEB_CONTENT.length(),
                base + "/launcher-download/deb");
        Path target = tempDir.resolve("launcher.deb");

        assertThrows(HashVerificationException.class, () -> client.downloadDeb(tampered, target));
        assertFalse(Files.exists(target));
    }

    @Test
    void downloadRejectsAMismatchedHashAndLeavesNoFile(@TempDir Path tempDir) throws Exception {
        LauncherReleaseInfo tamperedRelease = new LauncherReleaseInfo("1.2.0", "0".repeat(64), JAR_CONTENT.length(),
                "test", base + "/api/launcher-auth/releases/launcher/1.2.0/download", null);
        Path target = tempDir.resolve("launcher.jar");

        assertThrows(HashVerificationException.class, () -> client.download(tamperedRelease, target, null));
        assertFalse(Files.exists(target));
    }

    @Test
    void fetchLatestSendsTheChannelParamForALauncherRelease() throws Exception {
        LauncherReleaseInfo release = client.fetchLatest(ReleaseType.LAUNCHER, "beta", null);

        // The test server's own handler doesn't branch on the channel value itself (only
        // "restricted" triggers the 401 below) - this just confirms a normal channel request
        // still round-trips a real response rather than getting lost/malformed on the way out.
        assertEquals("1.2.0", release.version());
    }

    @Test
    void fetchLatestOmitsTheChannelParamForABootstrapRelease() throws Exception {
        // A channel is meaningless for a bootstrap release (always single-stream) - passing one
        // anyway must not leak into the request, or the test server answers 400 and this fails.
        LauncherReleaseInfo release = client.fetchLatest(ReleaseType.BOOTSTRAP, "beta", null);

        assertEquals("2.0.0", release.version());
    }

    @Test
    void fetchLatestThrowsRestrictedChannelExceptionOnAnUnauthenticated401() {
        assertThrows(RestrictedChannelException.class, () -> client.fetchLatest(ReleaseType.LAUNCHER, "restricted", null));
    }

    @Test
    void fetchLatestSucceedsOnARestrictedChannelOnceSigned() throws Exception {
        SignedParams signed = new SignedParams(1L, "aa".repeat(32), 1234567890L, "bb".repeat(64), "test-client");

        LauncherReleaseInfo release = client.fetchLatest(ReleaseType.LAUNCHER, "restricted", signed);

        assertEquals("1.2.0", release.version());
    }

    @Test
    void downloadThrowsRestrictedChannelExceptionOn403(@TempDir Path tempDir) {
        server.createContext("/api/launcher-auth/releases/launcher/restricted-version/download", exchange -> {
            exchange.sendResponseHeaders(403, -1);
            exchange.close();
        });
        LauncherReleaseInfo restrictedRelease = new LauncherReleaseInfo("restricted-version", JAR_HASH, JAR_CONTENT.length(),
                "test", base + "/api/launcher-auth/releases/launcher/restricted-version/download", null);

        assertThrows(RestrictedChannelException.class,
                () -> client.download(restrictedRelease, tempDir.resolve("launcher.jar"), null));
    }
}
