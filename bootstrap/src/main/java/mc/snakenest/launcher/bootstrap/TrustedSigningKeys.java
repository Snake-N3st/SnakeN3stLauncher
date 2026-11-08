package mc.snakenest.launcher.bootstrap;

import mc.snakenest.launcher.crypto.Ed25519Verifier;
import mc.snakenest.launcher.crypto.SignedArtifactStatement;
import mc.snakenest.launcher.util.Hex;
import mc.snakenest.launcher.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The public keys this build trusts, per artifact type.
 *
 * <p><b>Why they are read from properties, not compiled in.</b> A constant would pin one site's keys into the
 * source, and a build run against any other site - a test server has its own vault, hence its own keys - would
 * refuse every one of its artifacts. The site bakes its keys into the {@code jvm.prop} resource of each
 * bootstrap it publishes ({@code sn3.signing.launcherKeys}, {@code sn3.signing.bootstrapKeys}), next to the
 * {@code sn3.clientId} and {@code sn3.baseUrl} it already puts there.
 *
 * <p>That is as strong as a constant where it matters. A bootstrap only ever replaces itself with a release
 * verified against the keys it already holds, so a compromised site cannot ship a jar with keys of its own
 * choosing without first signing it with a key this installation pins; and a *first* install is trusted on
 * first use whichever way the keys arrive. It also makes rotation free of special machinery: publish a
 * bootstrap, signed by the old key, whose {@code jvm.prop} lists the old and the new one.
 *
 * <p>Each value is a comma-separated list of 64-hex Ed25519 public keys. {@code launcher} artifacts are
 * verified against the launcher keys; {@code bootstrap} and {@code deb} against the bootstrap keys - the
 * split the site's vault enforces, mirrored so that a launcher key cannot vouch for a bootstrap.
 *
 * <p>A build with no keys for a type verifies nothing for it ({@link #keysFor} is empty). A malformed entry
 * is skipped with an error in the log, and if that leaves none the type is treated as unconfigured rather
 * than as unverifiable: this project's rule is to warn rather than block when a signature cannot be checked,
 * and failing closed on a typo would turn one into a fleet-wide outage.
 */
final class TrustedSigningKeys {

    static final String LAUNCHER_PROPERTY = "sn3.signing.launcherKeys";
    static final String BOOTSTRAP_PROPERTY = "sn3.signing.bootstrapKeys";

    /**
     * Not used by the bootstrap itself: the pin the *launcher* verifies modpack manifests against, carried by
     * this jar only so it can be handed on when the launcher is spawned. Kept next to its siblings so all the
     * pins a site bakes in are named in one place.
     */
    static final String MODPACK_ROOT_PROPERTY = "sn3.signing.modpackRootKeys";

    private final Map<String, List<byte[]>> byType;

    private TrustedSigningKeys(Map<String, List<byte[]>> byType) {
        this.byType = byType;
    }

    static TrustedSigningKeys none() {
        return new TrustedSigningKeys(Map.of());
    }

    static TrustedSigningKeys of(List<byte[]> launcher, List<byte[]> bootstrap) {
        return new TrustedSigningKeys(Map.of(
                SignedArtifactStatement.TYPE_LAUNCHER, List.copyOf(launcher),
                SignedArtifactStatement.TYPE_BOOTSTRAP, List.copyOf(bootstrap),
                SignedArtifactStatement.TYPE_DEB, List.copyOf(bootstrap)));
    }

    /** The keys this JVM was given, read from the system properties the bundled resources populate. */
    static TrustedSigningKeys fromSystemProperties() {
        return fromProperties(System::getProperty);
    }

    static TrustedSigningKeys fromProperties(Function<String, String> property) {
        List<byte[]> launcher = parse(LAUNCHER_PROPERTY, property.apply(LAUNCHER_PROPERTY));
        List<byte[]> bootstrap = parse(BOOTSTRAP_PROPERTY, property.apply(BOOTSTRAP_PROPERTY));

        return new TrustedSigningKeys(Map.of(
                SignedArtifactStatement.TYPE_LAUNCHER, launcher,
                SignedArtifactStatement.TYPE_BOOTSTRAP, bootstrap,
                SignedArtifactStatement.TYPE_DEB, bootstrap));
    }

    /** The keys that may sign {@code type}; empty when this build pins none for it. */
    List<byte[]> keysFor(String type) {
        return byType.getOrDefault(type, List.of());
    }

    boolean isConfiguredFor(String type) {
        return !keysFor(type).isEmpty();
    }

    private static List<byte[]> parse(String property, String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }

        List<byte[]> keys = new ArrayList<>();

        for (String entry : value.split(",")) {
            String hex = entry.trim();

            if (hex.isEmpty()) {
                continue;
            }

            try {
                byte[] key = Hex.decode(hex);

                if (hex.length() != 2 * Ed25519Verifier.PUBLIC_KEY_BYTES) {
                    throw new IllegalArgumentException("not " + Ed25519Verifier.PUBLIC_KEY_BYTES + " bytes");
                }

                keys.add(key);
            } catch (IllegalArgumentException e) {
                Log.error(TrustedSigningKeys.class, "Ignoring a malformed key in " + property + ": " + e.getMessage(), null);
            }
        }

        return List.copyOf(keys);
    }
}
