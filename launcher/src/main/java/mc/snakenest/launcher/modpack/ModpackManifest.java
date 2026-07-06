package mc.snakenest.launcher.modpack;

import com.google.gson.annotations.SerializedName;
import mc.snakenest.launcher.game.ModLoader;

import java.util.List;

/**
 * {@code GET /api/modpacks/{slug}/manifest} - mirrors
 * ModpackManifestController::show(). {@code files} is the tree to diff
 * against the local install <b>by hash</b>, never by version string.
 */
public record ModpackManifest(
        String version,
        String changelog,
        @SerializedName("total_size") long totalSize,
        @SerializedName("mc_version") String mcVersion,
        String loader,
        @SerializedName("loader_version") String loaderVersion,
        List<ManifestFile> files
) {
    public ModLoader modLoader() {
        return ModLoader.fromApiValue(loader);
    }
}
