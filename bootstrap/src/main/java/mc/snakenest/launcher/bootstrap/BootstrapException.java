package mc.snakenest.launcher.bootstrap;

/** Checked on purpose: {@code BootstrapMain} must decide how to report a failure and exit. */
public final class BootstrapException extends Exception {

    public BootstrapException(String message) {
        super(message);
    }

    public BootstrapException(String message, Throwable cause) {
        super(message, cause);
    }
}
