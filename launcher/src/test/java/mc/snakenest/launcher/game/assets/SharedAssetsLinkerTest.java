package mc.snakenest.launcher.game.assets;

import mc.snakenest.launcher.config.AssetStorageMode;
import mc.snakenest.launcher.util.AppDirs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the real filesystem (not mocks) - the whole point of this class is what hardlinks
 * actually do on disk, which a mock would only restate rather than verify.
 */
class SharedAssetsLinkerTest {

    @Test
    void preSeedHardlinksPooledAssetsIntoTheInstance(@TempDir Path root) throws IOException {
        AppDirs dirs = new AppDirs(root);
        SharedAssetsLinker linker = new SharedAssetsLinker(dirs);
        Path instanceDir = dirs.instance("aventure-ultime");

        Path pooledAsset = dirs.sharedAssetsPool().resolve("objects").resolve("ab").resolve("abcdef123");
        Files.createDirectories(pooledAsset.getParent());
        Files.writeString(pooledAsset, "asset-content");

        linker.preSeed(instanceDir, AssetStorageMode.LAUNCHER_SHARED);

        Path instanceAsset = instanceDir.resolve("assets").resolve("objects").resolve("ab").resolve("abcdef123");
        assertTrue(Files.exists(instanceAsset), "the pooled asset should have been linked into the instance");
        assertEquals("asset-content", Files.readString(instanceAsset));
        assertTrue(isSameFile(pooledAsset, instanceAsset), "it should be a hardlink to the pooled file, not a copy");
    }

    @Test
    void preSeedHardlinksPooledLibrariesButNeverVersions(@TempDir Path root) throws IOException {
        AppDirs dirs = new AppDirs(root);
        SharedAssetsLinker linker = new SharedAssetsLinker(dirs);
        Path instanceDir = dirs.instance("aventure-ultime");

        Path pooledLibrary = dirs.sharedLibrariesPool().resolve("libraries").resolve("com").resolve("example").resolve("lib.jar");
        Path pooledClientJar = dirs.sharedLibrariesPool().resolve("versions").resolve("1.20.1").resolve("1.20.1.jar");
        Files.createDirectories(pooledLibrary.getParent());
        Files.createDirectories(pooledClientJar.getParent());
        Files.writeString(pooledLibrary, "lib");
        Files.writeString(pooledClientJar, "client");

        linker.preSeed(instanceDir, AssetStorageMode.LAUNCHER_SHARED);

        // Mirrored at the same relative paths FlowUpdater itself writes into the instance.
        assertTrue(isSameFile(pooledLibrary, instanceDir.resolve("libraries").resolve("com").resolve("example").resolve("lib.jar")));

        // versions/ is deliberately NOT pre-seeded any more: this launcher runs client.jar from the
        // instance root and never reads versions/, while the pool's versions/ - when the pool is the
        // player's real .minecraft - is a pile of complete third-party game installations, worlds
        // included. See SharedAssetsLinker.LEGACY_SHARED_SUBDIR.
        assertFalse(Files.exists(instanceDir.resolve("versions").resolve("1.20.1").resolve("1.20.1.jar")));
    }

    @Test
    void migrateUnlinksForeignGameInstallationsFromVersionsWithoutCopyingThem(@TempDir Path root) throws IOException {
        // Reproduces exactly what an earlier build did with AssetStorageMode.OFFICIAL_MINECRAFT:
        // the pool was the player's real .minecraft, whose versions/ holds one whole game
        // installation per TLauncher profile - so every profile, worlds and all, got hardlinked
        // into every modpack instance.
        AppDirs dirs = new AppDirs(root);
        SharedAssetsLinker linker = new SharedAssetsLinker(dirs);

        Path pool = dirs.sharedLibrariesPool();
        Path foreignWorld = pool.resolve("versions").resolve("Koh Lanta").resolve("saves").resolve("maison").resolve("level.dat");
        Path foreignJar = pool.resolve("versions").resolve("Koh Lanta").resolve("Koh Lanta.jar");
        Path pooledLibrary = pool.resolve("libraries").resolve("com").resolve("lib.jar");
        writeFile(foreignWorld, "someone-elses-world");
        writeFile(foreignJar, "someone-elses-jar");
        writeFile(pooledLibrary, "a-real-shared-library");

        Path instance = dirs.instance("pack-a");
        Path linkedWorld = instance.resolve("versions").resolve("Koh Lanta").resolve("saves").resolve("maison").resolve("level.dat");
        Path linkedJar = instance.resolve("versions").resolve("Koh Lanta").resolve("Koh Lanta.jar");
        Path linkedLibrary = instance.resolve("libraries").resolve("com").resolve("lib.jar");
        Files.createDirectories(linkedWorld.getParent());
        Files.createDirectories(linkedLibrary.getParent());
        Files.createLink(linkedWorld, foreignWorld);
        Files.createLink(linkedJar, foreignJar);
        Files.createLink(linkedLibrary, pooledLibrary);

        SharedAssetsLinker.MigrationResult result = linker.migrate(instance, AssetStorageMode.LAUNCHER_SHARED);

        // Gone from the modpack...
        assertFalse(Files.exists(linkedWorld), "another profile's world has no business in this modpack");
        assertFalse(Files.exists(linkedJar), "another profile's jar has no business in this modpack");
        assertEquals(2, result.unlinked());

        // ...but emphatically NOT deleted from the player's actual Minecraft installation. Removing
        // a hardlink only drops that one name for the data.
        assertTrue(Files.exists(foreignWorld), "the player's real world must survive untouched");
        assertEquals("someone-elses-world", Files.readString(foreignWorld));

        // And the genuinely shareable file is still shared - un-linking versions/ must not turn
        // into "stop sharing anything".
        assertTrue(isSameFile(pooledLibrary, linkedLibrary));
    }

