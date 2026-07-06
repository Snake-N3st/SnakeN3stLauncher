package mc.snakenest.launcher.bootstrap;

import mc.snakenest.launcher.util.AppDirs;
import mc.snakenest.launcher.util.Log;
import mc.snakenest.launcher.util.Sha256;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Entry point of the bootstrap module: check the site for the latest
 * launcher release, download + verify it if it isn't already cached, run it
 * as a new process, then exit immediately. Never runs two JVMs at once -
 * see the plan's "Deux artefacts, un seul JVM actif à la fois".
 *
 * <p>Reads the same {@code sn3.baseUrl}/{@code sn3.clientId} JVM system
 * properties as the launcher itself, and passes them straight through to
 * the process it spawns.
 */
public final class BootstrapMain {

    private static final String DEFAULT_BASE_URL = "https://snake-n3st.fr";

    private BootstrapMain() {
    }

    public static void main(String[] args) {
        AppDirs dirs = new AppDirs();
        Log.initialize(dirs);

        String clientId = System.getProperty("sn3.clientId");
        if (clientId == null || clientId.isBlank()) {
            System.err.println("Missing required -Dsn3.clientId system property.");
            System.exit(1);
            return;
        }
        String baseUrlProperty = System.getProperty("sn3.baseUrl", DEFAULT_BASE_URL);
        URI baseUrl = URI.create(baseUrlProperty);

        try {
            Path jar = ensureLatestLauncherJar(dirs, baseUrl, clientId);
            Process process = spawnLauncher(jar, baseUrlProperty, clientId);
            Log.info(BootstrapMain.class, "Launched " + jar.getFileName() + " (pid " + process.pid() + "), bootstrap exiting.");
        } catch (Exception e) {
            Log.error(BootstrapMain.class, "Bootstrap failed", e);
            System.err.println("Could not start the launcher: " + e.getMessage());
            System.exit(1);
        }
    }

    private static Path ensureLatestLauncherJar(AppDirs dirs, URI baseUrl, String clientId) throws Exception {
        LauncherReleaseClient releaseClient = new LauncherReleaseClient(baseUrl, clientId);
        LauncherReleaseInfo latest = releaseClient.fetchLatest();

        Path target = dirs.cacheLauncher().resolve(latest.version() + ".jar");
        if (isValidCachedJar(target, latest)) {
            Log.info(BootstrapMain.class, "Launcher " + latest.version() + " already cached, skipping download.");
            return target;
        }

        Log.info(BootstrapMain.class, "Downloading launcher " + latest.version() + "...");
        releaseClient.download(latest, target);
        return target;
    }

    private static boolean isValidCachedJar(Path target, LauncherReleaseInfo latest) {
        if (!Files.isRegularFile(target)) {
            return false;
        }
        try (var in = Files.newInputStream(target)) {
            return Sha256.hex(in).equalsIgnoreCase(latest.sha256());
        } catch (IOException e) {
            return false;
        }
    }

    private static Process spawnLauncher(Path jar, String baseUrl, String clientId) throws IOException {
        List<String> command = new ArrayList<>();
        command.add(javaBinary());
        command.add("-Dsn3.baseUrl=" + baseUrl);
        command.add("-Dsn3.clientId=" + clientId);
        command.add("-jar");
        command.add(jar.toString());

        return new ProcessBuilder(command)
                .inheritIO()
                .start();
    }

    private static String javaBinary() {
        String javaHome = System.getProperty("java.home");
        String os = System.getProperty("os.name", "").toLowerCase();
        String executable = os.contains("win") ? "java.exe" : "java";
        return Path.of(javaHome, "bin", executable).toString();
    }
}
