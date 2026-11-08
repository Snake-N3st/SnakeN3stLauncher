package mc.snakenest.launcher.game;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Picks which {@code java} binary a modpack is launched with, and checks that a binary the player
 * chose is actually usable before they find out the hard way.
 *
 * <p><b>What "default" means.</b> With nothing configured anywhere, the game runs on the launcher's
 * own JVM: OpenLauncherLib derives its command from {@code java.home} (see {@code
 * JavaUtil.getJavaCommand()}), and the bootstrap re-execs the launcher, so the JVM that runs the
 * launcher UI is also the one that ends up running Minecraft. That is the behaviour that shipped
 * before this class existed, and it stays the behaviour when both settings are empty - which is why
 * "empty" is a meaningful, first-class value here rather than something to be filled in with a
 * guess.
 *
 * <p>Resolution is deliberately two-tier: a modpack's own choice wins, otherwise the launcher-wide
 * default applies, otherwise the launcher's JVM. That way a player who needs an older Java for one
 * specific modpack sets it there without disturbing everything else.
 */
public final class JavaRuntimes {

    /** How long to let a candidate binary print its version before giving up on it. */
    private static final long PROBE_TIMEOUT_SECONDS = 10;

    private JavaRuntimes() {
    }

    /**
     * The binary to launch with, or {@code null} to let OpenLauncherLib fall back to the launcher's
     * own JVM.
     *
     * @param modpackJavaPath this modpack's own setting, blank/null when it follows the default
     * @param defaultJavaPath the launcher-wide default, blank/null when it is the launcher's JVM
     */
    public static String resolve(String modpackJavaPath, String defaultJavaPath) {
        String perModpack = normalize(modpackJavaPath);

        return perModpack != null ? perModpack : normalize(defaultJavaPath);
    }

    /** Trims, and collapses "unset" to a single representation so callers only test for null. */
    public static String normalize(String path) {
        if (path == null) {
            return null;
        }

        String trimmed = path.trim();

        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Runs {@code <path> -version} and returns what it printed, e.g.
     * {@code openjdk version "21.0.3" ...}.
     *
     * <p>Actually executing the candidate is the only check worth making: a path can exist, be
     * executable, and still be the wrong architecture, a broken symlink into a removed JDK, or not
     * a JVM at all. Anything short of running it would let the player save a setting that only
     * fails later, when the game refuses to start and the cause is far less obvious.
     *
     * @throws IOException if the binary is missing, not executable, times out, or exits non-zero
     */
    public static String describe(String path) throws IOException {
        String candidate = normalize(path);

        if (candidate == null) {
            throw new IOException("Aucun chemin indiqué.");
        }

        Path binary = Path.of(candidate);

        if (!Files.isRegularFile(binary)) {
            throw new IOException("Ce chemin ne désigne pas un fichier.");
        }

        if (!Files.isExecutable(binary)) {
            throw new IOException("Ce fichier n'est pas exécutable.");
        }

        Process process = new ProcessBuilder(candidate, "-version")
                // "java -version" writes to standard error, not standard output.
                .redirectErrorStream(true)
                .start();

        try {
            // Wait first, read afterwards - never the other way round: readAllBytes() blocks until
            // the stream closes, which only happens when the process exits, so reading first would
            // make the timeout below unreachable and let a hung binary freeze the caller forever.
            // Safe to leave the pipe undrained meanwhile because "java -version" prints a few
            // hundred bytes, far below the OS pipe buffer that a process must fill before it
            // blocks on its own output.
            if (!process.waitFor(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("Le programme n'a pas répondu.");
            }

            String output = new String(process.getInputStream().readAllBytes()).trim();

            if (process.exitValue() != 0) {
                throw new IOException("Le programme s'est arrêté avec le code " + process.exitValue() + ".");
            }

            if (output.isEmpty()) {
                throw new IOException("Le programme n'a affiché aucune version : ce n'est probablement pas un binaire Java.");
            }

            // Only the first line is the version proper; the rest is the runtime/VM banner.
            return output.lines().findFirst().orElse(output);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IOException("Vérification interrompue.");
        }
    }

    /**
     * The launcher's own JVM binary - shown in the UI so "par défaut" names something concrete
     * rather than leaving the player guessing which Java that actually is.
     */
    public static String launcherJavaBinary() {
        String home = System.getProperty("java.home");

        if (home == null || home.isBlank()) {
            return "java";
        }

        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");

        return Path.of(home, "bin", windows ? "java.exe" : "java").toString();
    }
}