    @Test
    void migratingVersionsMustNotTurnHardlinksIntoRealCopies(@TempDir Path root) throws IOException {
        // The trap this fix had to avoid: repairWronglySharedFiles gives wrongly-shared files a
        // private copy. Applied to versions/, that would have rewritten gigabytes of other people's
        // profiles into the instance for real - turning a phantom size into an actual one, which is
        // the exact opposite of what clicking "Migrer" is meant to do.
        AppDirs dirs = new AppDirs(root);
        SharedAssetsLinker linker = new SharedAssetsLinker(dirs);

        Path pooledProfileFile = dirs.sharedLibrariesPool().resolve("versions").resolve("Bedwars").resolve("Bedwars.jar");
        writeFile(pooledProfileFile, "x".repeat(4096));

        Path instance = dirs.instance("pack-a");
        Path linked = instance.resolve("versions").resolve("Bedwars").resolve("Bedwars.jar");
        Files.createDirectories(linked.getParent());
        Files.createLink(linked, pooledProfileFile);

        linker.migrate(instance, AssetStorageMode.LAUNCHER_SHARED);

        assertFalse(Files.exists(linked), "must be unlinked, not copied");
        assertEquals(1, Files.getAttribute(pooledProfileFile, "unix:nlink"),
                "the pool copy should be back down to a single link, i.e. nothing was duplicated");
    }

    @Test
    void preSeedNeverOverwritesAFileAlreadyInTheInstance(@TempDir Path root) throws IOException {
        AppDirs dirs = new AppDirs(root);
        SharedAssetsLinker linker = new SharedAssetsLinker(dirs);
        Path instanceDir = dirs.instance("aventure-ultime");

        Path pooledAsset = dirs.sharedAssetsPool().resolve("objects").resolve("ab").resolve("abcdef123");
        Files.createDirectories(pooledAsset.getParent());
        Files.writeString(pooledAsset, "pooled");

        Path instanceAsset = instanceDir.resolve("assets").resolve("objects").resolve("ab").resolve("abcdef123");
        Files.createDirectories(instanceAsset.getParent());
        Files.writeString(instanceAsset, "already-there");

        linker.preSeed(instanceDir, AssetStorageMode.LAUNCHER_SHARED);

        assertEquals("already-there", Files.readString(instanceAsset), "an existing instance file must be left untouched");
        assertFalse(isSameFile(pooledAsset, instanceAsset));
    }

