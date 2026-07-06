package mc.snakenest.launcher.modpack;

import java.util.List;

/** What {@link ManifestDiffer} decided needs to change to reach the target manifest. */
public record SyncPlan(List<ManifestFile> toDownload, List<String> toDelete) {
}
