package mc.snakenest.launcher.game;

import java.nio.file.Path;

/**
 * What to install and where - built directly from a modpack version's
 * manifest fields ({@code modpack.ModpackManifest}).
 *
 * @param loaderVersion ignored when {@code loader} is {@link ModLoader#VANILLA}
 */
public record InstallRequest(String mcVersion, ModLoader loader, String loaderVersion, Path instanceDir) {
}
