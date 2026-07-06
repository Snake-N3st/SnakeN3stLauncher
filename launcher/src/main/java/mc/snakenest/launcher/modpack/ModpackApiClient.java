package mc.snakenest.launcher.modpack;

import mc.snakenest.launcher.auth.PlayerSession;
import mc.snakenest.launcher.auth.SignedParams;
import mc.snakenest.launcher.auth.SignedRequestSigner;
import mc.snakenest.launcher.net.HttpJsonClient;
import mc.snakenest.launcher.net.JsonResponse;
import mc.snakenest.launcher.net.RawResponse;
import mc.snakenest.launcher.net.Uris;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Client for the {@code modpacks} plugin's endpoints. Every call requires
 * both proof of possession of the player's key (same signature recipe as
 * {@code auth.SignedRequestSigner}) <b>and</b> the launcher's own
 * {@code client_id} - see {@code LAUNCHER_INTEGRATION.md} section 7.
 */
public final class ModpackApiClient {

    private final HttpJsonClient http;
    private final URI baseUrl;

    public ModpackApiClient(HttpJsonClient http, URI baseUrl) {
        this.http = http;
        this.baseUrl = baseUrl;
    }

    public List<ModpackSummary> listModpacks(PlayerSession session) throws IOException, InterruptedException, ModpackApiException {
        JsonResponse response = get("/api/modpacks", Map.of(), session);
        requireSuccess(response, "listing modpacks");
        return response.as(ModpackListResponse.class).modpacks();
    }

    /** @param version exact version string, or {@code null} for the latest one */
    public ModpackManifest getManifest(String slug, String version, PlayerSession session)
            throws IOException, InterruptedException, ModpackApiException {
        Map<String, String> extra = version != null ? Map.of("version", version) : Map.of();
        JsonResponse response = get("/api/modpacks/" + slug + "/manifest", extra, session);
        requireSuccess(response, "fetching the manifest of " + slug);
        return response.as(ModpackManifest.class);
    }

    public List<ModpackVersionSummary> listVersions(String slug, PlayerSession session)
            throws IOException, InterruptedException, ModpackApiException {
        JsonResponse response = get("/api/modpacks/" + slug + "/versions", Map.of(), session);
        requireSuccess(response, "listing versions of " + slug);
        return response.as(VersionListResponse.class).versions();
    }

    /**
     * Streams the raw content of one blob. The caller must check
     * {@link RawResponse#isSuccess()} and close the response.
     */
    public RawResponse downloadBlob(String slug, String hash, PlayerSession session) throws IOException, InterruptedException {
        SignedParams signed = SignedRequestSigner.sign(session);
        URI uri = Uris.withQuery(baseUrl.resolve("/api/modpacks/" + slug + "/blob/" + hash), signed.toQueryParams());
        return http.getRaw(uri);
    }

    private JsonResponse get(String path, Map<String, String> extraParams, PlayerSession session)
            throws IOException, InterruptedException {
        SignedParams signed = SignedRequestSigner.sign(session);
        Map<String, String> params = signed.toQueryParams();
        params.putAll(extraParams);
        URI uri = Uris.withQuery(baseUrl.resolve(path), params);
        return http.get(uri);
    }

    private void requireSuccess(JsonResponse response, String action) throws ModpackApiException {
        if (response.isSuccess()) {
            return;
        }
        switch (response.statusCode()) {
            case 403 -> throw new ModpackAccessDeniedException("Access denied " + action);
            case 404 -> throw new ModpackNotFoundException("Not found: " + action);
            default -> throw new ModpackApiException("Unexpected status " + response.statusCode() + " " + action);
        }
    }

    private record ModpackListResponse(List<ModpackSummary> modpacks) {
    }

    private record VersionListResponse(List<ModpackVersionSummary> versions) {
    }
}
