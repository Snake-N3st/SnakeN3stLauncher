package mc.snakenest.launcher.bootstrap;

/**
 * Checked on purpose: {@code BootstrapMain} must decide how to report a failure and exit. Not
 * {@code final} - see {@link RestrictedChannelException}, which needs to be catchable
 * specifically while still being a {@code BootstrapException} for every existing catch site that
 * doesn't care about the distinction.
 */
public class BootstrapException extends Exception {

    public BootstrapException(String message) {
        super(message);
    }

    public BootstrapException(String message, Throwable cause) {
        super(message, cause);
    }
}
