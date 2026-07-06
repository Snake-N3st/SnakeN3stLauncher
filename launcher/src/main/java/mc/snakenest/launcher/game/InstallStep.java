package mc.snakenest.launcher.game;

/**
 * Coarse-grained install phases shown to the user, mapped from whichever
 * underlying installer library is doing the actual work
 * ({@code game.flowupdater.FlowUpdaterGameInstallService} maps FlowUpdater's
 * own {@code Step} enum onto this one) - kept separate so the rest of the
 * app never needs to know that library's vocabulary.
 */
public enum InstallStep {
    READING_VERSION_INFO,
    DOWNLOADING_LIBRARIES,
    DOWNLOADING_ASSETS,
    EXTRACTING_NATIVES,
    INSTALLING_MOD_LOADER,
    FINALIZING,
    DONE
}
