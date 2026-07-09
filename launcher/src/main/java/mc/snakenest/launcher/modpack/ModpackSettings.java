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

    /** Fallback when a modpack doesn't declare its own {@code ModpackManifest#defaultMemoryMb}. */
    public static final int DEFAULT_MEMORY_MB = 2048;

    public static ModpackSettings defaults() {
        return new ModpackSettings(DEFAULT_MEMORY_MB, "");
    }

    /**
     * The curator's recommended starting point for a modpack the player has never customized
     * settings for yet - see {@code ModpackManifest#defaultMemoryMb}/{@code #defaultJvmArgs}.
     * {@code extraJvmArgs} may be {@code null} (never set by the curator); normalized to "" to
     * match {@link #extraJvmArgs()}'s "empty string, never null" contract.
     */
    public static ModpackSettings defaults(int memoryMb, String extraJvmArgs) {
        return new ModpackSettings(memoryMb, extraJvmArgs != null ? extraJvmArgs : "");
    }
}
