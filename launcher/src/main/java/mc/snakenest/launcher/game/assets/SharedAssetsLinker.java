package mc.snakenest.launcher.game.assets;

import mc.snakenest.launcher.config.AssetStorageMode;
import mc.snakenest.launcher.util.AppDirs;
import mc.snakenest.launcher.util.Log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import java.util.stream.Stream;

/**
 * Implements the "Stockage des ressources vanilla" setting by hardlinking vanilla assets/
 * libraries into (and back out of) a shared pool, around a normal, unmodified
 * {@code GameInstallService#install} call - no fork of FlowUpdater/OpenLauncherLib needed.
 *
 * <p>The trick: FlowUpdater's own downloader already skips re-downloading a file that's already
 * present at the target path with the right size/hash (verified against the real 1.9.4 decompiled
 * source - {@code VanillaDownloader#downloadLibraries}/{@code #downloadAssets}). So:
 * <ol>
 *   <li>{@link #preSeed} hardlinks whatever's already in the shared pool into the instance
 *   *before* {@code install()} runs - FlowUpdater then sees those files as already correct and
 *   never re-downloads them.
 *   <li>{@code install()} runs completely normally, downloading only what's genuinely missing
 *   straight into the instance directory, exactly as it always has.
 *   <li>{@link #absorb} moves anything {@code install()} just downloaded fresh into the pool, then
 *   replaces the instance's copy with a hardlink back - so the pool grows and the next modpack
 *   (or the next version of this one) benefits too.
 * </ol>
 *
 * <p>Two pools, mirroring FlowUpdater's own relative-path layout so a straight tree walk works for
 * both: an assets pool (mirrors the instance's {@code assets/} folder - Mojang's own
 * content-addressed {@code objects/xx/xxxx...} naming means this is naturally collision-free across
 * every Minecraft version) and a libraries pool (mirrors {@code libraries/} and <b>nothing else</b>,
 * see {@link #SHARED_LIBRARY_SUBDIRS} for why that's an allow-list rather than "everything except
 * assets and natives", and {@link #LEGACY_SHARED_SUBDIR} for why {@code versions/} used to be in it
 * and no longer is). {@code natives/} is never shared: extracted per-instance, platform-specific,
 * and small.
 *
 * <p>Never used for {@link AssetStorageMode#PER_MODPACK} - {@link #preSeed}/{@link #absorb} are
 * no-ops in that case, leaving the fully-independent-per-instance behavior untouched;
 * {@link #migrate} actively undoes any existing sharing instead.
 *
 * <p><b>Hardlink support isn't guaranteed</b> (different filesystems/drives between the pool and
 * an instance, or a filesystem that doesn't support hardlinks at all): the first failure aborts
 * the rest of that call's work and logs a warning, rather than partially linking - {@code
 * install()} then simply downloads whatever wasn't pre-seeded directly into the instance, the
 * same as {@link AssetStorageMode#PER_MODPACK}, for that one install.
 */
public final class SharedAssetsLinker {

    /**
     * The <b>only</b> instance subdirectories whose contents may ever be shared, besides
     * {@code assets/} - both mirror the same relative paths in the pool.
     *
     * <p>Deliberately an allow-list. It started out as a deny-list ({@code assets}, {@code natives})
     * applied while walking the whole instance, which silently pooled <b>everything else too</b>:
     * {@code mods/}, {@code config/} and - worst - {@code saves/}, so two packs whose world files
     * happened to match byte-for-byte (e.g. two freshly created worlds) ended up as literally the
     * same file on disk, where an in-place write from one pack would corrupt the other's save. An
     * allow-list makes that class of mistake impossible by construction rather than by remembering
     * to extend a list of exclusions.
     */
    private static final List<String> SHARED_LIBRARY_SUBDIRS = List.of("libraries");

