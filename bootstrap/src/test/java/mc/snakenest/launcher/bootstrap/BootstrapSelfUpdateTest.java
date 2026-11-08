package mc.snakenest.launcher.bootstrap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the rule that decides where a bootstrap update may be written.
 *
 * <p>Getting this wrong is what broke a real machine: running the .deb-installed launcher as root
 * let the bootstrap overwrite {@code /usr/share/snake-n3st-launcher/snaken3st-bootstrap.jar} with a
 * file created under root's umask (mode 0600), after which every normal account failed with
 * "Unable to access jarfile".
 */
class BootstrapSelfUpdateTest {

    /**
     * Asserted against the pure path rule, not through isPackageManaged: these paths do not exist
     * on a test machine, so isPackageManaged would answer "true" from its writability check alone
     * and the assertion would hold even with the path rule deleted - which is exactly what a first
     * version of this test did.
     */
    @Test
    void treatsSystemDirectoriesAsPackageManaged() {
        assertTrue(BootstrapMain.isUnderSystemPrefix(Path.of("/usr/share/snake-n3st-launcher/snaken3st-bootstrap.jar")));
        assertTrue(BootstrapMain.isUnderSystemPrefix(Path.of("/usr/local/lib/sn3/bootstrap.jar")));
        assertTrue(BootstrapMain.isUnderSystemPrefix(Path.of("/opt/sn3/bootstrap.jar")));
        assertTrue(BootstrapMain.isUnderSystemPrefix(Path.of("/snap/sn3/current/bootstrap.jar")));
    }

    /**
     * The path rule is what catches root: root *can* write to /usr/share, so a writability test
     * alone would happily let it overwrite the packaged jar. This is the case the real bug came
     * from, and it is why the rule cannot be replaced by a permission check.
     */
    @Test
    void doesNotClassifyAPlayerPathAsSystemOwned(@TempDir Path dir) {
        assertFalse(BootstrapMain.isUnderSystemPrefix(dir.resolve("bootstrap.jar")));
        assertFalse(BootstrapMain.isUnderSystemPrefix(Path.of("/home/joueur/sn3/bootstrap.jar")));
        // "/usrlocal" is not "/usr/" - the trailing slash in each prefix is what stops a sibling
        // directory from being swept up.
        assertFalse(BootstrapMain.isUnderSystemPrefix(Path.of("/usrlocal/bootstrap.jar")));
    }

    @Test
    void leavesAPlayerOwnedJarSelfUpdating(@TempDir Path dir) throws Exception {
        Path jar = dir.resolve("snaken3st-bootstrap.jar");
        Files.writeString(jar, "jar");

        assertFalse(BootstrapMain.isPackageManaged(jar));
    }

    /** A read-only jar anywhere is someone else's to manage, even outside the known prefixes. */
    @Test
    @DisabledOnOs(OS.WINDOWS)
    void treatsAnUnwritableJarAsPackageManaged(@TempDir Path dir) throws Exception {
        Path jar = dir.resolve("bootstrap.jar");
        Files.writeString(jar, "jar");
        Files.setPosixFilePermissions(jar, Set.of(OWNER_READ));

        assertTrue(BootstrapMain.isPackageManaged(jar));
    }

    // --- the hash short-circuit that stops a re-download on every start ------------------------

    @Test
    void recognisesAFileThatAlreadyMatchesTheExpectedDigest(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("bootstrap.jar");
        Files.writeString(file, "hello");

        // SHA-256 of "hello".
        String sha = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824";

        assertTrue(BootstrapMain.hasSha256(file, sha));
        assertTrue(BootstrapMain.hasSha256(file, sha.toUpperCase()), "digest comparison must ignore case");
        assertFalse(BootstrapMain.hasSha256(file, "0".repeat(64)));
    }

    @Test
    void missingFileNeverMatches(@TempDir Path dir) {
        assertFalse(BootstrapMain.hasSha256(dir.resolve("absent.jar"), "0".repeat(64)));
    }

    // --- Windows: a running jar cannot be replaced ---------------------------------------------

    @Test
    void windowsCannotReplaceARunningJarOthersCan() {
        assertFalse(BootstrapMain.canReplaceRunningJar("Windows 11"));
        assertFalse(BootstrapMain.canReplaceRunningJar("Windows 10"));
        assertFalse(BootstrapMain.canReplaceRunningJar("WINDOWS SERVER 2022"));
        assertTrue(BootstrapMain.canReplaceRunningJar("Linux"));
        assertTrue(BootstrapMain.canReplaceRunningJar("Mac OS X"));
        assertTrue(BootstrapMain.canReplaceRunningJar(""));
    }

    @Test
    void theUserCopyIsNamedAfterTheDigestSoANewReleaseNeverLandsOnTheRunningFile(@TempDir Path dir) {
        String a = "e378b618ea1b20c47773f76f40aa0a8aefec499f3bb90b3a10c88e0389b4a18c";
        String b = "0".repeat(64);

        assertEquals(dir.resolve("bootstrap-e378b618ea1b20c4.jar"), BootstrapMain.userCopyPath(dir, a));
        assertEquals(dir.resolve("bootstrap-e378b618ea1b20c4.jar"), BootstrapMain.userCopyPath(dir, a.toUpperCase()));
        assertTrue(!BootstrapMain.userCopyPath(dir, a).equals(BootstrapMain.userCopyPath(dir, b)));
    }

    @Test
    void aDigestThatIsNotAPlainHashNeverBecomesAFileName(@TempDir Path dir) {
        for (String bad : new String[] {null, "", "abc", "../../etc/passwd" + "0".repeat(48), "z".repeat(64), "0".repeat(63),
                "0".repeat(65), "0".repeat(63) + "\n"}) {
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> BootstrapMain.userCopyPath(dir, bad), String.valueOf(bad));
        }
    }

    @Test
    void pruningKeepsTheNewCopyAndTheRunningOneAndNothingElse(@TempDir Path dir) throws Exception {
        Path keep = Files.writeString(dir.resolve("bootstrap-aaaaaaaaaaaaaaaa.jar"), "new");
        Path running = Files.writeString(dir.resolve("bootstrap-bbbbbbbbbbbbbbbb.jar"), "running");
        Path stale = Files.writeString(dir.resolve("bootstrap-cccccccccccccccc.jar"), "stale");
        Path legacy = Files.writeString(dir.resolve("bootstrap.jar"), "legacy");
        Path unrelated = Files.writeString(dir.resolve("launcher-2.0.0.jar"), "launcher");
        Path notJar = Files.writeString(dir.resolve("bootstrap-notes.txt"), "text");

        BootstrapMain.pruneUserCopies(dir, keep, running);

        assertTrue(Files.exists(keep));
        assertTrue(Files.exists(running));
        assertFalse(Files.exists(stale));
        assertFalse(Files.exists(legacy));
        assertTrue(Files.exists(unrelated), "cached launcher jars are not this method's business");
        assertTrue(Files.exists(notJar));
    }

    @Test
    void pruningAMissingDirectoryIsHarmless(@TempDir Path dir) {
        BootstrapMain.pruneUserCopies(dir.resolve("absent"), dir.resolve("a.jar"), dir.resolve("b.jar"));
    }
}
