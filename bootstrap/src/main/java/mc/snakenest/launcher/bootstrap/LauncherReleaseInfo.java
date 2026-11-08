package mc.snakenest.launcher.bootstrap;

import com.google.gson.annotations.SerializedName;

/**
 * Mirrors {@code GET /api/launcher-auth/releases/latest} - see LAUNCHER_INTEGRATION.md section 8.
 *
 * @param deb       the Debian package carrying this very bootstrap, or {@code null} - the site only sends
 *                  it for a bootstrap release, and only once a package has actually been generated. Read
 *                  solely by {@link AptSelfUpdater}; every other install shape ignores it.
 * @param signature the site's Ed25519 signature of this release, or {@code null} while the site does not
 *                  sign (before its vault exists, or for a release it could not sign). Its absence is a
 *                  state in its own right, not an error - see {@link ArtifactVerifier} for what each
 *                  situation means. <b>Nothing in a parsed instance says whether it was verified</b>: a
 *                  server could put any field it likes in the JSON, so verification results live in
 *                  {@link LauncherReleaseClient.Checked}, computed client-side and never parsed.
 */
public record LauncherReleaseInfo(
        String version,
        String sha256,
        long size,
        String changelog,
        @SerializedName("download_url") String downloadUrl,
        DebPackage deb,
        Signature signature
) {

    /** Constructor for the shape this record had before signatures existed - callers that build one by hand. */
    public LauncherReleaseInfo(String version, String sha256, long size, String changelog, String downloadUrl, DebPackage deb) {
        this(version, sha256, size, changelog, downloadUrl, deb, null);
    }

    /**
     * The .deb published alongside a bootstrap release. It bundles the same jar bytes this release
     * is (see the site's {@code DebPackageGenerator}), so once it is installed the packaged jar
     * hashes to {@link LauncherReleaseInfo#sha256()} - which is what lets {@link AptSelfUpdater}'s
     * caller confirm the install really landed instead of trusting an exit code.
     *
     * @param version   the bootstrap version this package bundles - part of what its signature covers, and
     *                  not derivable from anything else the client has. {@code null} from a site that has
     *                  not recorded it yet, in which case the package cannot be verified.
     * @param signature signed with the bootstrap key, statement {@code type=deb}
     */
    public record DebPackage(
            String sha256,
            long size,
            @SerializedName("download_url") String downloadUrl,
            String version,
            Signature signature
    ) {

        public DebPackage(String sha256, long size, String downloadUrl) {
            this(sha256, size, downloadUrl, null, null);
        }
    }

    /**
     * {@code {"alg": "ed25519", "value": "<128 hex>", "key": "<fingerprint>"}}. {@code key} is indicative:
     * the client tries every key it pinned whatever this says, so a server cannot steer verification.
     */
    public record Signature(String alg, String value, String key) {
    }
}