    @Test
    void absorbMovesFreshlyDownloadedFilesIntoThePoolAndLinksThemBack(@TempDir Path root) throws IOException {
        AppDirs dirs = new AppDirs(root);
        SharedAssetsLinker linker = new SharedAssetsLinker(dirs);
        Path instanceDir = dirs.instance("aventure-ultime");

        Path downloadedAsset = instanceDir.resolve("assets").resolve("objects").resolve("cd").resolve("cdef456");
        Path downloadedLibrary = instanceDir.resolve("libraries").resolve("com").resolve("example").resolve("fresh.jar");
        Files.createDirectories(downloadedAsset.getParent());
        Files.createDirectories(downloadedLibrary.getParent());
        Files.writeString(downloadedAsset, "fresh-asset");
        Files.writeString(downloadedLibrary, "fresh-lib");

        linker.absorb(instanceDir, AssetStorageMode.LAUNCHER_SHARED);

        Path pooledAsset = dirs.sharedAssetsPool().resolve("objects").resolve("cd").resolve("cdef456");
        Path pooledLibrary = dirs.sharedLibrariesPool().resolve("libraries").resolve("com").resolve("example").resolve("fresh.jar");

        assertTrue(Files.exists(pooledAsset), "the fresh asset should now be in the pool");
        assertTrue(Files.exists(pooledLibrary), "the fresh library should now be in the pool");
        // Still readable at its original location, now as a hardlink - the game must not notice.
        assertEquals("fresh-asset", Files.readString(downloadedAsset));
        assertEquals("fresh-lib", Files.readString(downloadedLibrary));
        assertTrue(isSameFile(pooledAsset, downloadedAsset));
        assertTrue(isSameFile(pooledLibrary, downloadedLibrary));
    }

    @Test
    void absorbNeverPoolsNatives(@TempDir Path root) throws IOException {
        AppDirs dirs = new AppDirs(root);
        SharedAssetsLinker linker = new SharedAssetsLinker(dirs);
        Path instanceDir = dirs.instance("aventure-ultime");

        // Extracted per-instance and platform-specific - deliberately excluded from the pool.
        Path nativeFile = instanceDir.resolve("natives").resolve("liblwjgl.so");
        Files.createDirectories(nativeFile.getParent());
        Files.writeString(nativeFile, "native");

        linker.absorb(instanceDir, AssetStorageMode.LAUNCHER_SHARED);

        assertFalse(Files.exists(dirs.sharedLibrariesPool().resolve("natives").resolve("liblwjgl.so")),
                "natives must never be pooled");
        assertTrue(Files.exists(nativeFile), "the native must be left in place in the instance");
    }

    @Test
    void absorbDoesNotDuplicateAnAssetThatIsAlreadyPooled(@TempDir Path root) throws IOException {
        AppDirs dirs = new AppDirs(root);
        SharedAssetsLinker linker = new SharedAssetsLinker(dirs);
        Path instanceDir = dirs.instance("aventure-ultime");

        // Exactly the state left behind by preSeed: pooled, and hardlinked into the instance.
        Path pooledAsset = dirs.sharedAssetsPool().resolve("objects").resolve("ab").resolve("abcdef123");
        Files.createDirectories(pooledAsset.getParent());
        Files.writeString(pooledAsset, "pooled");
        Path instanceAsset = instanceDir.resolve("assets").resolve("objects").resolve("ab").resolve("abcdef123");
        Files.createDirectories(instanceAsset.getParent());
        Files.createLink(instanceAsset, pooledAsset);

        linker.absorb(instanceDir, AssetStorageMode.LAUNCHER_SHARED);

        assertEquals("pooled", Files.readString(pooledAsset));
        assertTrue(isSameFile(pooledAsset, instanceAsset), "the existing link should be left as-is");
    }

    @Test
    void perModpackModeTouchesNothingAtAll(@TempDir Path root) throws IOException {
        AppDirs dirs = new AppDirs(root);
        SharedAssetsLinker linker = new SharedAssetsLinker(dirs);
        Path instanceDir = dirs.instance("aventure-ultime");

        Path downloadedAsset = instanceDir.resolve("assets").resolve("objects").resolve("cd").resolve("cdef456");
        Files.createDirectories(downloadedAsset.getParent());
        Files.writeString(downloadedAsset, "fresh-asset");

        linker.preSeed(instanceDir, AssetStorageMode.PER_MODPACK);
        linker.absorb(instanceDir, AssetStorageMode.PER_MODPACK);

        assertFalse(Files.exists(dirs.sharedAssetsPool()), "no pool should ever be created in per-modpack mode");
        assertEquals("fresh-asset", Files.readString(downloadedAsset), "the instance's own copy must be left alone");
    }

