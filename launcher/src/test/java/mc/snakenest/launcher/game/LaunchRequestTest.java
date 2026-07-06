package mc.snakenest.launcher.game;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LaunchRequestTest {

    @Test
    void toStringNeverContainsTheToken() {
        String secretToken = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef";
        LaunchRequest request = new LaunchRequest(
                "SnakeTest",
                OfflineUuids.forUsername("SnakeTest"),
                Path.of("/tmp/instance"),
                "1.20.4",
                ModLoader.FORGE,
                "49.0.30",
                secretToken
        );

        String text = request.toString();

        assertFalse(text.contains(secretToken));
        assertTrue(text.contains("SnakeTest"));
        assertTrue(text.contains("redacted"));
    }
}
