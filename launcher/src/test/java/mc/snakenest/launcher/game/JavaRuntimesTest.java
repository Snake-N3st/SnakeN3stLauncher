package mc.snakenest.launcher.game;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaRuntimesTest {

    // --- resolve: the two-tier rule ---------------------------------------------------------

    @Test
    void aModpacksOwnChoiceWinsOverTheLauncherDefault() {
        assertEquals("/modpack/java", JavaRuntimes.resolve("/modpack/java", "/default/java"));
    }

    @Test
    void fallsBackToTheLauncherDefaultWhenTheModpackHasNoChoice() {
        assertEquals("/default/java", JavaRuntimes.resolve(null, "/default/java"));
        assertEquals("/default/java", JavaRuntimes.resolve("", "/default/java"));
        assertEquals("/default/java", JavaRuntimes.resolve("   ", "/default/java"));
    }

    /**
     * Null out of {@code resolve} is what preserves the behaviour that shipped before this setting
     * existed: OpenLauncherLib then derives the binary from {@code java.home}, i.e. the launcher's
     * own JVM. Anything else here - a guessed path, "java" on the PATH - would silently change
     * which JVM every existing install runs Minecraft on.
     */
    @Test
    void nothingConfiguredAnywhereMeansTheLaunchersOwnJvm() {
        assertNull(JavaRuntimes.resolve(null, null));
        assertNull(JavaRuntimes.resolve("", "  "));
    }

    @Test
    void trimsSurroundingWhitespace() {
        assertEquals("/modpack/java", JavaRuntimes.resolve("  /modpack/java  ", null));
        assertEquals("/default/java", JavaRuntimes.resolve(null, "\t/default/java\n"));
    }

    // --- describe: actually running the candidate --------------------------------------------

    @Test
    void describesTheJvmRunningTheseTests() throws IOException {
        String description = JavaRuntimes.describe(JavaRuntimes.launcherJavaBinary());

        // Every JVM prints something like: openjdk version "21.0.3" 2024-04-16
        assertTrue(description.toLowerCase().contains("version"),
                "expected a version banner, got: " + description);
    }

    @Test
    void rejectsAPathThatDoesNotExist(@TempDir Path dir) {
        Path missing = dir.resolve("nope");

        assertThrows(IOException.class, () -> JavaRuntimes.describe(missing.toString()));
    }

    @Test
    void rejectsADirectory(@TempDir Path dir) {
        assertThrows(IOException.class, () -> JavaRuntimes.describe(dir.toString()));
    }

    @Test
    void rejectsBlankInput() {
        assertThrows(IOException.class, () -> JavaRuntimes.describe(null));
        assertThrows(IOException.class, () -> JavaRuntimes.describe("   "));
    }

    /**
     * The case a file-exists check would wave through: a real, executable file that simply is not a
     * JVM. This is why {@code describe} runs the candidate instead of stat-ing it.
     */
    @Test
    void rejectsAnExecutableThatIsNotAJvm(@TempDir Path dir) throws Exception {
        Path fake = dir.resolve("java");
        Files.writeString(fake, "#!/bin/sh\nexit 0\n");

        if (!fake.toFile().setExecutable(true)) {
            return; // Filesystem without an executable bit (rare); nothing to assert here.
        }

        // Exits 0 like a real binary, but prints no version - which is exactly what has to be
        // caught, and cannot be caught without executing it.
        assertThrows(IOException.class, () -> JavaRuntimes.describe(fake.toString()));
    }

    @Test
    void launcherJavaBinaryPointsAtAnExistingFile() {
        assertTrue(Files.isRegularFile(Path.of(JavaRuntimes.launcherJavaBinary())));
    }
}
