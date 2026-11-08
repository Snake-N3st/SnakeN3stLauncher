package mc.snakenest.launcher.bootstrap;

import mc.snakenest.launcher.crypto.Ed25519KeyPair;
import mc.snakenest.launcher.crypto.SignedArtifactStatement;
import mc.snakenest.launcher.util.Hex;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The verifier on its own: pure, so these are the cases that do not need a server. */
class ArtifactVerifierTest {

    private static final String SHA = "e378b618ea1b20c47773f76f40aa0a8aefec499f3bb90b3a10c88e0389b4a18c";

    // The vector shared with the site (SigningStatementTest.php): seed 000102...1f.
    private static final String SHARED_PUBLIC_KEY = "03a107bff3ce10be1d70dd18e74bc09967e4d6309ba50d5f1ddc8664125531b8";
    private static final String SHARED_SIGNATURE = "19703f4adf05ff983f5139c26b3114b621b85604f826c3323cf3f6e29da41dfd"
            + "f3f3e31805232feed327c6bd8c5a163b1a2497153a2847197b95c5539d893f0f";

    private static ArtifactVerifier pinning(String bootstrapKeyHex) {
        return new ArtifactVerifier(TrustedSigningKeys.of(List.of(), List.of(Hex.decode(bootstrapKeyHex))));
    }

    private static LauncherReleaseInfo.Signature sig(String value) {
        return new LauncherReleaseInfo.Signature("ed25519", value, null);
    }

    @Test
    void theSitesOwnSignatureOfTheSharedVectorIsOk() {
        assertEquals(ArtifactVerifier.Verdict.OK,
                pinning(SHARED_PUBLIC_KEY).check("bootstrap", "2.2.0", SHA, 1234567, sig(SHARED_SIGNATURE)));
    }

    @Test
    void noSignatureIsMissingNotInvalid() {
        assertEquals(ArtifactVerifier.Verdict.MISSING, pinning(SHARED_PUBLIC_KEY).check("bootstrap", "2.2.0", SHA, 1234567, null));
    }

    @Test
    void theSameSignatureOverAnyOtherFieldIsInvalid() {
        ArtifactVerifier verifier = pinning(SHARED_PUBLIC_KEY);

        assertEquals(ArtifactVerifier.Verdict.INVALID, verifier.check("bootstrap", "2.2.1", SHA, 1234567, sig(SHARED_SIGNATURE)));
        assertEquals(ArtifactVerifier.Verdict.INVALID, verifier.check("bootstrap", "2.2.0", SHA, 1234568, sig(SHARED_SIGNATURE)));
        assertEquals(ArtifactVerifier.Verdict.INVALID, verifier.check("bootstrap", "2.2.0", "0".repeat(64), 1234567, sig(SHARED_SIGNATURE)));
        assertEquals(ArtifactVerifier.Verdict.INVALID, verifier.check("deb", "2.2.0", SHA, 1234567, sig(SHARED_SIGNATURE)));
    }

    @Test
    void anyOfSeveralPinnedKeysMayVerify() {
        Ed25519KeyPair rotated = Ed25519KeyPair.fromSeed(new byte[32]);
        byte[] statement = SignedArtifactStatement.build("bootstrap", "3.0.0", SHA, 10);
        ArtifactVerifier verifier = new ArtifactVerifier(TrustedSigningKeys.of(List.of(),
                List.of(Hex.decode(SHARED_PUBLIC_KEY), rotated.publicKey())));

        assertEquals(ArtifactVerifier.Verdict.OK, verifier.check("bootstrap", "3.0.0", SHA, 10, sig(rotated.signHex(statement))),
                "a rotation pins the old and the new key at once");
        assertEquals(ArtifactVerifier.Verdict.OK, verifier.check("bootstrap", "2.2.0", SHA, 1234567, sig(SHARED_SIGNATURE)));
    }

    @Test
    void anUnknownAlgorithmOrAMissingValueIsInvalid() {
        ArtifactVerifier verifier = pinning(SHARED_PUBLIC_KEY);

        assertEquals(ArtifactVerifier.Verdict.INVALID, verifier.check("bootstrap", "2.2.0", SHA, 1234567,
                new LauncherReleaseInfo.Signature("rsa", SHARED_SIGNATURE, null)));
        assertEquals(ArtifactVerifier.Verdict.INVALID, verifier.check("bootstrap", "2.2.0", SHA, 1234567,
                new LauncherReleaseInfo.Signature("ed25519", null, null)));
        assertEquals(ArtifactVerifier.Verdict.INVALID, verifier.check("bootstrap", "2.2.0", SHA, 1234567,
                new LauncherReleaseInfo.Signature(null, SHARED_SIGNATURE, null)));
    }

    @Test
    void reportsWhetherABuildPinsAnythingForAType() {
        ArtifactVerifier verifier = pinning(SHARED_PUBLIC_KEY);

        assertTrue(verifier.isConfiguredFor("bootstrap"));
        assertTrue(verifier.isConfiguredFor("deb"));
        assertFalse(verifier.isConfiguredFor("launcher"));
        assertFalse(ArtifactVerifier.disabled().isConfiguredFor("bootstrap"));
    }

    @Test
    void productionReadsTheKeysTheBundledResourceLeftInTheSystemProperties() {
        String before = System.getProperty(TrustedSigningKeys.BOOTSTRAP_PROPERTY);

        try {
            System.setProperty(TrustedSigningKeys.BOOTSTRAP_PROPERTY, SHARED_PUBLIC_KEY);

            ArtifactVerifier verifier = ArtifactVerifier.production();

            assertEquals(ArtifactVerifier.Verdict.OK, verifier.check("bootstrap", "2.2.0", SHA, 1234567, sig(SHARED_SIGNATURE)));
        } finally {
            if (before == null) {
                System.clearProperty(TrustedSigningKeys.BOOTSTRAP_PROPERTY);
            } else {
                System.setProperty(TrustedSigningKeys.BOOTSTRAP_PROPERTY, before);
            }
        }
    }
}
