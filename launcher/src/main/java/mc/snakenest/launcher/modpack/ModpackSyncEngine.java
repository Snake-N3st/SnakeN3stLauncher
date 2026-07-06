package mc.snakenest.launcher.modpack;

import mc.snakenest.launcher.auth.PlayerSession;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Orchestrates a full sync of one modpack instance directory: diff against
 * the last-applied manifest ({@link ManifestDiffer}), download/verify
 * whatever changed ({@link ModpackFileDownloader}), delete whatever's gone,
 * then record the new manifest as applied. Everything here does real I/O -
 * run off the UI thread.
 */
public final class ModpackSyncEngine {

    private final ModpackFileDownloader downloader;
    private final LocalManifestStore manifestStore;

    public ModpackSyncEngine(ModpackFileDownloader downloader, LocalManifestStore manifestStore) {
        this.downloader = downloader;
        this.manifestStore = manifestStore;
    }

    public void sync(String slug, Path instanceDir, ModpackManifest targetManifest, PlayerSession session, SyncProgressListener listener)
            throws IOException, InterruptedException, ModpackApiException {
        Files.createDirectories(instanceDir);

        Optional<StoredManifest> previous = manifestStore.load(instanceDir);
        SyncPlan plan = ManifestDiffer.diff(previous, targetManifest);

        for (ManifestFile file : plan.toDownload()) {
            listener.onFileStarted(file.path(), file.size());
            downloader.download(slug, file, instanceDir, session);
            listener.onFileDone(file.path());
        }

        for (String path : plan.toDelete()) {
            deleteAndPruneEmptyParents(instanceDir, path);
            listener.onDeleted(path);
        }

        manifestStore.save(instanceDir, StoredManifest.of(targetManifest));
    }

    private void deleteAndPruneEmptyParents(Path instanceDir, String relativePath) throws IOException {
        Path file = InstancePaths.resolveSafely(instanceDir, relativePath);
        Files.deleteIfExists(file);

        Path normalizedRoot = instanceDir.normalize();
        Path parent = file.getParent();
        while (parent != null && !parent.equals(normalizedRoot) && parent.startsWith(normalizedRoot)) {
            try (var entries = Files.list(parent)) {
                if (entries.findAny().isPresent()) {
                    break;
                }
            }
            Files.delete(parent);
            parent = parent.getParent();
        }
    }
}
