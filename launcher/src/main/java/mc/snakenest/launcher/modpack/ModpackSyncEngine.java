package mc.snakenest.launcher.modpack;

import mc.snakenest.launcher.auth.PlayerSession;
import mc.snakenest.launcher.util.AtomicFiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
        sync(slug, instanceDir, targetManifest, session, listener, false);
    }

    /**
     * @param resetPlayerFiles restore files the pack marked {@code DEFAULT}/{@code MERGE} as well -
     *                         only ever true when a player explicitly asked for it during a repair
     */
    public void sync(String slug, Path instanceDir, ModpackManifest targetManifest, PlayerSession session,
                     SyncProgressListener listener, boolean resetPlayerFiles)
            throws IOException, InterruptedException, ModpackApiException {
        Files.createDirectories(instanceDir);

        // Resolved before anything else and held for the rest of the sync: these rules decide the
        // plan itself, so reading them afterwards - or per file - would be too late.
        FileMetas metas = resolveFileMetas(slug, instanceDir, targetManifest, session);

        Optional<StoredManifest> previous = manifestStore.load(instanceDir);
        SyncPlan plan = ManifestDiffer.diff(previous, targetManifest, metas,
                path -> existsInInstance(instanceDir, path), resetPlayerFiles);

        Path batchCache = Files.createTempDirectory(instanceDir.toAbsolutePath().getParent(), ".sn3-batch-");
        try {
            List<ManifestFile> requested = new ArrayList<>(plan.toDownload());
            for (MergeAction action : plan.toMerge()) requested.add(action.file());
            DownloadProgress progress = new DownloadProgress(requested, listener);
            ModpackFileDownloader activeDownloader = downloader.withBlobCache(batchCache, progress::received);
            activeDownloader.prefetch(slug, requested, session);
            listener.beforeFileApplication();
            downloadAllInParallel(slug, instanceDir, plan.toDownload(), session, listener, activeDownloader);

            // Sequential, unlike the downloads: each one reads the player's existing file, merges and
            // rewrites it, and there are only ever a handful of them.
            for (MergeAction action : plan.toMerge()) {
                listener.onFileStarted(action.file().path(), action.file().size());
                mergeOne(slug, instanceDir, action, session, activeDownloader);
                listener.onFileDone(action.file().path());
            }

            progress.complete();

            for (String path : plan.toDelete()) {
                deleteAndPruneEmptyParents(instanceDir, path);
                listener.onDeleted(path);
            }

            manifestStore.save(instanceDir, StoredManifest.of(targetManifest));
        } finally {
            try (var files = Files.walk(batchCache)) {
                for (Path file : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
            }
        }
    }

    /**
     * The pack's rules, preferred fresh from the target manifest so a rule added in this very
     * update takes effect during it - falling back to the copy already on disk (a pack that
     * stopped shipping the file, or a manifest fetched without it), and finally to no rules at
     * all, which is plain hash-diffing exactly as before the convention existed.
     */
    private FileMetas resolveFileMetas(String slug, Path instanceDir, ModpackManifest manifest, PlayerSession session)
            throws IOException, InterruptedException, ModpackApiException {
        for (ManifestFile file : manifest.files()) {
            if (file.path().equals(FileMetas.FILE_NAME)) {
                // Downloaded rather than read from disk: it is a few hundred bytes, and the copy
                // on disk may be the previous version's.
                downloader.download(slug, file, instanceDir, session);
                break;
            }
        }

        Path onDisk = InstancePaths.resolveSafely(instanceDir, FileMetas.FILE_NAME);

        if (!Files.isRegularFile(onDisk)) {
            return FileMetas.empty();
        }

        return FileMetas.parse(Files.readString(onDisk, java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * Whether the instance already holds this path.
     *
     * <p>A path that fails the traversal check answers "no" rather than propagating: this is only
     * ever consulted to decide whether a file still needs seeding, and a hostile path is refused
     * for real - with the exception it deserves - the moment the plan tries to write it, via the
     * same {@link InstancePaths#resolveSafely} call in the download and merge steps.
     */
    private static boolean existsInInstance(Path instanceDir, String path) {
        try {
            return Files.exists(InstancePaths.resolveSafely(instanceDir, path));
        } catch (PathTraversalException e) {
            return false;
        }
    }

    /** Fetches the pack's version into memory and folds it into whatever the player already has. */
    private void mergeOne(String slug, Path instanceDir, MergeAction action, PlayerSession session, ModpackFileDownloader activeDownloader)
            throws IOException, InterruptedException, ModpackApiException {
        Path target = InstancePaths.resolveSafely(instanceDir, action.file().path());
        byte[] existing = Files.isRegularFile(target) ? Files.readAllBytes(target) : null;
        byte[] incoming = activeDownloader.fetchBytes(slug, action.file(), session);

        AtomicFiles.write(target, FileMerger.merge(action.format(), existing, incoming));
    }

    private void downloadAllInParallel(String slug, Path instanceDir, List<ManifestFile> files, PlayerSession session, SyncProgressListener listener, ModpackFileDownloader activeDownloader)
            throws IOException, InterruptedException, ModpackApiException {
        List<mc.snakenest.launcher.util.ParallelTasks.Work> jobs=new ArrayList<>();
        for(ManifestFile file:files)jobs.add(()->{
            listener.onFileStarted(file.path(),file.size());
            activeDownloader.download(slug,file,instanceDir,session);
            listener.onFileDone(file.path());
        });
        try {mc.snakenest.launcher.util.ParallelTasks.run(jobs,MAX_PARALLEL_DOWNLOADS,()->{});}
        catch(IOException|InterruptedException|ModpackApiException failure){throw failure;}
        catch(RuntimeException failure){throw failure;}
        catch(Exception failure){throw new IOException("Modpack file application failed",failure);}
    }

    /** Serializes worker callbacks and withholds completion until every network file verifies. */
    private static final class DownloadProgress {
        private final SyncProgressListener listener;
        private final long total;
        private long received;
        private long lastNotification = System.nanoTime();

        DownloadProgress(List<ManifestFile> requested, SyncProgressListener listener) throws IOException {
            this.listener = listener;
            java.util.Set<String> internal = new java.util.HashSet<>();
            long sum = 0;
            try {
                for (ManifestFile file : requested) {
                    if (file.size() < 0) throw new IOException("Negative manifest file size");
                    if (file.url() != null || internal.add(file.hash().toLowerCase(java.util.Locale.ROOT))) {
                        sum = Math.addExact(sum, file.size());
                    }
                }
            } catch (ArithmeticException e) { throw new IOException("Download size overflow", e); }
            total = sum;
            listener.onDownloadProgress(0, total);
        }

        synchronized void received(long count) {
            received += count;
            long now = System.nanoTime();
            if (now - lastNotification >= 100_000_000L && received < total) {
                listener.onDownloadProgress(received, total);
                lastNotification = now;
            }
        }

        synchronized void complete() {
            listener.onDownloadProgress(received, total);
        }
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
