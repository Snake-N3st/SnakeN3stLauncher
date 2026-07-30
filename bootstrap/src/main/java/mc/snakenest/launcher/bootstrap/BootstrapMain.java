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
 * <p>Two property sources are applied before anything else runs, in this exact order (each only
 * fills in a system property that isn't already set, so an earlier/more specific source always
 * wins - see each one's own Javadoc for why the order matters):
 * <ol>
 *   <li>{@link #loadPropertiesFileNextToJar()} - a {@code bootstrap.properties} file next to this
 *   jar, for a double-clickable jar or a desktop shortcut/file association with no launch command
 *   line to add {@code -D} arguments to at all.
 *   <li>{@link BundledJvmProperties#loadIfPresent()} - a {@code jvm.prop} file baked in at
 *   packaging time, typically {@code sn3.clientId}/{@code sn3.baseUrl} for a turnkey, single-site
 *   build a player never has to configure anything for.
 * </ol>
 */
public final class BootstrapMain {

    private static final String DEFAULT_BASE_URL = "https://snake-n3st.fr";
    private static final String PROPERTIES_FILE_NAME = "bootstrap.properties";

    private BootstrapMain() {
    }

    public static void main(String[] args) {
        loadPropertiesFileNextToJar();
        BundledJvmProperties.loadIfPresent();

        AppDirs dirs;
        try {
            dirs = new AppDirs();
            Log.initialize(dirs);
        } catch (Exception e) {
            // Nothing can be logged to the file logger yet - Log.initialize() is what's
            // failing. A GUI dialog here (rather than just stderr) matters for exactly the
            // same reason as the clientId check below: a double-clicked jar has no visible
            // console, so silently dying here would look like nothing happened at all.
            String message = "Impossible de démarrer le launcher : " + e.getMessage();
            System.err.println(message);
            BootstrapSplash.showFatalError(message);
            System.exit(1);
            return;
        }

        String clientId = ClientIds.resolve(System.getProperty("sn3.clientId"));
        if (clientId == null) {
            String message = "Configuration invalide : identifiant client manquant, ni via -Dsn3.clientId, un fichier bootstrap.properties, ni une ressource jvm.prop embarquée.";
            System.err.println(message);
            Log.warn(BootstrapMain.class, message);
            BootstrapSplash.showFatalError(message);
            System.exit(1);
            return;
        }
        String baseUrlProperty = System.getProperty("sn3.baseUrl", DEFAULT_BASE_URL);
        URI baseUrl = URI.create(baseUrlProperty);

        // A human is in the loop here (attemptStartup only returns true if the player actually
        // clicked "Réessayer" on the error dialog), so this can't spin forever the way an
        // automatic retry-on-a-timer could - the player can always choose "Fermer" instead.
        boolean retry;
        do {
            retry = attemptStartup(dirs, baseUrl, baseUrlProperty, clientId);
        } while (retry);
    }

    /**
     * One attempt at checking for updates and handing off to the launcher. Any failure shows a
     * blocking error dialog with a "Réessayer" option (see {@link
     * BootstrapSplash#showFatalErrorWithRetry}) instead of just exiting silently - a player
     * whose only feedback was a console window flashing shut (or nothing at all, for a
     * double-clicked jar) would otherwise have no idea whether the launcher failed to start or
     * is still quietly "doing something".
     *
     * @return {@code true} if the player asked to retry
     */
    private static boolean attemptStartup(AppDirs dirs, URI baseUrl, String baseUrlProperty, String clientId) {
        // Nice-to-have only: showIfPossible() already degrades to null in a headless
        // environment, and every use below is null-checked - a display-less test/CI run must
        // behave exactly as it did before this was added.
        BootstrapSplash splash = BootstrapSplash.showIfPossible();

        try {
            if (selfUpdateIfNeeded(baseUrl, clientId, splash)) {
                // The bootstrap jar was just replaced on disk with a newer version - this
                // running copy's own code is now stale, so it stops here rather than
                // continuing (or trying to re-exec itself, which is fragile cross-platform);
                // the player just needs to start it again to pick up the new one.
                String message = "Le launcher a été mis à jour.\nMerci de le relancer.";
                Log.info(BootstrapMain.class, "Bootstrap self-update applied, asking the player to restart.");
                close(splash);
                BootstrapSplash.showRestartPrompt(message);
                return false;
            }

            Path jar = ensureLatestLauncherJar(dirs, baseUrl, clientId, splash);
            setStatus(splash, "Démarrage du launcher...");
            Process process = spawnLauncher(jar, baseUrlProperty, clientId);
            Log.info(BootstrapMain.class, "Launched " + jar.getFileName() + " (pid " + process.pid() + "), bootstrap exiting.");
            close(splash);
            return false;
        } catch (Exception e) {
            Log.error(BootstrapMain.class, "Bootstrap failed", e);
            System.err.println("Could not start the launcher: " + e.getMessage());
            close(splash);
            boolean retry = BootstrapSplash.showFatalErrorWithRetry("Impossible de démarrer le launcher : " + e.getMessage());
            if (!retry) {
                System.exit(1);
            }
            return retry;
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
        Path location = codeSourceLocation();
        if (location == null) {
            return null;
        }
        return Files.isDirectory(location) ? location : location.getParent();
    }

    /**
     * The absolute path of this running jar itself, or {@code null} if that can't be determined -
     * running unpackaged (an IDE, {@code target/classes}, which resolves to a directory rather
     * than a single jar file) or an unusual classloader setup. {@link #selfUpdateIfNeeded} treats
     * that as "nothing to self-update", the same best-effort reasoning {@link #jarDirectory()}
     * already applies to the properties file lookup.
     */
    private static Path jarFile() {
        Path location = codeSourceLocation();
        return location != null && Files.isRegularFile(location) ? location : null;
    }

    private static Path codeSourceLocation() {
        try {
            URI location = BootstrapMain.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            return Path.of(location);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Checks the site for a newer bootstrap release than the jar currently running, and if one
     * exists, downloads it and atomically replaces this jar's own file on disk ({@code
     * AtomicFiles#writeVerified}, used internally by {@link LauncherReleaseClient#download}).
     * Compares by SHA-256 of the running jar itself rather than tracking a version string
     * anywhere - unlike a launcher release (which the site tracks independently of this build's
     * own Maven version), this jar has no reliable notion of "its own version number" to compare
     * against, so hashing sidesteps needing one, the same "no manual bookkeeping to drift out of
     * sync" reasoning {@link #isValidCachedJar} already applies to the launcher jar cache.
     *
     * @return true if a newer version was applied (the caller must stop and ask the player to
     * restart - see {@link #main}); {@code false} on any failure too (no bootstrap release
     * published yet, a network error, running unpackaged, or the replace itself failing) - this
     * is a nice-to-have that must never block getting the launcher running
     */
    private static boolean selfUpdateIfNeeded(URI baseUrl, String clientId, BootstrapSplash splash) {
        Path selfJar = jarFile();
        if (selfJar == null) {
            return false;
        }
        try {
            setStatus(splash, "Vérification des mises à jour du bootstrap...");
            LauncherReleaseClient releaseClient = new LauncherReleaseClient(baseUrl, clientId);
            LauncherReleaseInfo latest = releaseClient.fetchLatest(ReleaseType.BOOTSTRAP);

            String currentSha256;
            try (var in = Files.newInputStream(selfJar)) {
                currentSha256 = Sha256.hex(in);
            }
            if (currentSha256.equalsIgnoreCase(latest.sha256())) {
                return false;
            }

            setStatus(splash, "Téléchargement de la mise à jour du bootstrap...");
            Log.info(BootstrapMain.class, "Self-updating bootstrap to " + latest.version() + "...");
            releaseClient.download(latest, selfJar);
            return true;
        } catch (Exception e) {
            Log.warn(BootstrapMain.class, "Bootstrap self-update check failed, continuing with the current version: " + e.getMessage());
            return false;
        }
    }

    private static Path ensureLatestLauncherJar(AppDirs dirs, URI baseUrl, String clientId, BootstrapSplash splash) throws Exception {
        setStatus(splash, "Vérification de la dernière version...");
        LauncherReleaseClient releaseClient = new LauncherReleaseClient(baseUrl, clientId);
        LauncherReleaseInfo latest = releaseClient.fetchLatest(ReleaseType.LAUNCHER);

        Path target = dirs.cacheLauncher().resolve(latest.version() + ".jar");
        if (isValidCachedJar(target, latest)) {
            Log.info(BootstrapMain.class, "Launcher " + latest.version() + " already cached, skipping download.");
        } else {
            setStatus(splash, "Téléchargement de la version " + latest.version() + "...");
            Log.info(BootstrapMain.class, "Downloading launcher " + latest.version() + "...");
            releaseClient.download(latest, target);
        }

        pruneOldLauncherJars(dirs, target);
        return target;
    }

    /**
     * Every previous version's jar was left behind forever (each version gets its own filename,
     * see {@code target} above - nothing ever overwrote an older one) - only {@code keep} (the
     * version this run just confirmed/downloaded) has any reason to still be on disk, since a
     * bootstrap always targets whatever the site currently reports as latest. Best-effort: a
     * failure to delete an old jar is disk clutter, not worth failing the actual launch over.
     */
    private static void pruneOldLauncherJars(AppDirs dirs, Path keep) {
        Path cacheDir = dirs.cacheLauncher();
        if (!Files.isDirectory(cacheDir)) {
            return;
        }
        try (var entries = Files.list(cacheDir)) {
            for (Path entry : entries.toList()) {
                if (entry.equals(keep) || !entry.getFileName().toString().endsWith(".jar")) {
                    continue;
                }
                try {
                    Files.delete(entry);
                    Log.info(BootstrapMain.class, "Removed cached launcher jar " + entry.getFileName());
                } catch (IOException e) {
                    Log.warn(BootstrapMain.class, "Could not remove old cached launcher jar " + entry.getFileName() + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            Log.warn(BootstrapMain.class, "Could not list " + cacheDir + " to prune old launcher jars: " + e.getMessage());
        }
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
