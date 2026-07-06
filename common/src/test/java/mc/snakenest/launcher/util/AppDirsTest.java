package mc.snakenest.launcher.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppDirsTest {

    @Test
    void subdirectoriesNestUnderRoot(@TempDir Path root) {
        AppDirs dirs = new AppDirs(root);

        assertEquals(root, dirs.root());
        assertEquals(root.resolve("cache"), dirs.cache());
        assertEquals(root.resolve("cache").resolve("launcher"), dirs.cacheLauncher());
        assertEquals(root.resolve("instances"), dirs.instances());
        assertEquals(root.resolve("instances").resolve("aventure-ultime"), dirs.instance("aventure-ultime"));
        assertEquals(root.resolve("secure"), dirs.secure());
        assertEquals(root.resolve("logs"), dirs.logs());
        assertEquals(root.resolve("config.json"), dirs.configFile());
    }

    @Test
    void windowsRootUsesAppData() {
        Path resolved = AppDirs.resolveRootForTesting("Windows 11", "/home/someone", "C:\\Users\\someone\\AppData\\Roaming");

        assertEquals(Path.of("C:\\Users\\someone\\AppData\\Roaming", "snake-n3st"), resolved);
    }

    @Test
    void macRootUsesApplicationSupport() {
        Path resolved = AppDirs.resolveRootForTesting("Mac OS X", "/Users/someone", null);

        assertEquals(Path.of("/Users/someone", "Library", "Application Support", "snake-n3st"), resolved);
    }

    @Test
    void linuxRootUsesXdgStyleLocalShare() {
        Path resolved = AppDirs.resolveRootForTesting("Linux", "/home/someone", null);

        assertEquals(Path.of("/home/someone", ".local", "share", "snake-n3st"), resolved);
    }
}
