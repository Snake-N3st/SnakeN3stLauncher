package mc.snakenest.launcher.modpack;

import com.google.gson.annotations.SerializedName;
import mc.snakenest.launcher.game.ModLoader;

import java.util.List;

/**
 * {@code GET /api/modpacks/{slug}/manifest} - mirrors
 * ModpackManifestController::show(). {@code files} is the tree to diff
 * against the local install <b>by hash</b>, never by version string.
 *
 * @param defaultJvmArgs      the curator's recommended extra JVM args for this modpack, {@code
 *                            null} if never set - only used to seed {@code ModpackSettings} the
 *                            first time a player opens this modpack, never overrides a player's
 *                            own saved choice
 * @param rawDefaultMemoryMb  the curator's recommended heap size in MB, same seeding-only role as
 *                            {@code defaultJvmArgs} - {@code null} if never set (or, defensively,
 *                            not positive - e.g. an older server that predates this field and
 *                            leaves it out of the JSON entirely); use {@link #defaultMemoryMb()}
 *                            rather than this raw value directly
 */
public record ModpackManifest(
        String version,
        String changelog,
        @SerializedName("total_size") long totalSize,
        @SerializedName("mc_version") String mcVersion,
        String loader,
        @SerializedName("loader_version") String loaderVersion,
        List<ManifestFile> files,
        @SerializedName("default_jvm_args") String defaultJvmArgs,
        @SerializedName("default_memory_mb") Integer rawDefaultMemoryMb
) {
    public ModLoader modLoader() {
        return ModLoader.fromApiValue(loader);
    }

    /** {@link #rawDefaultMemoryMb}, normalized to {@link ModpackSettings#DEFAULT_MEMORY_MB} when unset or not positive. */
    public int defaultMemoryMb() {
        return rawDefaultMemoryMb != null && rawDefaultMemoryMb > 0 ? rawDefaultMemoryMb : ModpackSettings.DEFAULT_MEMORY_MB;
    }
}