    @Test
    void aFullRoundTripSharesOneAssetBetweenTwoModpacks(@TempDir Path root) throws IOException {
        AppDirs dirs = new AppDirs(root);
        SharedAssetsLinker linker = new SharedAssetsLinker(dirs);
        Path firstInstance = dirs.instance("premier");
        Path secondInstance = dirs.instance("second");

        // First modpack installs and "downloads" an asset, which then gets pooled.
        Path firstAsset = firstInstance.resolve("assets").resolve("objects").resolve("ab").resolve("shared123");
        Files.createDirectories(firstAsset.getParent());
        Files.writeString(firstAsset, "shared-content");
        linker.absorb(firstInstance, AssetStorageMode.LAUNCHER_SHARED);

        // Second modpack gets it for free, without any download.
        linker.preSeed(secondInstance, AssetStorageMode.LAUNCHER_SHARED);

        Path secondAsset = secondInstance.resolve("assets").resolve("objects").resolve("ab").resolve("shared123");
        assertTrue(Files.exists(secondAsset), "the second modpack should have received the pooled asset");
        assertEquals("shared-content", Files.readString(secondAsset));
        assertTrue(isSameFile(firstAsset, secondAsset), "both modpacks should point at the same physical file");
    }

    @Test
    void officialMinecraftFolderResolvesPerOs() {
        assertEquals(Path.of("C:\\Users\\someone\\AppData\\Roaming", ".minecraft"),
                SharedAssetsLinker.resolveOfficialMinecraftFolder("Windows 11", "C:\\Users\\someone", "C:\\Users\\someone\\AppData\\Roaming"));

        assertEquals(Path.of("/Users/someone", "Library", "Application Support", ".minecraft"),
                SharedAssetsLinker.resolveOfficialMinecraftFolder("Mac OS X", "/Users/someone", null));

        assertEquals(Path.of("/home/someone", ".minecraft"),
                SharedAssetsLinker.resolveOfficialMinecraftFolder("Linux", "/home/someone", null));
    }

    @Test
    void officialMinecraftFolderFallsBackToAppDataRoamingWhenTheEnvVarIsMissing() {
        assertEquals(Path.of("C:\\Users\\someone", "AppData", "Roaming", ".minecraft"),
                SharedAssetsLinker.resolveOfficialMinecraftFolder("Windows 10", "C:\\Users\\someone", null));
    }

    @Test
    void migrateDeduplicatesAnInstanceInstalledBeforeSharingWasOn(@TempDir Path root) throws IOException {
        // The case absorb() alone deliberately skips, and the whole reason migrate() exists: the
        // pool already has the file AND this instance holds its own separate physical copy.
        AppDirs dirs = new AppDirs(root);
        SharedAssetsLinker linker = new SharedAssetsLinker(dirs);

        Path pooledAsset = dirs.sharedAssetsPool().resolve("objects").resolve("ab").resolve("abcdef123");
        Files.createDirectories(pooledAsset.getParent());
        Files.writeString(pooledAsset, "shared-content");

        Path instanceDir = dirs.instance("installe-avant");
        Path ownCopy = instanceDir.resolve("assets").resolve("objects").resolve("ab").resolve("abcdef123");
        Files.createDirectories(ownCopy.getParent());
        Files.writeString(ownCopy, "shared-content");
        assertFalse(isSameFile(pooledAsset, ownCopy), "precondition: an independent copy, not a link");

        SharedAssetsLinker.MigrationResult result = linker.migrate(instanceDir, AssetStorageMode.LAUNCHER_SHARED);

        assertEquals(1, result.deduplicated());
        assertEquals("shared-content".length(), result.bytesReclaimed());
        assertTrue(isSameFile(pooledAsset, ownCopy), "the duplicate should now be a link to the pool");
        assertEquals("shared-content", Files.readString(ownCopy), "content must be untouched");
        assertFalse(result.hardlinksFailed());
    }

    @Test
    void migratePoolsAFileTheSharedStorageDoesNotHaveYet(@TempDir Path root) throws IOException {
        AppDirs dirs = new AppDirs(root);
        SharedAssetsLinker linker = new SharedAssetsLinker(dirs);
        Path instanceDir = dirs.instance("seul-installe");
        Path asset = instanceDir.resolve("assets").resolve("objects").resolve("cd").resolve("cdef456");
        Files.createDirectories(asset.getParent());
        Files.writeString(asset, "brand-new");

        SharedAssetsLinker.MigrationResult result = linker.migrate(instanceDir, AssetStorageMode.LAUNCHER_SHARED);

        assertEquals(1, result.pooled());
        assertEquals(0, result.deduplicated());
        assertTrue(isSameFile(dirs.sharedAssetsPool().resolve("objects").resolve("cd").resolve("cdef456"), asset));
    }

