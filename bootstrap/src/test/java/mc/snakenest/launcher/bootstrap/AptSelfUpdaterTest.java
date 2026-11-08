package mc.snakenest.launcher.bootstrap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the two decisions that can be checked without a package manager: whether this install is
 * apt-managed at all, and what exactly gets run as root once it is. Actually invoking apt is left
 * to a real machine - see the class's own note on why every failure path there is best-effort.
 */
class AptSelfUpdaterTest {

    private static final String MARKER = "sn3.aptInstall";

    @AfterEach
    void clearMarker() {
        System.clearProperty(MARKER);
    }

    /**
     * The default for every other install shape - a portable zip, the Windows installer, a jar a
     * player dropped in their home directory - none of which ship the marker file.
     */
    @Test
    void isNotAnAptInstallWithoutTheMarker() {
        assertFalse(AptSelfUpdater.isAptInstall());
    }

    @Test
    void isAnAptInstallWhenThePackageSaysSo() {
        System.setProperty(MARKER, "true");

        assertTrue(AptSelfUpdater.isAptInstall());
    }

    /**
     * The marker is a hand-written line in a file shipped by the package; anything that is not a
     * plain "true" has to read as "no", never as "probably yes". Getting this wrong would send a
     * non-apt install down the pkexec path and pop an authentication window at a player who has no
     * package to install.
     */
    @Test
    void anythingOtherThanTrueIsNotAnAptInstall() {
        for (String value : new String[]{"", "1", "yes", "oui", "false", "no"}) {
            System.setProperty(MARKER, value);
            assertFalse(AptSelfUpdater.isAptInstall(), "marker value \"" + value + "\"");
        }
    }

    /**
     * The seam the whole feature hangs on: the package ships a bootstrap.properties next to its jar
     * (see the site's DebPackageGenerator) and nothing else does. This is the exact file it writes,
     * byte for byte - if the key were ever renamed on one side only, every .deb install would
     * quietly go back to keeping a private per-user copy and nobody would notice.
     */
    @Test
    void readsTheMarkerOutOfThePropertiesFileThePackageInstalls(@TempDir Path installDir) throws Exception {
        Files.writeString(installDir.resolve("bootstrap.properties"), """
                # Installe par le paquet snake-n3st-launcher - ne pas modifier.
                sn3.aptInstall=true
                """);

        BootstrapMain.applyPropertiesFrom(installDir);

        assertTrue(AptSelfUpdater.isAptInstall());
    }

    @Test
    void aDirectoryWithNoPropertiesFileLeavesTheMarkerUnset(@TempDir Path installDir) {
        BootstrapMain.applyPropertiesFrom(installDir);

        assertFalse(AptSelfUpdater.isAptInstall());
    }

    @Test
    void runsAptDirectlyWhenNothingNeedsElevating() {
        List<String> command = AptSelfUpdater.installCommand(List.of(), Path.of("/tmp/x/launcher.deb"));

        assertEquals(List.of("apt-get", "install", "-y", "/tmp/x/launcher.deb"), command);
    }

    @Test
    void putsTheElevationInFrontOfApt() {
        List<String> command = AptSelfUpdater.installCommand(List.of("/usr/bin/pkexec"), Path.of("/tmp/x/launcher.deb"));

        assertEquals(List.of("/usr/bin/pkexec", "apt-get", "install", "-y", "/tmp/x/launcher.deb"), command);
    }

    /**
     * apt only treats an argument as a file rather than a package name when it looks like a path.
     * A relative one that lost its "./" would be read as the name of a package to fetch from the
     * configured repositories - which is emphatically not the freshly verified file this is meant
     * to install.
     */
    @Test
    void alwaysPassesAnAbsolutePathToApt() {
        List<String> command = AptSelfUpdater.installCommand(List.of(), Path.of("launcher.deb"));

        String argument = command.get(command.size() - 1);
        assertTrue(argument.startsWith("/"), "expected an absolute path, got " + argument);
        assertTrue(argument.endsWith("/launcher.deb"), argument);
    }
}
