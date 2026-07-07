package mc.snakenest.launcher.modpack;

import mc.snakenest.launcher.auth.PlayerSession;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Orchestrates a full sync of one modpack instance directory: diff against
 * the last-applied manifest ({@link ManifestDiffer}), download/verify
 * whatever changed ({@link ModpackFileDownloader}), delete whatever's gone,
 * then record the new manifest as applied. Everything here does real I/O -
 * run off the UI thread.
 */
public final class ModpackSyncEngine {

    // A modpack can be dozens/hundreds of small files - downloading them one at a time was the
    // main reason a sync felt slow, since each file paid a full round-trip before the next
    // started. Bounded (not "as many as there are files") so one huge modpack doesn't open
    // hundreds of sockets at once.
    private static final int MAX_PARALLEL_DOWNLOADS = 6;

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

        downloadAllInParallel(slug, instanceDir, plan.toDownload(), session, listener);

        for (String path : plan.toDelete()) {
            deleteAndPruneEmptyParents(instanceDir, path);
            listener.onDeleted(path);
        }

        manifestStore.save(instanceDir, StoredManifest.of(targetManifest));
    }

    private void downloadAllInParallel(String slug, Path instanceDir, List<ManifestFile> files, PlayerSession session, SyncProgressListener listener)
            throws IOException, InterruptedException, ModpackApiException {
        if (files.isEmpty()) {
            return;
        }

        int parallelism = Math.min(MAX_PARALLEL_DOWNLOADS, files.size());
        ExecutorService executor = Executors.newFixedThreadPool(parallelism, r -> {
            Thread t = new Thread(r, "modpack-download");
            t.setDaemon(true);
            return t;
        });
        try {
            List<Future<Void>> futures = new ArrayList<>(files.size());
            for (ManifestFile file : files) {
                Callable<Void> task = () -> {
                    listener.onFileStarted(file.path(), file.size());
                    downloader.download(slug, file, instanceDir, session);
                    listener.onFileDone(file.path());
                    return null;
                };
                futures.add(executor.submit(task));
            }
            awaitAll(futures);
        } finally {
            executor.shutdownNow();
        }
    }

    /** Waits for every download, then re-throws the first failure (if any) with its original checked type. */
    private void awaitAll(List<Future<Void>> futures) throws IOException, InterruptedException, ModpackApiException {
        Exception firstFailure = null;
        for (Future<Void> future : futures) {
            try {
                future.get();
            } catch (ExecutionException e) {
                if (firstFailure == null) {
                    firstFailure = e;
                }
            }
        }
        if (firstFailure == null) {
            return;
        }
        Throwable cause = firstFailure.getCause();
        if (cause instanceof IOException e) {
            throw e;
        }
        if (cause instanceof ModpackApiException e) {
            throw e;
        }
        if (cause instanceof InterruptedException e) {
            throw e;
        }
        if (cause instanceof RuntimeException e) {
            throw e;
        }
        throw new IOException("Modpack file download failed", cause);
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
