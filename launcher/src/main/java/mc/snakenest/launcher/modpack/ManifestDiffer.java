package mc.snakenest.launcher.modpack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Pure diff logic (no I/O): decides which files need downloading and which
 * need deleting to bring an instance directory from its previously-applied
 * manifest to a target one. Diffing is always by <b>hash</b>, never by
 * version string or by path alone - a file whose content didn't change is
 * never re-downloaded, even across version bumps that touched other files.
 */
public final class ManifestDiffer {

    private ManifestDiffer() {
    }

    public static SyncPlan diff(Optional<StoredManifest> previous, ModpackManifest target) {
        Map<String, String> previousPathToHash = previous.map(StoredManifest::pathToHash).orElseGet(Map::of);

        List<ManifestFile> toDownload = new ArrayList<>();
        for (ManifestFile file : target.files()) {
            String previousHash = previousPathToHash.get(file.path());
            if (!file.hash().equals(previousHash)) {
                toDownload.add(file);
            }
        }

        java.util.Set<String> targetPaths = new java.util.HashSet<>();
        for (ManifestFile file : target.files()) {
            targetPaths.add(file.path());
        }

        List<String> toDelete = new ArrayList<>();
        for (String previousPath : previousPathToHash.keySet()) {
            if (!targetPaths.contains(previousPath)) {
                toDelete.add(previousPath);
            }
        }

        return new SyncPlan(toDownload, toDelete);
    }
}
