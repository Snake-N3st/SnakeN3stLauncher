package mc.snakenest.launcher.game;

/**
 * Installs vanilla Minecraft and, if requested, a mod loader, into an
 * instance directory. Blocking/synchronous - callers must run this off the
 * UI thread themselves.
 */
public interface GameInstallService {

    void install(InstallRequest request, GameInstallListener listener) throws GameInstallException;
}
