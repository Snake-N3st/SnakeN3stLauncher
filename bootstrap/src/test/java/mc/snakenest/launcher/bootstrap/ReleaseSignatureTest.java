package mc.snakenest.launcher.bootstrap;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpServer;
import mc.snakenest.launcher.crypto.Ed25519KeyPair;
import mc.snakenest.launcher.crypto.SignatureVerificationException;
import mc.snakenest.launcher.crypto.SignedArtifactStatement;
import mc.snakenest.launcher.util.Sha256;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The signature policy, end to end through {@link LauncherReleaseClient#fetchLatestChecked}, against a local
 * server that serves whatever JSON a test puts in it - signed by real Ed25519 keys, because a fake verifier
 * would only prove the fake agrees with itself.
 *
 * <p>Mostly this is the list of things that must be refused: a signature from a key that is not pinned, one
 * made for a different type, one over different metadata, one that vanishes after a valid one was seen.
 */
class ReleaseSignatureTest {

    private static final Gson GSON = new Gson();

    private static final Ed25519KeyPair LAUNCHER_KEY = Ed25519KeyPair.fromSeed(seed(1));
    private static final Ed25519KeyPair BOOTSTRAP_KEY = Ed25519KeyPair.fromSeed(seed(2));
    private static final Ed25519KeyPair ATTACKER_KEY = Ed25519KeyPair.fromSeed(seed(3));

    private static final String JAR_SHA = Sha256.hex("jar".getBytes(StandardCharsets.UTF_8));
    private static final String DEB_SHA = Sha256.hex("deb".getBytes(StandardCharsets.UTF_8));

    private final AtomicReference<String> launcherJson = new AtomicReference<>();
    private final AtomicReference<String> bootstrapJson = new AtomicReference<>();

    private HttpServer server;
    private URI base;
    private TrustState trust;

    private static byte[] seed(int n) {
        byte[] seed = new byte[32];
        seed[0] = (byte) n;
        seed[31] = (byte) (n * 7);
        return seed;
    }

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        serve("/api/launcher-auth/releases/launcher/latest", launcherJson);
        serve("/api/launcher-auth/releases/bootstrap/latest", bootstrapJson);
        server.start();
        trust = TrustState.inMemory();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private void serve(String path, AtomicReference<String> body) {
        server.createContext(path, exchange -> {
            byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
    }

    private LauncherReleaseClient client(TrustedSigningKeys keys) {
        return new LauncherReleaseClient(base, "test-client", new ArtifactVerifier(keys), trust);
    }

    private LauncherReleaseClient pinned() {
        return client(TrustedSigningKeys.of(List.of(LAUNCHER_KEY.publicKey()), List.of(BOOTSTRAP_KEY.publicKey())));
    }

    private static Map<String, Object> signature(Ed25519KeyPair key, String type, String version, String sha, long size) {
        byte[] statement = SignedArtifactStatement.build(type, version, sha, size);
        Map<String, Object> signature = new LinkedHashMap<>();
        signature.put("alg", "ed25519");
        signature.put("value", key.signHex(statement));
        signature.put("key", "indicative-only");
        return signature;
    }

    private static Map<String, Object> release(String version, String sha, long size, Map<String, Object> signature) {
        Map<String, Object> release = new LinkedHashMap<>();
        release.put("version", version);
        release.put("sha256", sha);
        release.put("size", size);
        release.put("changelog", "test");
        release.put("download_url", "http://example.invalid/download");
        if (signature != null) {
            release.put("signature", signature);
        }
        return release;
    }

    private void launcher(Map<String, Object> release) {
        launcherJson.set(GSON.toJson(release));
    }

    private void bootstrap(Map<String, Object> release) {
        bootstrapJson.set(GSON.toJson(release));
    }

    private LauncherReleaseClient.Checked fetchLauncher() throws Exception {
        return pinned().fetchLatestChecked(ReleaseType.LAUNCHER, null, null);
    }

    private LauncherReleaseClient.Checked fetchBootstrap() throws Exception {
        return pinned().fetchLatestChecked(ReleaseType.BOOTSTRAP, null, null);
    }

    // --- the good path --------------------------------------------------------------------------

    @Test
    void aValidSignatureIsVerifiedAndRememberedAsSeen() throws Exception {
        launcher(release("2.4.0", JAR_SHA, 100, signature(LAUNCHER_KEY, "launcher", "2.4.0", JAR_SHA, 100)));

        LauncherReleaseClient.Checked checked = fetchLauncher();

        assertEquals(SignatureStatus.VERIFIED, checked.status());
        assertTrue(trust.hasSeenValid("launcher"));
        assertEquals("2.4.0", trust.highestVersion("launcher"));
        assertNull(checked.debStatus(), "a launcher release has no package");
    }

    @Test
    void aBootstrapIsVerifiedAgainstTheBootstrapKey() throws Exception {
        bootstrap(release("2.4.0", JAR_SHA, 100, signature(BOOTSTRAP_KEY, "bootstrap", "2.4.0", JAR_SHA, 100)));

        assertEquals(SignatureStatus.VERIFIED, fetchBootstrap().status());
        assertTrue(trust.hasSeenValid("bootstrap"));
        assertFalse(trust.hasSeenValid("launcher"), "each type is tracked on its own");
    }

    @Test
    void fetchLatestStillReturnsThePlainReleaseForCallersThatDoNotCare() throws Exception {
        launcher(release("2.4.0", JAR_SHA, 100, signature(LAUNCHER_KEY, "launcher", "2.4.0", JAR_SHA, 100)));

        assertEquals("2.4.0", pinned().fetchLatest(ReleaseType.LAUNCHER, null, null).version());
    }

    // --- what must be refused -------------------------------------------------------------------

    @Test
    void aSignatureFromAKeyThatIsNotPinnedIsRefused() {
        launcher(release("2.4.0", JAR_SHA, 100, signature(ATTACKER_KEY, "launcher", "2.4.0", JAR_SHA, 100)));

        assertThrows(SignatureVerificationException.class, this::fetchLauncher);
        assertFalse(trust.hasSeenValid("launcher"), "a refused release must not count as a verified one");
    }

    @Test
    void theKeyFieldInTheResponseCannotChooseTheTrustAnchor() {
        // The attacker signs with their own key and labels it with nothing useful: only the pinned keys count.
        Map<String, Object> forged = signature(ATTACKER_KEY, "launcher", "2.4.0", JAR_SHA, 100);
        forged.put("key", LAUNCHER_KEY.publicKeyHex());
        launcher(release("2.4.0", JAR_SHA, 100, forged));

        assertThrows(SignatureVerificationException.class, this::fetchLauncher);
    }

    @Test
    void aLauncherSignatureCannotBeReplayedAsABootstrap() {
        // Same version, digest and size; signed by the launcher key for type=launcher, presented as a bootstrap.
        bootstrap(release("2.4.0", JAR_SHA, 100, signature(LAUNCHER_KEY, "launcher", "2.4.0", JAR_SHA, 100)));

        assertThrows(SignatureVerificationException.class, this::fetchBootstrap);
    }

    @Test
    void aSignatureMadeWithTheRightKeyForTheWrongTypeIsRefused() {
        // Even signed by the bootstrap key itself, a statement that says type=launcher is no bootstrap.
        bootstrap(release("2.4.0", JAR_SHA, 100, signature(BOOTSTRAP_KEY, "launcher", "2.4.0", JAR_SHA, 100)));

        assertThrows(SignatureVerificationException.class, this::fetchBootstrap);
    }

    @Test
    void changingAnySignedFieldAfterSigningIsRefused() {
        Map<String, Object> good = signature(LAUNCHER_KEY, "launcher", "2.4.0", JAR_SHA, 100);

        launcher(release("2.3.0", JAR_SHA, 100, good));
        assertThrows(SignatureVerificationException.class, this::fetchLauncher, "version");

        launcher(release("2.4.0", DEB_SHA, 100, good));
        assertThrows(SignatureVerificationException.class, this::fetchLauncher, "digest");

        launcher(release("2.4.0", JAR_SHA, 101, good));
        assertThrows(SignatureVerificationException.class, this::fetchLauncher, "size");
    }

    @Test
    void aSignatureThatIsNotValidHexOrTheWrongLengthIsRefused() {
        for (String value : new String[] {"zz".repeat(64), "00".repeat(63), "00".repeat(65), "", "0"}) {
            Map<String, Object> bad = signature(LAUNCHER_KEY, "launcher", "2.4.0", JAR_SHA, 100);
            bad.put("value", value);
            launcher(release("2.4.0", JAR_SHA, 100, bad));

            assertThrows(SignatureVerificationException.class, this::fetchLauncher, value);
        }
    }

    @Test
    void anAlgorithmThisClientDoesNotKnowIsRefusedNotIgnored() {
        Map<String, Object> other = signature(LAUNCHER_KEY, "launcher", "2.4.0", JAR_SHA, 100);
        other.put("alg", "rsa");
        launcher(release("2.4.0", JAR_SHA, 100, other));

        assertThrows(SignatureVerificationException.class, this::fetchLauncher);
    }

    @Test
    void anUppercaseDigestFromTheServerIsRefusedNotNormalised() {
        // The site always emits lowercase; "fixing" it here would verify a statement it never signed.
        launcher(release("2.4.0", JAR_SHA.toUpperCase(), 100, signature(LAUNCHER_KEY, "launcher", "2.4.0", JAR_SHA, 100)));

        assertThrows(SignatureVerificationException.class, this::fetchLauncher);
    }

    @Test
    void aVersionWithANewlineInItIsRefused() {
        launcher(release("2.4.0\nsize=1", JAR_SHA, 100, signature(LAUNCHER_KEY, "launcher", "2.4.0", JAR_SHA, 100)));

        assertThrows(SignatureVerificationException.class, this::fetchLauncher);
    }

    // --- missing signatures: the HSTS rule ------------------------------------------------------

    @Test
    void anUnsignedReleaseIsAWarningWhileNoSignatureHasEverBeenSeen() throws Exception {
        launcher(release("2.4.0", JAR_SHA, 100, null));

        assertEquals(SignatureStatus.UNSIGNED, fetchLauncher().status());
        assertFalse(trust.hasSeenValid("launcher"), "warning is not trust");
    }

    @Test
    void anUnsignedReleaseAfterAValidOneWasSeenIsADowngradeAndIsRefused() throws Exception {
        launcher(release("2.4.0", JAR_SHA, 100, signature(LAUNCHER_KEY, "launcher", "2.4.0", JAR_SHA, 100)));
        assertEquals(SignatureStatus.VERIFIED, fetchLauncher().status());

        // The attacker strips the signature to fall back to the clickable warning.
        launcher(release("2.4.1", JAR_SHA, 100, null));

        assertThrows(SignatureVerificationException.class, this::fetchLauncher);
    }

    @Test
    void theDowngradeRuleIsPerType() throws Exception {
        launcher(release("2.4.0", JAR_SHA, 100, signature(LAUNCHER_KEY, "launcher", "2.4.0", JAR_SHA, 100)));
        fetchLauncher();

        // Bootstrap has never been seen signed, so an unsigned one is still only a warning.
        bootstrap(release("2.4.0", JAR_SHA, 100, null));
        assertEquals(SignatureStatus.UNSIGNED, fetchBootstrap().status());
    }

    @Test
    void anOlderButValidlySignedVersionIsAcceptedWithAWarningNotRefused() throws Exception {
        launcher(release("2.4.0", JAR_SHA, 100, signature(LAUNCHER_KEY, "launcher", "2.4.0", JAR_SHA, 100)));
        fetchLauncher();

        // A release withdrawn from the site makes an older one "latest" again: legitimate, so not a refusal.
        launcher(release("2.3.0", JAR_SHA, 100, signature(LAUNCHER_KEY, "launcher", "2.3.0", JAR_SHA, 100)));

        assertEquals(SignatureStatus.VERIFIED, fetchLauncher().status());
        assertEquals("2.4.0", trust.highestVersion("launcher"), "the high-water mark does not go down");
    }

    // --- a build with nothing pinned ------------------------------------------------------------

    @Test
    void aBuildWithNoPinnedKeysChecksNothingAndNeverRefuses() throws Exception {
        LauncherReleaseClient unpinned = client(TrustedSigningKeys.none());

        launcher(release("2.4.0", JAR_SHA, 100, null));
        assertEquals(SignatureStatus.NOT_CHECKED, unpinned.fetchLatestChecked(ReleaseType.LAUNCHER, null, null).status());

        Map<String, Object> garbage = signature(ATTACKER_KEY, "launcher", "2.4.0", JAR_SHA, 100);
        launcher(release("2.4.0", JAR_SHA, 100, garbage));
        assertEquals(SignatureStatus.NOT_CHECKED, unpinned.fetchLatestChecked(ReleaseType.LAUNCHER, null, null).status());
    }

    @Test
    void aServerCannotDeclareItselfVerified() throws Exception {
        // Extra fields in the JSON are just ignored: nothing parsed can stand in for a verdict.
        Map<String, Object> claim = release("2.4.0", JAR_SHA, 100, null);
        claim.put("status", "VERIFIED");
        claim.put("verified", true);
        launcher(claim);

        assertEquals(SignatureStatus.UNSIGNED, fetchLauncher().status());
    }

    // --- the Debian package ---------------------------------------------------------------------

    private Map<String, Object> deb(String version, Map<String, Object> signature) {
        Map<String, Object> deb = new LinkedHashMap<>();
        deb.put("version", version);
        deb.put("sha256", DEB_SHA);
        deb.put("size", 50);
        deb.put("download_url", "http://example.invalid/deb");
        if (signature != null) {
            deb.put("signature", signature);
        }
        return deb;
    }

    private void bootstrapWithDeb(Map<String, Object> deb) {
        Map<String, Object> release = release("2.4.0", JAR_SHA, 100, signature(BOOTSTRAP_KEY, "bootstrap", "2.4.0", JAR_SHA, 100));
        release.put("deb", deb);
        bootstrap(release);
    }

    @Test
    void aValidPackageSignatureIsVerifiedWithTheBootstrapKey() throws Exception {
        bootstrapWithDeb(deb("2.4.0", signature(BOOTSTRAP_KEY, "deb", "2.4.0", DEB_SHA, 50)));

        LauncherReleaseClient.Checked checked = fetchBootstrap();

        assertEquals(SignatureStatus.VERIFIED, checked.status());
        assertEquals(SignatureStatus.VERIFIED, checked.debStatus());
        assertTrue(trust.hasSeenValid("deb"));
    }

    @Test
    void aWrongPackageSignatureRefusesOnlyThePackageNotTheJar() throws Exception {
        bootstrapWithDeb(deb("2.4.0", signature(ATTACKER_KEY, "deb", "2.4.0", DEB_SHA, 50)));

        LauncherReleaseClient.Checked checked = fetchBootstrap();

        assertEquals(SignatureStatus.VERIFIED, checked.status(), "the jar carries its own signature");
        assertEquals(SignatureStatus.REFUSED, checked.debStatus());
        assertFalse(trust.hasSeenValid("deb"));
    }

    @Test
    void aPackageRecordedWithoutItsVersionCannotBeVerifiedAndIsUnsignedNotRefused() throws Exception {
        // The site's transition: a package built before it recorded the bundled version.
        bootstrapWithDeb(deb(null, null));

        assertEquals(SignatureStatus.UNSIGNED, fetchBootstrap().debStatus());
    }

    @Test
    void aPackageSignatureReplayedForAnotherVersionIsRefused() throws Exception {
        bootstrapWithDeb(deb("2.4.0", signature(BOOTSTRAP_KEY, "deb", "2.3.0", DEB_SHA, 50)));

        assertEquals(SignatureStatus.REFUSED, fetchBootstrap().debStatus());
    }

    @Test
    void aBootstrapJarSignatureCannotVouchForThePackage() throws Exception {
        // type=bootstrap over the deb's own digest and version: right key, wrong statement type.
        bootstrapWithDeb(deb("2.4.0", signature(BOOTSTRAP_KEY, "bootstrap", "2.4.0", DEB_SHA, 50)));

        assertEquals(SignatureStatus.REFUSED, fetchBootstrap().debStatus());
    }

    @Test
    void anUnsignedPackageIsRefusedOnceASignedOneWasSeen() throws Exception {
        bootstrapWithDeb(deb("2.4.0", signature(BOOTSTRAP_KEY, "deb", "2.4.0", DEB_SHA, 50)));
        assertEquals(SignatureStatus.VERIFIED, fetchBootstrap().debStatus());

        bootstrapWithDeb(deb("2.4.0", null));

        assertEquals(SignatureStatus.REFUSED, fetchBootstrap().debStatus());
    }

    // --- persistence ----------------------------------------------------------------------------

    @Test
    void whatWasSeenSurvivesARestart(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("trust-state.json");
        trust = TrustState.load(file);
        launcher(release("2.4.0", JAR_SHA, 100, signature(LAUNCHER_KEY, "launcher", "2.4.0", JAR_SHA, 100)));
        fetchLauncher();

        // A new process: the state is read back from disk, and the stripped signature is refused.
        trust = TrustState.load(file);
        launcher(release("2.4.1", JAR_SHA, 100, null));

        assertThrows(SignatureVerificationException.class, this::fetchLauncher);
    }
}
