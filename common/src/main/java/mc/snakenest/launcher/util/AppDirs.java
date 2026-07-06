package mc.snakenest.launcher.util;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Resolves the launcher's single data root and the clean, fixed layout of
 * subdirectories beneath it. Every other class that needs a path on disk
 * goes through here rather than building one by hand, so the layout only
 * needs to change in one place.
 *
 * <p>Root, by OS:
 * <ul>
 *   <li>Linux: {@code $HOME/.local/share/snake-n3st}</li>
 *   <li>macOS: {@code ~/Library/Application Support/snake-n3st}</li>
 *   <li>Windows: {@code %APPDATA%\snake-n3st}</li>
 * </ul>
 */
public final class AppDirs {

    private final Path root;

    public AppDirs() {
        this(resolveRoot());
    }

    /** Points the whole tree at an arbitrary directory - mainly useful for tests. */
    public AppDirs(Path root) {
        this.root = root;
    }

    static Path resolveRoot() {
        return resolveRootForTesting(System.getProperty("os.name", ""), System.getProperty("user.home"), System.getenv("APPDATA"));
    }

    /** Package-visible, pure version of {@link #resolveRoot()} - exercised directly by tests. */
    static Path resolveRootForTesting(String osName, String home, String windowsAppData) {
        String os = osName.toLowerCase();

        if (os.contains("win")) {
            Path base = (windowsAppData != null && !windowsAppData.isBlank())
                    ? Paths.get(windowsAppData)
                    : Paths.get(home, "AppData", "Roaming");
            return base.resolve("snake-n3st");
        }

        if (os.contains("mac") || os.contains("darwin")) {
            return Paths.get(home, "Library", "Application Support", "snake-n3st");
        }

        return Paths.get(home, ".local", "share", "snake-n3st");
    }

    public Path root() {
        return root;
    }

    public Path cache() {
        return root.resolve("cache");
    }

    /** Where the bootstrap caches downloaded launcher jars. */
    public Path cacheLauncher() {
        return cache().resolve("launcher");
    }

    public Path instances() {
        return root.resolve("instances");
    }

    /** Full, independent game directory for one modpack. */
    public Path instance(String modpackSlug) {
        return instances().resolve(modpackSlug);
    }

    /** Encrypted private key + its local wrapping key, tightened permissions. */
    public Path secure() {
        return root.resolve("secure");
    }

    public Path logs() {
        return root.resolve("logs");
    }

    public Path configFile() {
        return root.resolve("config.json");
    }
}
