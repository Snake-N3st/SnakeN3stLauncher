package mc.snakenest.launcher.game.openlauncherlib;

import fr.flowarg.openlauncherlib.NoFramework;
import fr.theshark34.openlauncherlib.minecraft.AuthInfos;
import fr.theshark34.openlauncherlib.minecraft.GameFolder;
import mc.snakenest.launcher.game.GameLaunchException;
import mc.snakenest.launcher.game.GameLaunchService;
import mc.snakenest.launcher.game.LaunchRequest;
import mc.snakenest.launcher.game.ModLoader;

import java.util.List;

/**
 * The only class in the launcher allowed to import
 * {@code fr.theshark34.openlauncherlib.*}/{@code fr.flowarg.openlauncherlib.*}
 * (GPL-3.0) - everything else depends only on {@link GameLaunchService}.
 * Uses {@code NoFramework}, this library's version-agnostic launch API
 * (reads the actual installed version JSON rather than requiring a
 * hardcoded {@code GameType} per Minecraft version range), paired with
 * {@link GameFolder#FLOW_UPDATER} to match the directory layout
 * {@code FlowUpdaterGameInstallService} already wrote.
 *
 * <p><b>Not yet exercised against a real launch in this environment</b> -
 * same caveat as {@code FlowUpdaterGameInstallService}: every signature
 * used here was individually confirmed against the actual 3.2.11 jar, but
 * the full install-then-launch flow is still unverified end-to-end.
 */
public final class OpenLauncherLibGameLaunchService implements GameLaunchService {

    @Override
    public Process launch(LaunchRequest request) throws GameLaunchException {
        try {
            AuthInfos authInfos = new AuthInfos(request.username(), "0", request.offlineUuid().toString());

            NoFramework noFramework = new NoFramework(request.instanceDir(), authInfos, GameFolder.FLOW_UPDATER);
            noFramework.setAdditionalVmArgs(List.of("-Dsn3.token=" + request.sn3TokenHex()));

            return noFramework.launch(request.mcVersion(), request.loaderVersion(), toNoFrameworkModLoader(request.loader()));
        } catch (Exception e) {
            throw new GameLaunchException("Could not launch " + request.mcVersion() + " (" + request.loader() + ")", e);
        }
    }

    private NoFramework.ModLoader toNoFrameworkModLoader(ModLoader loader) {
        return switch (loader) {
            case VANILLA -> NoFramework.ModLoader.VANILLA;
            case FORGE -> NoFramework.ModLoader.FORGE;
            case FABRIC -> NoFramework.ModLoader.FABRIC;
            case NEOFORGE -> NoFramework.ModLoader.NEO_FORGE;
            case UNKNOWN -> throw new IllegalStateException("Unknown mod loader - the manifest declared a loader value this launcher build doesn't recognize");
        };
    }
}
