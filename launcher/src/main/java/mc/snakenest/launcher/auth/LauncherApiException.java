package mc.snakenest.launcher.auth;

/** Checked on purpose: callers must decide how to surface an API failure to the user. */
public final class LauncherApiException extends Exception {

    public LauncherApiException(String message) {
        super(message);
    }

    public LauncherApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