    /**
     * Shared by earlier builds, deliberately no longer shared - and actively un-shared by
     * {@link #migrate}. Kept as a named constant precisely because dropping it from
     * {@link #SHARED_LIBRARY_SUBDIRS} isn't enough on its own: instances installed by those builds
     * are still full of the links it created.
     *
     * <p>The reasoning it was originally included on - "{@code versions/} holds the vanilla client
     * jar, which is a vanilla resource like any other" - holds for a launcher that lays instances
     * out the way vanilla does. Ours doesn't: it runs {@code client.jar} from the instance root
     * with the version JSONs beside it, and never reads {@code versions/} at all. Verified on a
     * real instance: {@code client.jar} and {@code versions/1.21.11/1.21.11.jar} were
     * byte-identical, and only the former is what gets launched.
     *
     * <p>So sharing it bought nothing, and cost far more than a redundant jar. Under
     * {@link AssetStorageMode#OFFICIAL_MINECRAFT} the pool is the player's real {@code .minecraft},
     * whose {@code versions/} - with TLauncher especially - holds one <b>complete game installation
     * per profile</b>: {@code mods/}, {@code config/}, {@code options.txt}, {@code saves/}. Linking
     * that subtree wholesale dragged every one of those profiles into every modpack instance,
     * worlds included (48 {@code level.dat} files on the instance that surfaced this). That is
     * precisely the corruption risk {@link #SHARED_LIBRARY_SUBDIRS} exists to prevent - an in-place
     * write to a linked world file rewrites the player's real one - reintroduced one directory
     * deeper, where a top-level allow-list couldn't see it.
     */
    private static final String LEGACY_SHARED_SUBDIR = "versions";

    private final AppDirs dirs;

    public SharedAssetsLinker(AppDirs dirs) {
        this.dirs = dirs;
    }

