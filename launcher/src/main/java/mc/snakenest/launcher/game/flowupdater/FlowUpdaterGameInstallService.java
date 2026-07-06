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
            case FORGE -> new ForgeVersionBuilder().withForgeVersion(request.loaderVersion()).build();
            case FABRIC -> new FabricVersionBuilder().withFabricVersion(request.loaderVersion()).build();
            case NEOFORGE -> new NeoForgeVersionBuilder().withNeoForgeVersion(request.loaderVersion()).build();
            case UNKNOWN -> throw new IllegalStateException("Unknown mod loader - the manifest declared a loader value this launcher build doesn't recognize");
        };
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
