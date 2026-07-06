package mc.snakenest.launcher.bootstrap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import mc.snakenest.launcher.util.AtomicFiles;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Talks to the site's {@code launcher-auth} plugin release endpoints (see
 * {@code LAUNCHER_INTEGRATION.md} section 8). Deliberately independent of
 * {@code launcher.net.HttpJsonClient} - this module must stay free of any
 * dependency on the {@code launcher} module (which embeds GPL libraries),
 * so it talks HTTP directly rather than sharing that client.
 */
final class LauncherReleaseClient {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final URI baseUrl;
    private final String clientId;

    LauncherReleaseClient(URI baseUrl, String clientId) {
        this.baseUrl = baseUrl;
        this.clientId = clientId;
    }

    LauncherReleaseInfo fetchLatest() throws IOException, InterruptedException, BootstrapException {
        URI uri = baseUrl.resolve("/api/launcher-auth/releases/latest?client_id=" + encode(clientId));
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(TIMEOUT).header("Accept", "application/json").GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new BootstrapException("Could not fetch the latest launcher release (status " + response.statusCode() + ")");
        }
        return GSON.fromJson(response.body(), LauncherReleaseInfo.class);
    }

    /** Streams the release jar to {@code target}, verifying its SHA-256 before it's ever committed there. */
    void download(LauncherReleaseInfo release, Path target) throws IOException, InterruptedException, BootstrapException {
        URI uri = URI.create(release.downloadUrl() + (release.downloadUrl().contains("?") ? "&" : "?") + "client_id=" + encode(clientId));
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(TIMEOUT).GET().build();
        HttpResponse<java.io.InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            throw new BootstrapException("Could not download launcher release " + release.version() + " (status " + response.statusCode() + ")");
        }
        AtomicFiles.writeVerified(target, response.body(), release.sha256());
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
