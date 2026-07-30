package mc.snakenest.launcher.modpack;

import mc.snakenest.launcher.auth.PlayerSession;
import mc.snakenest.launcher.net.HttpJsonClient;
import mc.snakenest.launcher.net.RawResponse;
import mc.snakenest.launcher.util.AtomicFiles;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;

/**
 * Downloads one modpack file, verifying its content before it's ever
 * written at its final path: streamed to a temp file while hashing, then
 * only moved into place if the SHA-256 matches what the manifest promised
 * (protects against a truncated or corrupted transfer - the doc explicitly
 * calls this out as mandatory, not optional). {@link ManifestFile#url()},
 * when set, redirects the fetch to a third-party origin server instead of
 * this site's own blob storage - the hash is still this site's own (computed
 * once at import time), so the same verification applies either way.
 */
public final class ModpackFileDownloader {

    private final ModpackApiClient api;
    private final HttpJsonClient http;

    public ModpackFileDownloader(ModpackApiClient api, HttpJsonClient http) {
        this.api = api;
        this.http = http;
    }

    public void download(String slug, ManifestFile file, Path instanceDir, PlayerSession session)
            throws IOException, InterruptedException, ModpackApiException {
        Path target = InstancePaths.resolveSafely(instanceDir, file.path());

        try (RawResponse response = fetch(slug, file, session)) {
            if (response.statusCode() == 404) {
                throw new ModpackNotFoundException("Unknown blob " + file.hash() + " for " + slug);
            }
            if (response.statusCode() == 403) {
                throw new ModpackAccessDeniedException("Access denied downloading " + file.path());
            }
            if (!response.isSuccess()) {
                throw new ModpackApiException("Unexpected status " + response.statusCode() + " downloading " + file.path());
            }

            AtomicFiles.writeVerified(target, response.body(), file.hash());
        }
    }

    private RawResponse fetch(String slug, ManifestFile file, PlayerSession session) throws IOException, InterruptedException {
        if (file.url() != null) {
            return http.getRaw(URI.create(file.url()));
        }
        return api.downloadBlob(slug, file.hash(), session);
    }
}
