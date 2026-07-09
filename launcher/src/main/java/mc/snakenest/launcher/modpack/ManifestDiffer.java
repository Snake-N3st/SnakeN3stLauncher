package mc.snakenest.launcher.modpack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Pure diff logic (no I/O): decides which files need downloading and which
 * need deleting to bring an instance directory from its previously-applied
 * manifest to a target one. Diffing is always by <b>hash</b>, never by
 * version string or by path alone - a file whose content didn't change is
 * never re-downloaded, even across version bumps that touched other files.
 *
 * <p>{@link #PROTECTED_PATHS} is the one exception to pure hash-diffing - see its Javadoc.
 */
public final class ManifestDiffer {

    /**
     * Player-owned Minecraft state files, seeded once but never touched again by a regular sync,
     * regardless of what their hash does in later modpack versions - only an explicit "Réparer"
     * (which clears the locally-recorded manifest before diffing, so these paths look untracked
     * again) resets them. A modpack curator's uploaded zip is usually a snapshot of their own
     * local instance, which commonly contains these files simply because they ran the game to
     * build/test the pack - without this exception, an unrelated pack update that happens to
     * catch a fresh copy of the curator's own {@code options.txt}/{@code servers.dat} would
     * silently overwrite every player's personalized settings/server list on their very next
     * launch, since a sync runs before every launch, not just on explicit updates.
     */
    private static final Set<String> PROTECTED_PATHS = Set.of("options.txt", "servers.dat");

    private ManifestDiffer() {
    }

    public static SyncPlan diff(Optional<StoredManifest> previous, ModpackManifest target) {
        Map<String, String> previousPathToHash = previous.map(StoredManifest::pathToHash).orElseGet(Map::of);

        List<ManifestFile> toDownload = new ArrayList<>();
        for (ManifestFile file : target.files()) {
            boolean alreadyTracked = previousPathToHash.containsKey(file.path());
            if (PROTECTED_PATHS.contains(file.path())) {
                // Seed it only if this is the first time it's ever been synced for this
                // install - once tracked, its hash is never re-checked.
                if (!alreadyTracked) {
                    toDownload.add(file);
                }
                continue;
            }
            if (!file.hash().equals(previousPathToHash.get(file.path()))) {
                toDownload.add(file);
            }
        }

        java.util.Set<String> targetPaths = new java.util.HashSet<>();
        for (ManifestFile file : target.files()) {
            targetPaths.add(file.path());
        }

        List<String> toDelete = new ArrayList<>();
        for (String previousPath : previousPathToHash.keySet()) {
            if (PROTECTED_PATHS.contains(previousPath)) {
                continue;
            }
            if (!targetPaths.contains(previousPath)) {
                toDelete.add(previousPath);
            }
        }

        return new SyncPlan(toDownload, toDelete);
    }
}
