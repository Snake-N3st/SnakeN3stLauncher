package mc.snakenest.launcher.bootstrap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrustStateTest {

    @Test
    void startsEmpty(@TempDir Path dir) {
        TrustState state = TrustState.load(dir.resolve(TrustState.FILE_NAME));

        assertFalse(state.hasSeenValid("launcher"));
        assertNull(state.highestVersion("launcher"));
    }

    @Test
    void remembersAcrossInstances(@TempDir Path dir) {
        Path file = dir.resolve(TrustState.FILE_NAME);
        TrustState.load(file).recordValid("launcher", "2.4.0");

        TrustState later = TrustState.load(file);

        assertTrue(later.hasSeenValid("launcher"));
        assertEquals("2.4.0", later.highestVersion("launcher"));
        assertFalse(later.hasSeenValid("bootstrap"));
    }

    @Test
    void theHighWaterMarkOnlyGoesUp(@TempDir Path dir) {
        TrustState state = TrustState.load(dir.resolve(TrustState.FILE_NAME));
        state.recordValid("launcher", "2.4.0");
        state.recordValid("launcher", "2.3.0");
        state.recordValid("launcher", "2.10.0");

        assertEquals("2.10.0", state.highestVersion("launcher"), "compared numerically, not as strings");
    }

    @Test
    void aCorruptFileReadsAsEmptyAndIsReplacedOnTheNextWrite(@TempDir Path dir) throws Exception {
        Path file = dir.resolve(TrustState.FILE_NAME);
        Files.writeString(file, "{ this is not json");

        TrustState state = TrustState.load(file);
        assertFalse(state.hasSeenValid("launcher"));

        state.recordValid("launcher", "2.4.0");
        assertTrue(TrustState.load(file).hasSeenValid("launcher"));
    }

    @Test
    void aFileWithTheWrongShapeReadsAsEmpty(@TempDir Path dir) throws Exception {
        Path file = dir.resolve(TrustState.FILE_NAME);

        for (String content : new String[] {"", "null", "[]", "{}", "{\"seen\":null}", "{\"seen\":\"x\"}"}) {
            Files.writeString(file, content);
            assertFalse(TrustState.load(file).hasSeenValid("launcher"), content);
        }
    }

    @Test
    void failingToWriteNeverThrows(@TempDir Path dir) throws Exception {
        // The data directory's "file" is a regular file, so nothing can be written beneath it.
        Path blocker = Files.writeString(dir.resolve("blocker"), "x");
        TrustState state = TrustState.load(blocker.resolve(TrustState.FILE_NAME));

        state.recordValid("launcher", "2.4.0");

        assertTrue(state.hasSeenValid("launcher"), "still remembered for this run");
    }

    @Test
    void inMemoryKeepsNothingOnDisk() {
        TrustState state = TrustState.inMemory();
        state.recordValid("launcher", "2.4.0");

        assertTrue(state.hasSeenValid("launcher"));
    }
}
