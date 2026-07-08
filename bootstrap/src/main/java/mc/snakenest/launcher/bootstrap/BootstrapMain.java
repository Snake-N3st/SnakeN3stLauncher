package mc.snakenest.launcher.bootstrap;

import mc.snakenest.launcher.util.AppDirs;
import mc.snakenest.launcher.util.Log;
import mc.snakenest.launcher.util.Sha256;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Entry point of the bootstrap module: check the site for the latest
 * launcher release, download + verify it if it isn't already cached, run it
 * as a new process, then exit immediately. Never runs two JVMs at once -
 * see the plan's "Deux artefacts, un seul JVM actif à la fois".
 *
 * <p>Reads the same {@code sn3.baseUrl}/{@code sn3.clientId}/{@code
 * sn3.dataDir} JVM system properties as the launcher itself, and passes
 * them straight through to the process it spawns ({@link #spawnLauncher}) -
 * a spawned child process does <b>not</b> automatically inherit its
 * parent's {@code -D} JVM properties (unlike OS environment variables), so
 * this forwarding is required, not just a convenience: without it, a
 * {@code -Dsn3.dataDir=...} passed to this process alone would silently
 * fail to reach the launcher it spawns, which would fall back to the
 * default OS data directory instead.
 *
 * <p>{@link #loadPropertiesFileNextToJar()} runs first and loads every key
 * from a {@code bootstrap.properties} file next to this jar (if any) as a
 * JVM system property - for a double-clickable jar or a desktop
 * shortcut/file association, there's no launch command line to add
 * {@code -D} arguments to at all.
 */
public final class BootstrapMain {

    private static final String DEFAULT_BASE_URL = "https://snake-n3st.fr";
    private static final String PROPERTIES_FILE_NAME = "bootstrap.properties";

    private BootstrapMain() {
    }

    public static void main(String[] args) {
        loadPropertiesFileNextToJar();

        AppDirs dirs = new AppDirs();
        Log.initialize(dirs);

        String clientId = ClientIds.resolve(System.getProperty("sn3.clientId"));
        if (clientId == null) {
            System.err.println("Missing sn3.clientId: neither -Dsn3.clientId nor a bundled .clientId resource is set.");
            System.exit(1);
            return;
        }
        String baseUrlProperty = System.getProperty("sn3.baseUrl", DEFAULT_BASE_URL);
        URI baseUrl = URI.create(baseUrlProperty);

        // Nice-to-have only: showIfPossible() already degrades to null in a headless
        // environment, and every use below is null-checked - a display-less test/CI run must
        // behave exactly as it did before this was added.
        BootstrapSplash splash = BootstrapSplash.showIfPossible();

        try {
            Path jar = ensureLatestLauncherJar(dirs, baseUrl, clientId, splash);
            setStatus(splash, "Démarrage du launcher...");
            Process process = spawnLauncher(jar, baseUrlProperty, clientId);
            Log.info(BootstrapMain.class, "Launched " + jar.getFileName() + " (pid " + process.pid() + "), bootstrap exiting.");
            close(splash);
        } catch (Exception e) {
            Log.error(BootstrapMain.class, "Bootstrap failed", e);
            System.err.println("Could not start the launcher: " + e.getMessage());
            close(splash);
            BootstrapSplash.showFatalError("Impossible de démarrer le launcher : " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Best-effort: if a {@value #PROPERTIES_FILE_NAME} file sits next to this jar, every key in
     * it becomes a JVM system property, unless already set via {@code -D} (an explicit {@code -D}
     * always wins over the file, same "most specific override wins" precedence as
     * {@code common.util.ClientIds}). Runs before {@link AppDirs}/{@link Log} even exist, since
     * {@code sn3.dataDir} might itself come from this file - a failure here can only be reported
     * to stderr, not the file logger, and is never fatal (falls back to whatever {@code -D}
     * arguments/defaults already apply, exactly as if the file didn't exist).
     */
    private static void loadPropertiesFileNextToJar() {
        Path jarDirectory = jarDirectory();
        if (jarDirectory == null) {
            return;
        }
        Path propertiesFile = jarDirectory.resolve(PROPERTIES_FILE_NAME);
        if (!Files.isRegularFile(propertiesFile)) {
            return;
        }
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(propertiesFile)) {
            properties.load(in);
        } catch (IOException e) {
            System.err.println("Could not read " + propertiesFile + ": " + e.getMessage());
            return;
        }
        for (String key : properties.stringPropertyNames()) {
            if (System.getProperty(key) == null) {
                System.setProperty(key, properties.getProperty(key));
            }
        }
    }

    /**
     * The directory this jar itself lives in, or {@code null} if that can't be determined (e.g.
     * an unusual classloader setup) - best-effort only, {@link #loadPropertiesFileNextToJar()}
     * simply finds nothing in that case, same as if the properties file didn't exist. Running
     * unpackaged (an IDE, {@code target/classes}) resolves to that classes directory itself,
     * which is a harmless, occasionally convenient place to drop a test properties file.
     */
    private static Path jarDirectory() {
        try {
            URI location = BootstrapMain.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path path = Path.of(location);
            return Files.isDirectory(path) ? path : path.getParent();
        } catch (Exception e) {
            return null;
        }
    }

    private static Path ensureLatestLauncherJar(AppDirs dirs, URI baseUrl, String clientId, BootstrapSplash splash) throws Exception {
        setStatus(splash, "Vérification de la dernière version...");
        LauncherReleaseClient releaseClient = new LauncherReleaseClient(baseUrl, clientId);
        LauncherReleaseInfo latest = releaseClient.fetchLatest();

        Path target = dirs.cacheLauncher().resolve(latest.version() + ".jar");
        if (isValidCachedJar(target, latest)) {
            Log.info(BootstrapMain.class, "Launcher " + latest.version() + " already cached, skipping download.");
            return target;
        }

        setStatus(splash, "Téléchargement de la version " + latest.version() + "...");
        Log.info(BootstrapMain.class, "Downloading launcher " + latest.version() + "...");
        releaseClient.download(latest, target);
        return target;
    }

    private static void setStatus(BootstrapSplash splash, String status) {
        if (splash != null) {
            splash.setStatus(status);
        }
    }

    private static void close(BootstrapSplash splash) {
        if (splash != null) {
            splash.close();
        }
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
        // Not read by this class directly (only AppDirs cares), but a spawned process doesn't
        // inherit its parent's JVM properties on its own - forwarded here so a
        // -Dsn3.dataDir=... passed to bootstrap actually reaches the launcher it spawns too,
        // keeping them pointed at the same (possibly test/dev-only) data directory.
        String dataDir = System.getProperty("sn3.dataDir");
        if (dataDir != null && !dataDir.isBlank()) {
            command.add("-Dsn3.dataDir=" + dataDir);
        }
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
