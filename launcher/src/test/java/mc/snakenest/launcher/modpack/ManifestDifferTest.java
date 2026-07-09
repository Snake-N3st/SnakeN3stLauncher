package mc.snakenest.launcher.modpack;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManifestDifferTest {

    private static ModpackManifest manifest(ManifestFile... files) {
        return new ModpackManifest("1.0.0", null, 0, "1.20.4", "vanilla", null, List.of(files), null, ModpackSettings.DEFAULT_MEMORY_MB);
    }

    @Test
    void freshInstallDownloadsEverythingAndDeletesNothing() {
        ModpackManifest target = manifest(
                new ManifestFile("mods/a.jar", "hash-a", 10),
                new ManifestFile("mods/b.jar", "hash-b", 20)
        );

        SyncPlan plan = ManifestDiffer.diff(Optional.empty(), target);

        assertEquals(2, plan.toDownload().size());
        assertTrue(plan.toDelete().isEmpty());
    }

    @Test
    void identicalManifestProducesAnEmptyPlan() {
        ManifestFile file = new ManifestFile("mods/a.jar", "hash-a", 10);
        ModpackManifest target = manifest(file);
        StoredManifest previous = new StoredManifest("1.0.0", Map.of("mods/a.jar", "hash-a"));

        SyncPlan plan = ManifestDiffer.diff(Optional.of(previous), target);

        assertTrue(plan.toDownload().isEmpty());
        assertTrue(plan.toDelete().isEmpty());
    }

    @Test
    void onlyTheChangedFileIsRedownloaded() {
        ModpackManifest target = manifest(
                new ManifestFile("mods/a.jar", "hash-a-v2", 10),
                new ManifestFile("mods/b.jar", "hash-b", 20)
        );
        StoredManifest previous = new StoredManifest("1.0.0", Map.of(
                "mods/a.jar", "hash-a-v1",
                "mods/b.jar", "hash-b"
        ));

        SyncPlan plan = ManifestDiffer.diff(Optional.of(previous), target);

        assertEquals(List.of(new ManifestFile("mods/a.jar", "hash-a-v2", 10)), plan.toDownload());
        assertTrue(plan.toDelete().isEmpty());
    }

    @Test
    void fileRemovedInNewVersionIsDeleted() {
        ModpackManifest target = manifest(new ManifestFile("mods/a.jar", "hash-a", 10));
        StoredManifest previous = new StoredManifest("1.0.0", Map.of(
                "mods/a.jar", "hash-a",
                "mods/removed.jar", "hash-removed"
        ));

        SyncPlan plan = ManifestDiffer.diff(Optional.of(previous), target);

        assertTrue(plan.toDownload().isEmpty());
        assertEquals(List.of("mods/removed.jar"), plan.toDelete());
    }

    @Test
    void renamedFileIsDownloadedAtTheNewPathAndDeletedAtTheOldOne() {
        ModpackManifest target = manifest(new ManifestFile("mods/new-name.jar", "hash-a", 10));
        StoredManifest previous = new StoredManifest("1.0.0", Map.of("mods/old-name.jar", "hash-a"));

        SyncPlan plan = ManifestDiffer.diff(Optional.of(previous), target);

        assertEquals(List.of(new ManifestFile("mods/new-name.jar", "hash-a", 10)), plan.toDownload());
        assertEquals(List.of("mods/old-name.jar"), plan.toDelete());
    }

    @Test
    void protectedFileIsSeededOnFreshInstall() {
        ModpackManifest target = manifest(new ManifestFile("options.txt", "hash-a", 10));

        SyncPlan plan = ManifestDiffer.diff(Optional.empty(), target);

        assertEquals(List.of(new ManifestFile("options.txt", "hash-a", 10)), plan.toDownload());
    }

    @Test
    void protectedFileIsNeverRedownloadedOnceTrackedEvenIfItsHashChangesUpstream() {
        ModpackManifest target = manifest(new ManifestFile("options.txt", "hash-a-v2", 10));
        StoredManifest previous = new StoredManifest("1.0.0", Map.of("options.txt", "hash-a-v1"));

        SyncPlan plan = ManifestDiffer.diff(Optional.of(previous), target);

        assertTrue(plan.toDownload().isEmpty());
    }

    @Test
    void protectedFileIsNeverDeletedEvenIfDroppedFromTheTargetManifest() {
        ModpackManifest target = manifest();
        StoredManifest previous = new StoredManifest("1.0.0", Map.of("servers.dat", "hash-a"));

        SyncPlan plan = ManifestDiffer.diff(Optional.of(previous), target);

        assertTrue(plan.toDelete().isEmpty());
    }

    @Test
    void protectedFileIsReseededAfterReparerClearsTheLocalManifest() {
        ModpackManifest target = manifest(new ManifestFile("servers.dat", "hash-a-v2", 10));

        // "Réparer" clears the locally-recorded manifest before diffing (see
        // LocalManifestStore#clear) - simulated here the same way, Optional.empty().
        SyncPlan plan = ManifestDiffer.diff(Optional.empty(), target);

        assertEquals(List.of(new ManifestFile("servers.dat", "hash-a-v2", 10)), plan.toDownload());
    }
}
