package mc.snakenest.launcher.bootstrap;

/**
 * What the client concluded about an artifact's signature - the soft outcomes. The hard ones (a signature
 * that is wrong, or gone after one was seen) are exceptions and never reach a caller that could ignore them;
 * see {@link LauncherReleaseClient#fetchLatestChecked}.
 */
enum SignatureStatus {

    /** The signature is valid for exactly this type, version, digest and size. Carry on, silently. */
    VERIFIED,

    /**
     * No signature, and none has ever been verified on this installation. This is the transition: warn
     * loudly, let the player go on. It stops being possible the moment one valid signature has been seen
     * ({@link TrustState}), which is what makes it safe to allow at all.
     */
    UNSIGNED,

    /** This build pins no key for this artifact, so nothing was checked (a dev build, an unbuilt checkout). */
    NOT_CHECKED,

    /**
     * The Debian package only: its signature is wrong or gone. Unlike the jar this does not abort the whole
     * check - the jar it carries is verified on its own - it only rules out installing that package.
     */
    REFUSED
}
