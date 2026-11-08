package mc.snakenest.launcher.bootstrap;

/**
 * Thrown when {@code /api/launcher-auth/releases/launcher/latest} rejects the request (401 - no
 * signature at all, or 403 - signed but not eligible) because the resolved distribution channel
 * is restricted - see {@code Api\LauncherReleaseController::rejectUnlessChannelAccessible} in the
 * sibling {@code SnakeN3stLogin} repo. Distinguished from a plain {@link BootstrapException} so
 * {@link BootstrapMain} can offer the three-way fallback (default channel / reconnect / last
 * successful jar) instead of just showing a generic error.
 */
final class RestrictedChannelException extends BootstrapException {

    RestrictedChannelException(String message) {
        super(message);
    }
}