    @Test
    void migrateRefusesToDeduplicateFilesThatDifferInSize(@TempDir Path root) throws IOException {
        // Shouldn't happen (asset names are hashes, library paths pin a version), but picking a
        // winner here would silently corrupt an install - so it must leave both alone.
        AppDirs dirs = new AppDirs(root);
        SharedAssetsLinker linker = new SharedAssetsLinker(dirs);

        Path pooled = dirs.sharedLibrariesPool().resolve("libraries").resolve("lib.jar");
        Files.createDirectories(pooled.getParent());
        Files.writeString(pooled, "pooled-version");

        Path instanceDir = dirs.instance("pack");
        Path ownCopy = instanceDir.resolve("libraries").resolve("lib.jar");
        Files.createDirectories(ownCopy.getParent());
        Files.writeString(ownCopy, "a completely different length of content");

        SharedAssetsLinker.MigrationResult result = linker.migrate(instanceDir, AssetStorageMode.LAUNCHER_SHARED);

        assertEquals(0, result.deduplicated());
        assertEquals("pooled-version", Files.readString(pooled));
        assertEquals("a completely different length of content", Files.readString(ownCopy));
    }

    @Test
    void migrateIsIdempotent(@TempDir Path root) throws IOException {
        AppDirs dirs = new AppDirs(root);
        SharedAssetsLinker linker = new SharedAssetsLinker(dirs);
        Path instanceDir = dirs.instance("pack");
        Path asset = instanceDir.resolve("assets").resolve("objects").resolve("ab").resolve("abc");
        Files.createDirectories(asset.getParent());
        Files.writeString(asset, "content");

        linker.migrate(instanceDir, AssetStorageMode.LAUNCHER_SHARED);
        SharedAssetsLinker.MigrationResult second = linker.migrate(instanceDir, AssetStorageMode.LAUNCHER_SHARED);

        assertEquals(0, second.pooled());
        assertEquals(0, second.deduplicated());
        assertEquals("content", Files.readString(asset));
    }

    @Test
    void migrateNeverPoolsNatives(@TempDir Path root) throws IOException {
        AppDirs dirs = new AppDirs(root);
        SharedAssetsLinker linker = new SharedAssetsLinker(dirs);
        Path instanceDir = dirs.instance("pack");
        Path nativeFile = instanceDir.resolve("natives").resolve("liblwjgl.so");
        Files.createDirectories(nativeFile.getParent());
        Files.writeString(nativeFile, "native");

        linker.migrate(instanceDir, AssetStorageMode.LAUNCHER_SHARED);

        assertFalse(Files.exists(dirs.sharedLibrariesPool().resolve("natives").resolve("liblwjgl.so")));
        assertTrue(Files.exists(nativeFile));
    }

    @Test
    void migrateToPerModpackBreaksTheSharingIntoIndependentCopies(@TempDir Path root) throws IOException {
        AppDirs dirs = new AppDirs(root);
        SharedAssetsLinker linker = new SharedAssetsLinker(dirs);
        Path instanceDir = dirs.instance("pack");
        Path asset = instanceDir.resolve("assets").resolve("objects").resolve("ab").resolve("abc");
        Files.createDirectories(asset.getParent());
        Files.writeString(asset, "content");

        // First share it...
        linker.migrate(instanceDir, AssetStorageMode.LAUNCHER_SHARED);
        Path pooled = dirs.sharedAssetsPool().resolve("objects").resolve("ab").resolve("abc");
        assertTrue(isSameFile(pooled, asset), "precondition: shared");

        // ...then ask for the per-modpack option to actually take effect.
        SharedAssetsLinker.MigrationResult result = linker.migrate(instanceDir, AssetStorageMode.PER_MODPACK);

        assertEquals(1, result.privatised());
        assertFalse(isSameFile(pooled, asset), "the instance should now own its own copy");
        assertEquals("content", Files.readString(asset), "content must survive un-sharing");
        assertEquals("content", Files.readString(pooled), "and so must the pooled copy");
    }

    @Test
    void preSeedAndAbsorbReportSuccessWhenHardlinkingWorks(@TempDir Path root) throws IOException {
        AppDirs dirs = new AppDirs(root);
        SharedAssetsLinker linker = new SharedAssetsLinker(dirs);
        Path instanceDir = dirs.instance("pack");
        Path asset = instanceDir.resolve("assets").resolve("objects").resolve("ab").resolve("abc");
        Files.createDirectories(asset.getParent());
        Files.writeString(asset, "content");

        assertTrue(linker.preSeed(instanceDir, AssetStorageMode.LAUNCHER_SHARED));
        assertTrue(linker.absorb(instanceDir, AssetStorageMode.LAUNCHER_SHARED));
    }

