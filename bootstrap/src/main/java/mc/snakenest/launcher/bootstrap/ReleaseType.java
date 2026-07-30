package mc.snakenest.launcher.bootstrap;

/**
 * Mirrors the site's {@code LauncherRelease::TYPE_LAUNCHER}/{@code TYPE_BOOTSTRAP} constants - the
 * full launcher jar this bootstrap downloads and runs, versus the bootstrap jar itself, which can
 * self-update (see {@code BootstrapMain#selfUpdateIfNeeded}). Independently versioned/published
 * artifacts, fetched through the same API shape scoped by this value.
 */
enum ReleaseType {
    LAUNCHER("launcher"),
    BOOTSTRAP("bootstrap");

    private final String apiValue;

    ReleaseType(String apiValue) {
        this.apiValue = apiValue;
    }

    /** The exact string this site's API expects/returns for this type - see {@code routes/api.php}'s {type} segment. */
    String apiValue() {
        return apiValue;
    }
}
