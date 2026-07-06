package mc.snakenest.launcher.game;

/** Checked on purpose: the UI must decide how to show an install failure. Never exposes the underlying installer library's exception types. */
public final class GameInstallException extends Exception {

    public GameInstallException(String message, Throwable cause) {
        super(message, cause);
    }
}
