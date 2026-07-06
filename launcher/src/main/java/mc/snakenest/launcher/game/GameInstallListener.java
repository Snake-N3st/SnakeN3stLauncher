package mc.snakenest.launcher.game;

/** Progress callbacks for {@link GameInstallService#install}. Invoked on the caller's own thread. */
public interface GameInstallListener {

    void onStepStarted(InstallStep step);

    void onProgress(long bytesDownloaded, long bytesTotal);
}
