package mc.snakenest.launcher.game.flowupdater;

import fr.flowarg.flowupdater.FlowUpdater;
import fr.flowarg.flowupdater.download.IProgressCallback;
import fr.flowarg.flowupdater.download.DownloadList;
import fr.flowarg.flowupdater.download.Step;
import fr.flowarg.flowupdater.versions.IModLoaderVersion;
import fr.flowarg.flowupdater.versions.VanillaVersion;
import fr.flowarg.flowupdater.versions.fabric.FabricVersionBuilder;
import fr.flowarg.flowupdater.versions.forge.ForgeVersionBuilder;
import fr.flowarg.flowupdater.versions.neoforge.NeoForgeVersionBuilder;
import mc.snakenest.launcher.game.GameInstallException;
import mc.snakenest.launcher.game.GameInstallListener;
import mc.snakenest.launcher.game.GameInstallService;
import mc.snakenest.launcher.game.InstallRequest;
import mc.snakenest.launcher.game.InstallStep;

/**
 * The only class in the launcher allowed to import {@code fr.flowarg.flowupdater.*}
 * (GPL-3.0) - everything else depends only on {@link GameInstallService}.
 * Downloads vanilla Minecraft and, if requested, installs Forge/Fabric/NeoForge.
 *
 * <p>Vanilla installs are supported as-is - the in-game auth bridge (the
 * "universalpacket" mod, Fabric+Forge only, see {@code LAUNCHER_INTEGRATION.md}
 * section 5) won't be present for a vanilla install, so that player won't get
 * the passwordless in-game login, but nothing about the install/launch itself
 * is broken by that. Only reject a mod loader here if it actually fails to
 * install/launch, not preemptively.
 *
 * <p><b>Not yet exercised against a real Minecraft install in this
 * environment</b> (that would mean downloading a full game distribution) -
 * every method signature used here was individually confirmed against the
 * actual 1.9.4 jar via {@code javap}/decompiled source, not guessed, but the
 * full install flow itself is still unverified end-to-end. Worth a real
 * manual smoke test (install + launch one real modpack) before shipping.
 */
public final class FlowUpdaterGameInstallService implements GameInstallService {

    @Override
    public void install(InstallRequest request, GameInstallListener listener) throws GameInstallException {
        try {
            VanillaVersion vanilla = new VanillaVersion.VanillaVersionBuilder()
                    .withName(request.mcVersion())
                    .build();

            FlowUpdater.FlowUpdaterBuilder builder = new FlowUpdater.FlowUpdaterBuilder()
                    .withVanillaVersion(vanilla)
                    .withProgressCallback(adaptCallback(listener));

            IModLoaderVersion modLoader = buildModLoaderVersion(request);
            if (modLoader != null) {
                builder.withModLoaderVersion(modLoader);
            }

            FlowUpdater updater = builder.build();
            updater.update(request.instanceDir());
        } catch (Exception e) {
            // Catches both update()'s checked Exception and the builders' unchecked BuilderException -
            // callers only ever need to handle our own GameInstallException, never FlowUpdater's types.
            throw new GameInstallException("Could not install " + request.mcVersion() + " (" + request.loader() + ")", e);
        }
    }

    private IModLoaderVersion buildModLoaderVersion(InstallRequest request) {
        return switch (request.loader()) {
            case VANILLA -> null;
            case FORGE -> new ForgeVersionBuilder().withForgeVersion(mcVersionPrefixed(request)).build();
            case FABRIC -> new FabricVersionBuilder().withFabricVersion(request.loaderVersion()).build();
            case NEOFORGE -> new NeoForgeVersionBuilder().withNeoForgeVersion(neoForgeVersion(request)).build();
            case UNKNOWN -> throw new IllegalStateException("Unknown mod loader - the manifest declared a loader value this launcher build doesn't recognize");
        };
    }

    /**
     * FlowUpdater's {@code ForgeVersion} splits its input on {@code "-"} and expects exactly
     * {@code "<mcVersion>-<forgeBuild>"} (e.g. {@code "1.20.1-47.2.20"}) - our manifest's
     * {@code loaderVersion} only carries the forge build number on its own (e.g.
     * {@code "47.2.20"}), which crashed FlowUpdater with an
     * {@code ArrayIndexOutOfBoundsException} (it indexes {@code data[1]} after splitting).
     * Prefixing it here, rather than requiring the site to store the combined string, keeps
     * "what does loaderVersion mean" the same across Forge/Fabric/NeoForge.
     */
    private String mcVersionPrefixed(InstallRequest request) {
        String prefix = request.mcVersion() + "-";
        return request.loaderVersion().startsWith(prefix) ? request.loaderVersion() : prefix + request.loaderVersion();
    }

    /**
     * Only the very first NeoForge release (for 1.20.1, back when it had just forked from
     * Forge) used the same {@code "<mcVersion>-<build>"} format as old Forge; every later
     * NeoForge version is self-contained (e.g. {@code "20.4.237"}) and must be passed as-is.
     */
    private String neoForgeVersion(InstallRequest request) {
        return "1.20.1".equals(request.mcVersion()) ? mcVersionPrefixed(request) : request.loaderVersion();
    }

    private IProgressCallback adaptCallback(GameInstallListener listener) {
        return new IProgressCallback() {
            @Override
            public void step(Step step) {
                listener.onStepStarted(mapStep(step));
            }

            @Override
            public void update(DownloadList.DownloadInfo info) {
                listener.onProgress(info.getDownloadedBytes(), info.getTotalToDownloadBytes());
            }
        };
    }

    private InstallStep mapStep(Step step) {
        return switch (step) {
            case READ -> InstallStep.READING_VERSION_INFO;
            case DL_LIBS -> InstallStep.DOWNLOADING_LIBRARIES;
            case DL_ASSETS -> InstallStep.DOWNLOADING_ASSETS;
            case EXTRACT_NATIVES -> InstallStep.EXTRACTING_NATIVES;
            case MOD_LOADER -> InstallStep.INSTALLING_MOD_LOADER;
            case END -> InstallStep.DONE;
            case INTEGRATION, MOD_PACK, MODS, EXTERNAL_FILES, POST_EXECUTIONS -> InstallStep.FINALIZING;
        };
    }
}
