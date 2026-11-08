package mc.snakenest.launcher.bootstrap;

import mc.snakenest.launcher.crypto.Ed25519Verifier;
import mc.snakenest.launcher.crypto.SignedArtifactStatement;
import mc.snakenest.launcher.util.Hex;

/**
 * Checks one release's signature against the keys this build pins. Pure: no I/O, no state, no policy -
 * it says what the signature *is*, and the caller ({@link LauncherReleaseClient}) decides what to do about it.
 *
 * <p><b>What is verified.</b> The client rebuilds the statement {@code sn3.artifact.v1 / type / version /
 * sha256 / size} from the values it is about to use and checks the signature against it. It never receives the
 * statement from the server: a statement the server chose would only prove the server agrees with itself.
 * Because the digest is in it, and the download is later written through {@code AtomicFiles.writeVerified}
 * against that very digest, the bytes that land on disk are the bytes that were signed - no second pass over a
 * multi-megabyte jar, and the metadata is authenticated before a single byte is fetched.
 *
 * <p>The verdict is deliberately three-way. {@link Verdict#INVALID} covers everything that is present and
 * wrong, including a signature in an algorithm this client does not know and a statement that cannot be built
 * from the values it was given: none of those is ever legitimate.
 */
final class ArtifactVerifier {

    enum Verdict { OK, MISSING, INVALID }

    private static final String ALGORITHM = "ed25519";

    private final TrustedSigningKeys keys;

    ArtifactVerifier(TrustedSigningKeys keys) {
        this.keys = keys;
    }

    /** Verifies nothing: what a build with no pinned keys, and every test that predates signatures, runs with. */
    static ArtifactVerifier disabled() {
        return new ArtifactVerifier(TrustedSigningKeys.none());
    }

    static ArtifactVerifier production() {
        return new ArtifactVerifier(TrustedSigningKeys.fromSystemProperties());
    }

    /** Whether this build has any key to verify {@code type} with. When not, there is nothing to decide. */
    boolean isConfiguredFor(String type) {
        return keys.isConfiguredFor(type);
    }

    Verdict check(String type, String version, String sha256, long size, LauncherReleaseInfo.Signature signature) {
        if (signature == null) {
            return Verdict.MISSING;
        }

        if (!ALGORITHM.equals(signature.alg()) || signature.value() == null) {
            return Verdict.INVALID;
        }

        byte[] statement;
        byte[] raw;

        try {
            statement = SignedArtifactStatement.build(type, version, sha256, size);
            raw = Hex.decode(signature.value());
        } catch (IllegalArgumentException e) {
            // An off-format digest or version, or a signature that is not hex: present, and unusable.
            return Verdict.INVALID;
        }

        // Every pinned key, whatever `signature.key()` claims: that field is indicative, and letting it pick
        // the key would hand the choice of trust anchor to whoever writes the response.
        for (byte[] key : keys.keysFor(type)) {
            if (Ed25519Verifier.verify(key, statement, raw)) {
                return Verdict.OK;
            }
        }

        return Verdict.INVALID;
    }
}
