package mc.snakenest.launcher.ui.modpack;

/** Data + callbacks for {@link ModpackDetailPage}. Holds no logic of its own. */
public record ModpackDetailViewModel(
        String name,
        String description,
        String changelog,
        long totalSizeBytes,
        int fileCount,
        Runnable onDemarrer,
        Runnable onOpenSettings,
        Runnable onOpenFolder,
        Runnable onBack
) {
}
