package mc.snakenest.launcher.modpack;

import mc.snakenest.launcher.auth.PlayerSession;
import mc.snakenest.launcher.net.RawResponse;
import mc.snakenest.launcher.util.AtomicFiles;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Downloads one modpack file, verifying its content before it's ever
 * written at its final path: streamed to a temp file while hashing, then
 * only moved into place if the SHA-256 matches what the manifest promised
 * (protects against a truncated or corrupted transfer - the doc explicitly
 * calls this out as mandatory, not optional).
 */
public final class ModpackFileDownloader {

    private final ModpackApiClient api;

    public ModpackFileDownloader(ModpackApiClient api) {
        this.api = api;
    }

    public void download(String slug, ManifestFile file, Path instanceDir, PlayerSession session)
            throws IOException, InterruptedException, ModpackApiException {
        Path target = InstancePaths.resolveSafely(instanceDir, file.path());

        try (RawResponse response = api.downloadBlob(slug, file.hash(), session)) {
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
}
