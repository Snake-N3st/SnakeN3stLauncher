package mc.snakenest.launcher.game;

/** Checked on purpose: the UI must decide how to show a launch failure. Never exposes the underlying library's exception types. */
public final class GameLaunchException extends Exception {

    public GameLaunchException(String message, Throwable cause) {
        super(message, cause);
    }
}