    /**
     * Call before {@code GameInstallService#install} - no-op for {@link AssetStorageMode#PER_MODPACK}.
     *
     * @return false if hardlinking turned out to be impossible, so this install is silently
     *         behaving like {@link AssetStorageMode#PER_MODPACK}; callers should tell the player
     *         rather than leave them wondering why nothing is being shared
     */
    public boolean preSeed(Path instanceDir, AssetStorageMode mode) {
        Path assetsPool = assetsPoolFor(mode);
        Path librariesPool = librariesPoolFor(mode);
        if (assetsPool == null || librariesPool == null) {
            return true;
        }

        if (!linkTree(assetsPool, instanceDir.resolve("assets"))) {
            return false;
        }
        for (String subdir : SHARED_LIBRARY_SUBDIRS) {
            if (!linkTree(librariesPool.resolve(subdir), instanceDir.resolve(subdir))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Call after a successful install - no-op for {@link AssetStorageMode#PER_MODPACK}.
     *
     * @return false on a hardlink failure, same meaning as {@link #preSeed}
     */
    public boolean absorb(Path instanceDir, AssetStorageMode mode) {
        Path assetsPool = assetsPoolFor(mode);
        Path librariesPool = librariesPoolFor(mode);
        if (assetsPool == null || librariesPool == null) {
            return true;
        }

        if (!absorbTree(instanceDir.resolve("assets"), assetsPool)) {
            return false;
        }
        for (String subdir : SHARED_LIBRARY_SUBDIRS) {
            if (!absorbTree(instanceDir.resolve(subdir), librariesPool.resolve(subdir))) {
                return false;
            }
        }
        return true;
    }

    /** What one {@link #migrate} pass actually did, so the caller can report something concrete instead of "done". */
    /**
     * @param unlinked files removed from the instance because they were links to somebody else's
     *                 game installation rather than this modpack's own - counted separately from
     *                 {@code privatised} because nothing was copied and no disk space changed
     *                 hands, see {@link #removeLegacyVersionLinks}
     */
    public record MigrationResult(int pooled, int deduplicated, int privatised, long bytesReclaimed,
                                  boolean hardlinksFailed, int unlinked) {

        static MigrationResult empty() {
            return new MigrationResult(0, 0, 0, 0, false, 0);
        }

        MigrationResult plus(MigrationResult other) {
            return new MigrationResult(
                    pooled + other.pooled,
                    deduplicated + other.deduplicated,
                    privatised + other.privatised,
                    bytesReclaimed + other.bytesReclaimed,
                    hardlinksFailed || other.hardlinksFailed,
                    unlinked + other.unlinked);
        }
    }

    /**
     * Actually applies {@code mode} to an instance that already exists, rather than only affecting
     * future installs (which is all {@link #preSeed}/{@link #absorb} do).
     *
     * <p>{@link #absorb} alone isn't enough here: it deliberately skips a file already present in
     * the pool, which during an install just means "it's already a hardlink". For an instance
     * installed *before* sharing was turned on, that same case is precisely the one that matters -
     * the pool has the file and the instance holds a second, independent physical copy of it. So
     * migration additionally *deduplicates*: it drops that copy and re-links it to the pool, which
     * is where the disk space actually comes back.
     *
     * <p>Migrating to {@link AssetStorageMode#PER_MODPACK} does the reverse (breaks the links into
     * private copies), so the setting means the same thing whichever direction it's changed in -
     * that direction costs disk space rather than saving it, which is what that option is for.
     */
    public MigrationResult migrate(Path instanceDir, AssetStorageMode mode) {
        if (mode == AssetStorageMode.PER_MODPACK) {
            MigrationResult result = MigrationResult.empty();
            // Files could be linked into either pool, so both are un-shared regardless of which
            // mode was previously selected.
            for (Path pool : List.of(dirs.sharedLibrariesPool(), officialMinecraftFolder())) {
                for (String subdir : SHARED_LIBRARY_SUBDIRS) {
                    result = result.plus(privatiseTree(instanceDir.resolve(subdir), pool.resolve(subdir)));
                }
            }
            result = result.plus(privatiseTree(instanceDir.resolve("assets"), dirs.sharedAssetsPool()))
                    .plus(privatiseTree(instanceDir.resolve("assets"), officialMinecraftFolder().resolve("assets")));
            return result.plus(repairWronglySharedFiles(instanceDir))
                    .plus(removeLegacyVersionLinks(instanceDir));
        }

        Path assetsPool = assetsPoolFor(mode);
        Path librariesPool = librariesPoolFor(mode);
        MigrationResult result = migrateTree(instanceDir.resolve("assets"), assetsPool);
        for (String subdir : SHARED_LIBRARY_SUBDIRS) {
            result = result.plus(migrateTree(instanceDir.resolve(subdir), librariesPool.resolve(subdir)));
        }
        return result.plus(repairWronglySharedFiles(instanceDir))
                .plus(removeLegacyVersionLinks(instanceDir));
    }

    /**
     * Undoes the damage described on {@link #SHARED_LIBRARY_SUBDIRS}: an instance set up by an
     * earlier build may still have {@code mods/}/{@code config/}/{@code saves/} files hardlinked
     * into a pool. Fixing the allow-list stops that happening again but can't unpick links that
     * already exist, so migration also gives every file <em>outside</em> the shareable directories
     * its own copy back - cheap when there's nothing wrong (the common case), and the only way a
     * player already affected ever gets their saves properly separated again.
     */
    private MigrationResult repairWronglySharedFiles(Path instanceDir) {
        int privatised = 0;
        for (Path pool : List.of(dirs.sharedLibrariesPool(), officialMinecraftFolder())) {
            if (!Files.isDirectory(pool)) {
                continue;
            }
            for (Path file : filesUnder(instanceDir)) {
                Path relative = instanceDir.relativize(file);
                String topLevel = relative.getNameCount() > 0 ? relative.getName(0).toString() : "";
                if (SHARED_LIBRARY_SUBDIRS.contains(topLevel) || "assets".equals(topLevel) || "natives".equals(topLevel)) {
                    continue;
                }
                // versions/ is handled by removeLegacyVersionLinks instead: those files belong to
                // the pool, not to this instance, so giving them a private copy here would turn a
                // phantom 13 GB of hardlinks into 13 GB genuinely written to disk - the exact
                // opposite of what a player clicking "Migrer" is asking for.
                if (LEGACY_SHARED_SUBDIR.equals(topLevel)) {
                    continue;
                }
                if (privatise(file, pool.resolve(relative))) {
                    Log.warn(SharedAssetsLinker.class, "Un-shared " + relative
                            + ": it should never have been pooled (fixed by the allow-list, see SHARED_LIBRARY_SUBDIRS)");
                    privatised++;
                }
            }
        }
        return new MigrationResult(0, 0, privatised, 0, false, 0);
    }

    /**
     * Deletes the instance's {@code versions/} entries that are hardlinks into a pool - see
     * {@link #LEGACY_SHARED_SUBDIR} for how they got there and why they shouldn't be.
     *
     * <p><b>Deleted, not privatised.</b> These files were never this instance's to begin with: they
     * are the player's other game installations, dragged in by an earlier build. Deleting a hardlink
     * only drops <em>this</em> name for the data - the original under {@code .minecraft} keeps its
     * own link and is left completely untouched, which is what makes this safe to do to somebody
     * else's Minecraft profiles.
     *
     * <p>Files under {@code versions/} that are <em>not</em> shared are left alone: whatever wrote
     * them, it wasn't the pre-seeding, so they aren't ours to remove.
     */
    private MigrationResult removeLegacyVersionLinks(Path instanceDir) {
        Path versionsDir = instanceDir.resolve(LEGACY_SHARED_SUBDIR);
        if (!Files.isDirectory(versionsDir)) {
            return MigrationResult.empty();
        }

        int unlinked = 0;
        for (Path pool : List.of(dirs.sharedLibrariesPool(), officialMinecraftFolder())) {
            if (!Files.isDirectory(pool)) {
                continue;
            }
            for (Path file : filesUnder(versionsDir)) {
                Path poolFile = pool.resolve(instanceDir.relativize(file));
                try {
                    if (!Files.exists(poolFile) || !Files.isSameFile(file, poolFile)) {
                        continue;
                    }
                    Files.delete(file);
                    unlinked++;
                } catch (IOException e) {
                    Log.warn(SharedAssetsLinker.class, "Could not un-link " + file.getFileName() + ": " + e.getMessage());
                }
            }
        }

        if (unlinked > 0) {
            Log.info(SharedAssetsLinker.class, "Removed " + unlinked
                    + " file(s) wrongly linked under " + LEGACY_SHARED_SUBDIR + "/ from the shared pool"
                    + " (originals untouched - see LEGACY_SHARED_SUBDIR)");
            deleteEmptyDirectories(versionsDir);
        }
        return new MigrationResult(0, 0, 0, 0, false, unlinked);
    }

    /** Deepest-first, so a parent is only attempted once its children are gone. Never removes {@code root} itself. */
    private void deleteEmptyDirectories(Path root) {
        List<Path> directories;
        try (Stream<Path> stream = Files.walk(root)) {
            directories = stream.filter(Files::isDirectory)
                    .filter(path -> !path.equals(root))
                    .sorted(java.util.Comparator.comparingInt(Path::getNameCount).reversed())
                    .toList();
        } catch (IOException e) {
            return;
        }
        for (Path directory : directories) {
            try {
                Files.delete(directory);
            } catch (IOException e) {
                // Not empty (something legitimately lives there), or already gone - both fine.
            }
        }
    }

    private Path assetsPoolFor(AssetStorageMode mode) {
        return switch (mode) {
            case PER_MODPACK -> null;
            case LAUNCHER_SHARED -> dirs.sharedAssetsPool();
            case OFFICIAL_MINECRAFT -> officialMinecraftFolder().resolve("assets");
        };
    }

    private Path librariesPoolFor(AssetStorageMode mode) {
        return switch (mode) {
            case PER_MODPACK -> null;
            case LAUNCHER_SHARED -> dirs.sharedLibrariesPool();
            // The official launcher's own .minecraft root IS the mirror namespace here (not a
            // "libraries" subfolder) - it already holds libraries/ and versions/ directly, the
            // same relative layout FlowUpdater itself writes into an instance.
            case OFFICIAL_MINECRAFT -> officialMinecraftFolder();
        };
    }

    /** Same OS detection as FlowUpdater's own {@code IOUtils.getMinecraftFolder()} (verified against its 1.9.4 source) - kept independent rather than imported, since only {@code game.flowupdater.FlowUpdaterGameInstallService} is allowed to depend on FlowUpdater types (see that package's README). */
    Path officialMinecraftFolder() {
        return resolveOfficialMinecraftFolder(System.getProperty("os.name", ""), System.getProperty("user.home"), System.getenv("APPDATA"));
    }

    /** Package-visible, pure version of {@link #officialMinecraftFolder()} - exercised directly by tests. */
    static Path resolveOfficialMinecraftFolder(String osName, String home, String windowsAppData) {
        String os = osName.toLowerCase();

        if (os.contains("win")) {
            Path base = (windowsAppData != null && !windowsAppData.isBlank()) ? Path.of(windowsAppData) : Path.of(home, "AppData", "Roaming");
            return base.resolve(".minecraft");
        }

        if (os.contains("mac") || os.contains("darwin")) {
            return Path.of(home, "Library", "Application Support", ".minecraft");
        }

        return Path.of(home, ".minecraft");
    }

    /**
     * Hardlinks every regular file under {@code poolRoot} into the same relative path under
     * {@code targetRoot}, skipping anything already present there (never overwrites an existing
     * file, linked or not).
     *
     * @return false if hardlinking failed and the rest of this call's pooling should be abandoned
     */
    private boolean linkTree(Path poolRoot, Path targetRoot) {
        if (!Files.isDirectory(poolRoot)) {
            return true;
        }
        java.util.List<Path> poolFiles;
        try (Stream<Path> stream = Files.walk(poolRoot)) {
            poolFiles = stream.filter(Files::isRegularFile).toList();
        } catch (IOException e) {
            Log.warn(SharedAssetsLinker.class, "Could not walk " + poolRoot + ": " + e.getMessage());
            return true;
        }

        for (Path poolFile : poolFiles) {
            Path relative = poolRoot.relativize(poolFile);
            Path targetFile = targetRoot.resolve(relative);
            if (Files.exists(targetFile)) {
                continue;
            }
            try {
                Files.createDirectories(targetFile.getParent());
                Files.createLink(targetFile, poolFile);
            } catch (IOException | UnsupportedOperationException e) {
                Log.warn(SharedAssetsLinker.class, "Hardlinking unavailable between " + poolRoot + " and " + targetRoot + ", falling back to per-modpack storage for this install: " + e.getMessage());
                return false;
            }
        }
        return true;
    }

    /**
     * Moves every regular file newly present under {@code sourceRoot} (relative to
     * {@code excludedTopLevel} subdirectories) that isn't already in {@code poolRoot} into the
     * pool, then hardlinks it back at its original location - so a fresh download this install
     * just made becomes available to every future one too.
     *
     * @return false if hardlinking failed and the rest of this call's pooling should be abandoned
     */
    private boolean absorbTree(Path sourceRoot, Path poolRoot) {
        // Materialized into a list before processing, rather than iterated lazily - each file
        // gets moved out and immediately replaced (same name, now a hardlink) as it's visited,
        // and Files.walk's behavior when the tree it's iterating is being modified concurrently
        // is unspecified.
        for (Path sourceFile : filesUnder(sourceRoot)) {
            Path relative = sourceRoot.relativize(sourceFile);
            Path poolFile = poolRoot.resolve(relative);
            if (Files.exists(poolFile)) {
                // Already pooled (pre-seeded as a hardlink, or absorbed by a previous
                // install) - nothing new to do.
                continue;
            }

            try {
                Files.createDirectories(poolFile.getParent());
                Files.move(sourceFile, poolFile);
                Files.createLink(sourceFile, poolFile);
            } catch (IOException | UnsupportedOperationException e) {
                Log.warn(SharedAssetsLinker.class, "Hardlinking unavailable between " + sourceRoot + " and " + poolRoot + ", leaving this install's files un-pooled: " + e.getMessage());
                return false;
            }
        }
        return true;
    }

    /**
     * Like {@link #absorbTree}, but also handles the case that one deliberately skips: the pool
     * already holds this path *and* the instance has its own separate copy of it. That's the whole
     * point of migrating an instance installed before sharing was on - see {@link #migrate}.
     */
    private MigrationResult migrateTree(Path sourceRoot, Path poolRoot) {
        int pooled = 0;
        int deduplicated = 0;
        long reclaimed = 0;

        for (Path sourceFile : filesUnder(sourceRoot)) {
            Path poolFile = poolRoot.resolve(sourceRoot.relativize(sourceFile));
            try {
                if (!Files.exists(poolFile)) {
                    Files.createDirectories(poolFile.getParent());
                    Files.move(sourceFile, poolFile);
                    Files.createLink(sourceFile, poolFile);
                    pooled++;
                    continue;
                }

                if (Files.isSameFile(sourceFile, poolFile)) {
                    // Already the same physical file - nothing to reclaim.
                    continue;
                }

                long size = Files.size(sourceFile);
                if (size != Files.size(poolFile)) {
                    // Same relative path, different content. Shouldn't happen (assets are named by
                    // their own hash, library paths pin a version), so rather than pick a winner
                    // and risk corrupting an install, leave it alone and say so.
                    Log.warn(SharedAssetsLinker.class, "Not deduplicating " + sourceRoot.relativize(sourceFile)
                            + ": it differs in size from the pooled copy");
                    continue;
                }

                Files.delete(sourceFile);
                Files.createLink(sourceFile, poolFile);
                deduplicated++;
                reclaimed += size;
            } catch (IOException | UnsupportedOperationException e) {
                Log.warn(SharedAssetsLinker.class, "Could not migrate " + sourceRoot.relativize(sourceFile)
                        + " into " + poolRoot + ": " + e.getMessage());
                return new MigrationResult(pooled, deduplicated, 0, reclaimed, true, 0);
            }
        }
        return new MigrationResult(pooled, deduplicated, 0, reclaimed, false, 0);
    }

    /**
     * The {@link AssetStorageMode#PER_MODPACK} direction: turns each file this instance shares with
     * {@code poolRoot} back into an independent copy, so the instance really does stand alone.
     * Copy-then-replace via a temp file in the same directory, since writing into a hardlinked file
     * in place would change the pooled copy (and therefore every other instance's) too.
     */
    private MigrationResult privatiseTree(Path sourceRoot, Path poolRoot) {
        if (!Files.isDirectory(poolRoot)) {
            return MigrationResult.empty();
        }

        int privatised = 0;
        for (Path sourceFile : filesUnder(sourceRoot)) {
            if (privatise(sourceFile, poolRoot.resolve(sourceRoot.relativize(sourceFile)))) {
                privatised++;
            }
        }
        return new MigrationResult(0, 0, privatised, 0, false, 0);
    }

    /**
     * Gives {@code file} its own copy if (and only if) it's currently the same physical file as
     * {@code poolFile}. Copy-then-rename via a temp file in the same directory, because writing
     * into a hardlinked file in place would change the pooled copy - and therefore every other
     * instance's - too.
     *
     * @return whether anything was actually un-shared
     */
    private boolean privatise(Path file, Path poolFile) {
        try {
            if (!Files.exists(poolFile) || !Files.isSameFile(file, poolFile)) {
                return false;
            }
            Path tmp = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".unshare");
            Files.copy(file, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            Log.warn(SharedAssetsLinker.class, "Could not un-share " + file.getFileName() + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Every regular file under {@code root}, materialized into a list rather than streamed because
     * callers create/move/delete files as they iterate, and {@code Files.walk}'s behavior over a
     * tree being modified underneath it is unspecified.
     */
    private List<Path> filesUnder(Path root) {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile).toList();
        } catch (IOException e) {
            Log.warn(SharedAssetsLinker.class, "Could not walk " + root + ": " + e.getMessage());
            return List.of();
        }
    }
}