    @Test
    void perModpackModeAlwaysReportsSuccessSinceItSharesNothing(@TempDir Path root) {
        AppDirs dirs = new AppDirs(root);
        SharedAssetsLinker linker = new SharedAssetsLinker(dirs);

        assertTrue(linker.preSeed(dirs.instance("pack"), AssetStorageMode.PER_MODPACK));
        assertTrue(linker.absorb(dirs.instance("pack"), AssetStorageMode.PER_MODPACK));
    }

    /**
     * Regression test for a real bug: the libraries pool used to be a deny-list ({@code assets},
     * {@code natives}) applied over the whole instance, so it also pooled {@code mods/},
     * {@code config/} and {@code saves/}. Two packs with byte-identical world files then became the
     * same physical file, where an in-place write from one pack would corrupt the other's save.
     */
    @Test
    void neverSharesModsConfigsOrWorldsBetweenModpacks(@TempDir Path root) throws IOException {
        AppDirs dirs = new AppDirs(root);
        SharedAssetsLinker linker = new SharedAssetsLinker(dirs);

        for (String slug : new String[]{"pack-a", "pack-b"}) {
            Path instance = dirs.instance(slug);
            writeFile(instance.resolve("config").resolve("shared.cfg"), "identical-default-config");
            writeFile(instance.resolve("saves").resolve("world").resolve("level.dat"), "identical-fresh-world");
            writeFile(instance.resolve("mods").resolve("common.jar"), "identical-mod-bytes");
            // The one thing that legitimately should be shared, as a control.
            writeFile(instance.resolve("libraries").resolve("com").resolve("lib.jar"), "identical-library");

            linker.absorb(instance, AssetStorageMode.LAUNCHER_SHARED);
            linker.migrate(instance, AssetStorageMode.LAUNCHER_SHARED);
        }

        Path a = dirs.instance("pack-a");
        Path b = dirs.instance("pack-b");

        assertFalse(isSameFile(a.resolve("config").resolve("shared.cfg"), b.resolve("config").resolve("shared.cfg")),
                "configs must never be shared between modpacks");
        assertFalse(isSameFile(a.resolve("saves").resolve("world").resolve("level.dat"), b.resolve("saves").resolve("world").resolve("level.dat")),
                "WORLD SAVES must never be shared between modpacks");
        assertFalse(isSameFile(a.resolve("mods").resolve("common.jar"), b.resolve("mods").resolve("common.jar")),
                "mods must never be shared between modpacks");

        assertTrue(isSameFile(a.resolve("libraries").resolve("com").resolve("lib.jar"), b.resolve("libraries").resolve("com").resolve("lib.jar")),
                "vanilla libraries, on the other hand, are exactly what should be shared");

        // And nothing that shouldn't be shared may even exist in the pool.
        assertFalse(Files.exists(dirs.sharedLibrariesPool().resolve("mods")));
        assertFalse(Files.exists(dirs.sharedLibrariesPool().resolve("config")));
        assertFalse(Files.exists(dirs.sharedLibrariesPool().resolve("saves")));
    }

    @Test
    void migrateRepairsFilesAnEarlierBuildWronglyShared(@TempDir Path root) throws IOException {
        // Simulates the on-disk damage left by the buggy build: a save hardlinked into the pool.
        AppDirs dirs = new AppDirs(root);
        SharedAssetsLinker linker = new SharedAssetsLinker(dirs);

        Path pooledSave = dirs.sharedLibrariesPool().resolve("saves").resolve("world").resolve("level.dat");
        writeFile(pooledSave, "world-data");

        Path instance = dirs.instance("pack-a");
        Path instanceSave = instance.resolve("saves").resolve("world").resolve("level.dat");
        Files.createDirectories(instanceSave.getParent());
        Files.createLink(instanceSave, pooledSave);
        assertTrue(isSameFile(pooledSave, instanceSave), "precondition: wrongly shared");

        SharedAssetsLinker.MigrationResult result = linker.migrate(instance, AssetStorageMode.LAUNCHER_SHARED);

        assertEquals(1, result.privatised());
        assertFalse(isSameFile(pooledSave, instanceSave), "the save should have been given its own copy back");
        assertEquals("world-data", Files.readString(instanceSave), "content must survive the repair");
    }

    private static void writeFile(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    /** True when both paths are the same physical file (i.e. one is a hardlink to the other), not merely equal in content. */
    private static boolean isSameFile(Path a, Path b) throws IOException {
        return Files.exists(a) && Files.exists(b) && Files.isSameFile(a, b);
    }
}
