package mc.snakenest.launcher.modpack;

/**
 * Per-modpack launcher preferences the player can tune from the "Gerer"
 * action ({@code ui.modpack.ModpackDetailPage}): how much memory to give the
 * JVM, and any extra JVM arguments. Persisted by {@link ModpackSettingsStore},
 * one small JSON file per modpack slug.
 *
 * @param memoryMb     heap size passed as {@code -Xmx<memoryMb>M}
 * @param extraJvmArgs raw, space-separated extra JVM args appended after the
 *                     memory flag and the launcher's own {@code -Dsn3.token};
 *                     empty string (not null) when unset
 */
public record ModpackSettings(int memoryMb, String extraJvmArgs) {

    public static final int DEFAULT_MEMORY_MB = 2048;

    public static ModpackSettings defaults() {
        return new ModpackSettings(DEFAULT_MEMORY_MB, "");
    }
}
