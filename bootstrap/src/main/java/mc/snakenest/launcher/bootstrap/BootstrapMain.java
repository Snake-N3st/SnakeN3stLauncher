package mc.snakenest.launcher.bootstrap;

import mc.snakenest.launcher.auth.PlayerSession;
import mc.snakenest.launcher.auth.SignedParams;
import mc.snakenest.launcher.crypto.SignatureVerificationException;
import mc.snakenest.launcher.auth.SignedRequestSigner;
import mc.snakenest.launcher.crypto.EncryptedFileKeyStorage;
import mc.snakenest.launcher.crypto.KeyStorageException;
import mc.snakenest.launcher.util.AppDirs;
import mc.snakenest.launcher.util.Log;
import mc.snakenest.launcher.util.Sha256;
import mc.snakenest.launcher.util.StartupSignal;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
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
 *   line to add {@code -D} arguments to at all. The Debian package also uses it to say how it
 *   installed this jar, which is what {@link AptSelfUpdater} keys off.
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
            // After the logger exists - it reports what it does. See InstalledJarHealer.
            InstalledJarHealer.startIfRequested(dirs, jarFile());
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

        // Before any window is built: the splash has to be painted in whichever theme the player
        // last chose in the launcher, or they get a dark splash followed by a light launcher every
        // single start. Falls back to dark on a first run, where no config exists yet.
        BootstrapTheme.use(LauncherConfigFragment.read(dirs).theme);

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
            SelfUpdate update = Boolean.getBoolean("sn3.offline") ? SelfUpdate.none()
                    : selfUpdateIfNeeded(dirs, baseUrl, clientId, splash);

            if (update.outcome() == SelfUpdateOutcome.RESTART) {
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

            if (update.outcome() == SelfUpdateOutcome.DELEGATE) {
                // No "please relaunch" here, unlike the branch above: nothing was replaced under
                // this running process, so the newer jar can simply be started right now and the
                // player never sees that an update happened at all.
                Log.info(BootstrapMain.class, "Handing over to the updated bootstrap at " + update.jar());
                spawnBootstrap(update.jar(), baseUrlProperty, clientId, update.healTarget());
                close(splash);
                return false;
            }

            Path jar = ensureLatestLauncherJar(dirs, baseUrl, clientId, splash);
            setStatus(splash, "Démarrage du launcher...");

            // Both files live in the cache dir and belong to this bootstrap run: the launcher
            // touches the first when its window appears, and writes its standard error into the
            // second so a startup crash can actually be shown to the player (see
            // LauncherStartupWatcher and BootstrapCrashDialog).
            Path readyFile = handoffFile(dirs, "ready");
            Path stderrFile = handoffFile(dirs, "stderr");

            Process process = spawnLauncher(jar, baseUrlProperty, clientId, readyFile, stderrFile);
            Log.info(BootstrapMain.class, "Launched " + jar.getFileName() + " (pid " + process.pid() + "), waiting for its window.");
            // "Successfully spawned", not "successfully ran" - see LastLaunchedJar's own docblock
            // for why that's the most this can actually observe and why it's still good enough
            // for the "launch the last jar" fallback option.
            LastLaunchedJar.record(dirs, versionOf(jar));

            // The splash deliberately stays up until there is something else on screen to replace
            // it - closing it the instant the process was spawned left several seconds of nothing
            // at all, which reads as a failed launch.
            LauncherStartupWatcher.Result result = LauncherStartupWatcher.await(process, readyFile);
            close(splash);

            if (result.crashed()) {
                Log.error(BootstrapMain.class,
                        "The launcher exited with code " + result.exitCode() + " before showing a window", null);
                BootstrapCrashDialog.show(result.exitCode(), readQuietly(stderrFile), baseUrl, clientId);
            }

            cleanUpQuietly(readyFile, stderrFile);
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
        applyPropertiesFrom(jarDirectory);
    }

    /**
     * The half of {@link #loadPropertiesFileNextToJar()} that does not depend on where this jar
     * happens to live - split out so it can be exercised against a real directory, which is the
     * only way to check the one property the Debian package relies on ({@code sn3.aptInstall}, see
     * {@link AptSelfUpdater}) actually survives the trip from a file on disk to a system property.
     */
    static void applyPropertiesFrom(Path directory) {
        Path propertiesFile = directory.resolve(PROPERTIES_FILE_NAME);
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
    /**
     * A release client that verifies signatures against the keys this jar pins, and remembers in the player's
     * data directory that one has been seen. A build that pins none verifies nothing - see
     * {@link TrustedSigningKeys}.
     */
    private static LauncherReleaseClient newReleaseClient(AppDirs dirs, URI baseUrl, String clientId) {
        return new LauncherReleaseClient(baseUrl, clientId, ArtifactVerifier.production(),
                TrustState.load(dirs.root().resolve(TrustState.FILE_NAME)));
    }

    /** What {@link #selfUpdateIfNeeded} decided to do. */
    private enum SelfUpdateOutcome {
        /** Already running the newest bootstrap, or the check failed - carry on with this jar. */
        NONE,

        /** This jar was replaced on disk, so the running code is now stale - ask for a relaunch. */
        RESTART,

        /** A newer bootstrap now lives in the player's own data directory - run that one instead. */
        DELEGATE
    }

    /**
     * {@code jar} is set only for {@link SelfUpdateOutcome#DELEGATE}; {@code healTarget}, when set, is the
     * installed jar the newer copy should bring up to date once this process has let go of it - see
     * {@link InstalledJarHealer}.
     */
    private record SelfUpdate(SelfUpdateOutcome outcome, Path jar, Path healTarget) {

        static SelfUpdate none() {
            return new SelfUpdate(SelfUpdateOutcome.NONE, null, null);
        }

        static SelfUpdate restart() {
            return new SelfUpdate(SelfUpdateOutcome.RESTART, null, null);
        }

        static SelfUpdate delegate(Path jar) {
            return new SelfUpdate(SelfUpdateOutcome.DELEGATE, jar, null);
        }

        static SelfUpdate delegate(Path jar, Path healTarget) {
            return new SelfUpdate(SelfUpdateOutcome.DELEGATE, jar, healTarget);
        }
    }

    /**
     * Brings the bootstrap up to date, without ever writing to a jar the OS package manager owns.
     *
     * <p><b>Why the split.</b> The .deb installs this jar into {@code /usr/share}, root-owned and
     * tracked by dpkg. Overwriting it in place is wrong even when it succeeds: running the launcher
     * once as root replaced the packaged jar with a file created under root's umask, mode 0600,
     * which every normal user on the machine then failed to read - "Unable to access jarfile". But
     * simply skipping the update there is not acceptable either; the bootstrap has to keep itself
     * current whoever runs it. So for a system-wide install the new version is written into the
     * player's own data directory and run from there, leaving the packaged file untouched. A
     * per-user install (portable zip, a jar in the player's home) still updates in place, which
     * costs no extra process start.
     *
     * <p><b>And why that user copy is the fallback rather than the answer.</b> It works, but it
     * leaves the machine split: one copy per account, the packaged jar drifting further behind
     * every release, and {@code apt} still reporting the old version. When the jar came from the
     * site's {@code .deb}, {@link AptSelfUpdater} installs the new package instead - one update for
     * the whole machine, with dpkg still owning the file. It is allowed to decline (no pkexec, a
     * player who says no), and then the user copy takes over exactly as before.
     */
    private static SelfUpdate selfUpdateIfNeeded(AppDirs dirs, URI baseUrl, String clientId, BootstrapSplash splash) {
        Path selfJar = jarFile();
        if (selfJar == null) {
            return SelfUpdate.none();
        }
        try {
            setStatus(splash, "Vérification des mises à jour du bootstrap...");
            LauncherReleaseClient releaseClient = newReleaseClient(dirs, baseUrl, clientId);
            // No channel/signature for a bootstrap release - always single-stream, unauthenticated (see ReleaseType/LauncherReleaseClient).
            LauncherReleaseClient.Checked checked = releaseClient.fetchLatestChecked(ReleaseType.BOOTSTRAP, null, null);
            LauncherReleaseInfo latest = checked.info();

            if (hasSha256(selfJar, latest.sha256())) {
                // Up to date: any data-directory copy left over from an earlier hand-over is dead weight.
                pruneUserCopies(dirs.cache(), selfJar, selfJar);
                return SelfUpdate.none();
            }

            if (isPackageManaged(selfJar)) {
                // Built before the install, not after, and that ordering is load-bearing. The JVM
                // loads a class the first time it is used, straight out of the jar file this very
                // process is running from - and the install below replaces exactly that file. dpkg
                // renames the new file over the old one, so the inode this process still holds open
                // survives and the gamble normally pays off; a package manager that truncated in
                // place instead would leave the JVM reading a half-written jar, and this method
                // would die with NoClassDefFoundError after the update had already succeeded. So
                // the last class needed on the way out is loaded while the jar is still whole,
                // which costs one object and removes the gamble.
                SelfUpdate installedByApt = SelfUpdate.delegate(selfJar);

                // The proper repair when this jar came from the site's own .deb: let the package
                // manager replace it, so dpkg's idea of what is installed stays true and every
                // account on the machine gets the new version at once. The hash re-check is what
                // makes trusting it safe - apt reporting success while leaving the old jar in place
                // would otherwise mean reinstalling on every single start, forever.
                // A package whose own signature is wrong or gone is not installed: it runs as root, and the
                // jar it carries is verified separately, so the user-copy route below still delivers the update.
                boolean debTrusted = checked.debStatus() != SignatureStatus.REFUSED;

                if (!debTrusted) {
                    Log.error(BootstrapMain.class, "Not updating through the package manager: the Debian package failed verification.", null);
                }

                if (debTrusted && AptSelfUpdater.isAptInstall()
                        && AptSelfUpdater.install(releaseClient, latest, splash)
                        && hasSha256(selfJar, latest.sha256())) {
                    Log.info(BootstrapMain.class, "Bootstrap updated to " + latest.version() + " through the package manager.");
                    return installedByApt;
                }

                return delegateToUserCopy(dirs, releaseClient, latest, splash, selfJar, false);
            }

            // A running jar cannot be replaced on Windows: the JVM holds it open without FILE_SHARE_DELETE, so
            // the final move onto it is refused. Before this branch existed that refusal was caught, logged
            // ("continuing with the current version") and forgotten, which is why a bootstrap on Windows stayed
            // on whatever version it was installed as, forever, without the player seeing a thing. So on Windows
            // it is not even attempted: the new version goes to the player's data directory and is run from there.
            if (!canReplaceRunningJar(System.getProperty("os.name", "")) || forcesUserCopy()) {
                return delegateToUserCopy(dirs, releaseClient, latest, splash, selfJar, true);
            }

            setStatus(splash, "Téléchargement de la mise à jour du bootstrap...");
            Log.info(BootstrapMain.class, "Self-updating bootstrap to " + latest.version() + "...");

            try {
                releaseClient.download(latest, selfJar, null);
            } catch (IOException e) {
                // The jar sits somewhere this account cannot write (a read-only folder, an antivirus holding the
                // file), or the OS refused to replace it for a reason this code does not list. Giving up would
                // leave the bootstrap stuck on this version for good; the player's own data directory is always
                // writable, so the update goes there instead.
                Log.warn(BootstrapMain.class, "Could not replace " + selfJar + " in place (" + e.getMessage()
                        + "); updating a copy in the data directory instead.");
                // Not asked to heal the jar afterwards: if this account could not write it now, it cannot then.
                return delegateToUserCopy(dirs, releaseClient, latest, splash, selfJar, false);
            }

            // The download inherits this process's umask. Running as root with a restrictive umask
            // would otherwise leave a jar no other account can read - the exact failure this whole
            // method is shaped around.
            makeWorldReadable(selfJar);
            return SelfUpdate.restart();
        } catch (SignatureVerificationException e) {
            // Asymmetric on purpose: the update is refused, the bootstrap keeps running as it is. Nothing
            // unverified has been written anywhere (the check precedes the download), and refusing to start at
            // all would trade a real availability risk for no gain - see the signature policy.
            Log.error(BootstrapMain.class, "Bootstrap update REFUSED, keeping the current version: " + e.getMessage(), null);
            return SelfUpdate.none();
        } catch (Exception e) {
            Log.warn(BootstrapMain.class, "Bootstrap self-update check failed, continuing with the current version: " + e.getMessage());
            return SelfUpdate.none();
        }
    }

    /**
     * Whether this OS lets a running jar be overwritten. Windows does not - an open jar cannot be replaced or
     * deleted - while Linux and macOS do (the running process keeps its open inode). Kept as a pure function of
     * the OS name so the rule can be tested on any machine.
     */
    static boolean canReplaceRunningJar(String osName) {
        return !osName.toLowerCase().contains("win");
    }

    /**
     * {@code -Dsn3.selfUpdate=copy} takes the data-directory route on any OS. Two uses: a player whose
     * in-place update misbehaves (an antivirus, a network drive) has a way out that needs no new release, and
     * the hand-over can be exercised end to end on a machine that is not Windows.
     */
    static boolean forcesUserCopy() {
        return "copy".equalsIgnoreCase(System.getProperty("sn3.selfUpdate", ""));
    }

    /**
     * Where a downloaded bootstrap lives in the player's data directory: {@code cache/bootstrap-<digest>.jar}.
     *
     * <p>Named after the content, never a fixed name, for two reasons. The copy that is *currently running* may
     * itself be one of these, and overwriting a running jar is exactly what fails on Windows - a new release
     * must land in a file nobody has open. And the digest is the only thing taken from the server that is safe
     * to put in a file name: a version string is free text, a SHA-256 is 64 hex characters or rejected here.
     */
    static Path userCopyPath(Path cacheDir, String sha256) {
        if (sha256 == null || !sha256.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("Not a SHA-256 digest: " + sha256);
        }

        return cacheDir.resolve("bootstrap-" + sha256.substring(0, 16).toLowerCase() + ".jar");
    }

    /**
     * Deletes every user copy of the bootstrap except {@code keep} and the jar that is running. Best effort:
     * on Windows the running one is locked and simply stays until a later run, and a failure to delete a stale
     * file is never a reason to fail an update.
     */
    static void pruneUserCopies(Path cacheDir, Path keep, Path running) {
        try (var entries = Files.list(cacheDir)) {
            entries.filter(p -> {
                String name = p.getFileName().toString();
                return name.equals("bootstrap.jar") || (name.startsWith("bootstrap-") && name.endsWith(".jar"));
            }).filter(p -> !p.equals(keep) && !p.equals(running)).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException | RuntimeException e) {
                    Log.info(BootstrapMain.class, "Could not remove the old bootstrap copy " + p + ": " + e.getMessage());
                }
            });
        } catch (IOException | RuntimeException e) {
            // No cache directory yet, or it cannot be listed: nothing to prune.
        }
    }

    /**
     * The installed jar a newer data-directory copy should bring up to date. When this process is itself the
     * installed jar, that is {@code selfJar}; when it is already a data-directory copy (a release came out
     * since the last hand-over), the installed jar is the one it was itself asked to heal, which has to be
     * carried forward or it would be forgotten at the second update.
     */
    private static Path healTargetFor(AppDirs dirs, Path selfJar) {
        boolean selfIsUserCopy = selfJar.toAbsolutePath().normalize().getParent() != null
                && selfJar.toAbsolutePath().normalize().getParent().equals(dirs.cache().toAbsolutePath().normalize());

        if (!selfIsUserCopy) {
            return selfJar;
        }

        String inherited = System.getProperty(InstalledJarHealer.PROPERTY);
        return inherited == null || inherited.isBlank() ? null : Path.of(inherited);
    }

    /**
     * Downloads the latest bootstrap into the player's data directory (unless an earlier run already did) and
     * hands over to it. Used for package-managed jars, on Windows, and whenever replacing the jar in place
     * failed: the same hand-over in all three cases, so there is one path to get right instead of three.
     */
    private static SelfUpdate delegateToUserCopy(AppDirs dirs, LauncherReleaseClient releaseClient,
                                                 LauncherReleaseInfo latest, BootstrapSplash splash, Path selfJar,
                                                 boolean mayHealInstalledJar)
            throws Exception {
        Path userCopy = userCopyPath(dirs.cache(), latest.sha256());

        // Already downloaded by a previous run - just hand over to it rather than re-fetching the same bytes
        // on every start.
        if (!hasSha256(userCopy, latest.sha256())) {
            setStatus(splash, "Téléchargement de la mise à jour du bootstrap...");
            Log.info(BootstrapMain.class, "Updating the bootstrap to " + latest.version()
                    + " in the player's data directory (" + selfJar + " stays as it is).");
            Files.createDirectories(userCopy.getParent());
            releaseClient.download(latest, userCopy, null);
        }

        pruneUserCopies(dirs.cache(), userCopy, selfJar);

        return SelfUpdate.delegate(userCopy, mayHealInstalledJar ? healTargetFor(dirs, selfJar) : null);
    }

    /** Whether {@code file} exists and already hashes to {@code expectedSha256}. */
    static boolean hasSha256(Path file, String expectedSha256) {
        if (!Files.isRegularFile(file)) {
            return false;
        }
        try (var in = Files.newInputStream(file)) {
            return Sha256.hex(in).equalsIgnoreCase(expectedSha256);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Whether this jar belongs to a system-wide install rather than to the player.
     *
     * <p>Two independent signs, because either alone misses a case: a normal user cannot write to
     * {@code /usr/share} (so writability catches them), but root can - and root is precisely the
     * account that corrupts the packaged file. The path check catches root; the writability check
     * catches anything installed system-wide somewhere this list does not name.
     */
    static boolean isPackageManaged(Path jar) {
        Path absolute = jar.toAbsolutePath();

        return isUnderSystemPrefix(absolute) || !Files.isWritable(absolute);
    }

    /**
     * The path half of {@link #isPackageManaged}, kept separate because it is the half that catches
     * root - and the only half that can be tested honestly. A writability check on a path like
     * {@code /usr/share/...} answers "not writable" simply because the file does not exist on the
     * test machine, so a test that went through {@link #isPackageManaged} would pass whether this
     * rule existed or not.
     */
    static boolean isUnderSystemPrefix(Path jar) {
        String path = jar.toAbsolutePath().toString();

        for (String prefix : new String[]{"/usr/", "/opt/", "/snap/"}) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }

        return false;
    }

    /** Best-effort {@code chmod a+r}; a no-op on filesystems without POSIX permissions. */
    private static void makeWorldReadable(Path file) {
        try {
            var permissions = new java.util.HashSet<>(Files.getPosixFilePermissions(file));
            permissions.add(java.nio.file.attribute.PosixFilePermission.OWNER_READ);
            permissions.add(java.nio.file.attribute.PosixFilePermission.GROUP_READ);
            permissions.add(java.nio.file.attribute.PosixFilePermission.OTHERS_READ);
            Files.setPosixFilePermissions(file, permissions);
        } catch (Exception e) {
            // Windows, or an exotic filesystem - nothing to do and nothing worth failing over.
            Log.info(BootstrapMain.class, "Could not adjust permissions on " + file + ": " + e);
        }
    }

    /**
     * Resolves, downloads if needed, and returns the launcher jar to run - following the
     * player's saved channel preference (see {@link LauncherConfigFragment}), with the
     * three-way fallback ({@link ChannelFallbackDialog}) if that channel turns out restricted and
     * this installation can't prove eligibility. {@code null} return means "the player chose
     * 'launch the last jar' and there's nothing else to resolve" - handled by the caller.
     */
    private static Path ensureLatestLauncherJar(AppDirs dirs, URI baseUrl, String clientId, BootstrapSplash splash) throws Exception {
        if (Boolean.getBoolean("sn3.offline")) {
            Path cached = LastLaunchedJar.find(dirs);
            if (cached == null) throw new BootstrapException("Aucun launcher local précédemment démarré n’est disponible.");
            return cached;
        }
        try {
            return ensureOnlineLauncherJar(dirs, baseUrl, clientId, splash);
        } catch (SignatureVerificationException refused) {
            // Never turn a failed authenticity check into a network fallback.
            throw refused;
        } catch (IOException | BootstrapException unavailable) {
            Path cached = LastLaunchedJar.find(dirs);
            if (cached == null) throw unavailable;
            close(splash);
            if (!BootstrapSplash.confirmOfflineFallback()) throw unavailable;
            System.setProperty("sn3.offline", "true");
            return cached;
        }
    }

    private static Path ensureOnlineLauncherJar(AppDirs dirs, URI baseUrl, String clientId, BootstrapSplash splash) throws Exception {
        setStatus(splash, "Vérification de la dernière version...");
        LauncherReleaseClient releaseClient = newReleaseClient(dirs, baseUrl, clientId);
        LauncherConfigFragment config = LauncherConfigFragment.read(dirs);

        Resolved resolved = resolveLatestLauncherRelease(dirs, baseUrl, clientId, config, releaseClient, splash);
        if (resolved == null) {
            Path lastJar = LastLaunchedJar.find(dirs);
            if (lastJar == null) {
                throw new BootstrapException("Aucun jar précédemment lancé avec succès n'est disponible.");
            }
            Log.info(BootstrapMain.class, "Launching the last successfully-run jar instead: " + lastJar.getFileName());
            System.setProperty("sn3.offline", "true");
            return lastJar;
        }

        LauncherReleaseInfo latest = resolved.info();

        // Hard refusals (a wrong signature, or one missing after one was seen) were thrown inside the client,
        // before this point and before any download, and are deliberately NOT caught on the way to the error
        // dialog: none of them may end in "launch the last jar", which is one click away from a bypass.
        // What is left is the soft case: no signature, and none has ever been verified. Warn, let them choose.
        if (resolved.status() == SignatureStatus.UNSIGNED && !UnverifiedReleaseDialog.confirmLaunch(latest.version())) {
            throw new SignatureVerificationException("Lancement annulé : la version " + latest.version() + " du launcher n'est pas signée.");
        }

        Path target = dirs.cacheLauncher().resolve(latest.version() + ".jar");
        if (isValidCachedJar(target, latest)) {
            Log.info(BootstrapMain.class, "Launcher " + latest.version() + " already cached, skipping download.");
        } else {
            setStatus(splash, "Téléchargement de la version " + latest.version() + "...");
            Log.info(BootstrapMain.class, "Downloading launcher " + latest.version() + "...");
            releaseClient.download(latest, target, resolved.signed());
        }

        pruneOldLauncherJars(dirs, target, LastLaunchedJar.find(dirs));
        return target;
    }

    private record Resolved(LauncherReleaseInfo info, SignedParams signed, SignatureStatus status) {

        static Resolved of(LauncherReleaseClient.Checked checked, SignedParams signed) {
            return new Resolved(checked.info(), signed, checked.status());
        }
    }

    /** @return null if the player chose "launch the last jar" from the fallback dialog */
    private static Resolved resolveLatestLauncherRelease(AppDirs dirs, URI baseUrl, String clientId, LauncherConfigFragment config,
                                                           LauncherReleaseClient releaseClient, BootstrapSplash splash) throws Exception {
        try {
            return Resolved.of(releaseClient.fetchLatestChecked(ReleaseType.LAUNCHER, config.channel, null), null);
        } catch (RestrictedChannelException firstFailure) {
            Log.warn(BootstrapMain.class, "Channel \"" + config.channel + "\" is restricted, trying an existing local key: " + firstFailure.getMessage());
        }

        SignedParams existingKeySigned = trySignWithExistingKey(dirs, clientId, config);
        if (existingKeySigned != null) {
            try {
                return Resolved.of(releaseClient.fetchLatestChecked(ReleaseType.LAUNCHER, config.channel, existingKeySigned), existingKeySigned);
            } catch (RestrictedChannelException stillRestricted) {
                Log.warn(BootstrapMain.class, "Existing local key isn't eligible for channel \"" + config.channel + "\" either: " + stillRestricted.getMessage());
            }
        }

        return promptChannelFallback(dirs, baseUrl, clientId, config, releaseClient, splash);
    }

    /** @return null if the resolved key has no known playerId to sign with, or nothing is stored at all (never paired, or a PIN-protected vault - bootstrap never prompts for a PIN, staying minimal). */
    private static SignedParams trySignWithExistingKey(AppDirs dirs, String clientId, LauncherConfigFragment config) {
        if (!config.hasKnownPlayer()) {
            return null;
        }
        try {
            var key = new EncryptedFileKeyStorage(dirs).load();
            if (key.isEmpty()) {
                return null;
            }
            PlayerSession session = new PlayerSession(key.get(), config.playerId, clientId);
            return SignedRequestSigner.sign(session, "GET", "api/launcher-auth/releases/launcher/latest");
        } catch (KeyStorageException e) {
            // A PIN-protected vault (or a corrupted one) reads the same as "nothing usable" here -
            // bootstrap never prompts for a PIN itself, see EncryptedFileKeyStorage's own README.
            Log.warn(BootstrapMain.class, "Could not load the existing local key: " + e.getMessage());
            return null;
        }
    }

    private static Resolved promptChannelFallback(AppDirs dirs, URI baseUrl, String clientId, LauncherConfigFragment config,
                                                    LauncherReleaseClient releaseClient, BootstrapSplash splash) throws Exception {
        close(splash);
        ChannelFallbackDialog.Choice choice = ChannelFallbackDialog.show(config.channel != null ? config.channel : "défaut");
        switch (choice) {
            case USE_DEFAULT_CHANNEL -> {
                return Resolved.of(releaseClient.fetchLatestChecked(ReleaseType.LAUNCHER, null, null), null);
            }
            case RECONNECT -> {
                SignedParams signed = reconnectAndSign(dirs, baseUrl, clientId);
                return Resolved.of(releaseClient.fetchLatestChecked(ReleaseType.LAUNCHER, config.channel, signed), signed);
            }
            case LAUNCH_LAST_JAR -> {
                return null;
            }
            default -> throw new RestrictedChannelException("Accès refusé au canal restreint \"" + config.channel + "\".");
        }
    }

    /**
     * Runs {@link DeviceAuthClient} to completion (blocking, with its own small "waiting for
     * browser confirmation" window - see {@link ChannelFallbackDialog#showReconnecting}), and
     * persists the resulting key via the same {@code common.crypto.EncryptedFileKeyStorage} the
     * launcher itself uses, so this pairing survives past this one request (both for this
     * module's own future runs, and for the launcher once it actually starts).
     */
    private static SignedParams reconnectAndSign(AppDirs dirs, URI baseUrl, String clientId) throws Exception {
        var window = ChannelFallbackDialog.showReconnecting();
        try {
            var session = new DeviceAuthClient(baseUrl, clientId).run(window::isCancelled);
            if (session.isEmpty()) {
                throw new BootstrapException("Reconnexion annulée.");
            }
            try {
                new EncryptedFileKeyStorage(dirs).save(session.get().key());
            } catch (KeyStorageException e) {
                Log.warn(BootstrapMain.class, "Reconnected but could not persist the new key locally: " + e.getMessage());
            }
            return SignedRequestSigner.sign(session.get(), "GET", "api/launcher-auth/releases/launcher/latest");
        } finally {
            window.close();
        }
    }

    /**
     * Every previous version's jar was left behind forever (each version gets its own filename,
     * see {@code target} above - nothing ever overwrote an older one) - only whatever's in {@code
     * keep} has any reason to still be on disk. Normally that's just the version this run
     * confirmed/downloaded (a bootstrap always targets whatever the site currently reports as
     * latest) - but also {@link LastLaunchedJar}'s own recorded version, if it's a different one,
     * so a restricted-channel failure later can still fall back to it (see {@code
     * ChannelFallbackDialog.Choice#LAUNCH_LAST_JAR}) instead of finding it already pruned away.
     * Best-effort: a failure to delete an old jar is disk clutter, not worth failing the actual
     * launch over.
     */
    static void pruneOldLauncherJars(AppDirs dirs, Path... keep) {
        Path cacheDir = dirs.cacheLauncher();
        if (!Files.isDirectory(cacheDir)) {
            return;
        }
        // Arrays.stream + filter, not List.of: List.of rejects null elements outright, and
        // LastLaunchedJar.find() legitimately returns null whenever no jar has been recorded yet -
        // a brand new data directory, which is exactly what a first run in a fresh account (root's,
        // say) has. That combination threw NullPointerException here and killed the whole startup
        // over what is only ever a best-effort cleanup.
        List<Path> keepList = Arrays.stream(keep).filter(Objects::nonNull).toList();
        try (var entries = Files.list(cacheDir)) {
            for (Path entry : entries.toList()) {
                if (keepList.contains(entry) || !entry.getFileName().toString().endsWith(".jar")) {
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

    /** {@code target}'s own filename convention (see {@link #ensureLatestLauncherJar}) is always {@code "<version>.jar"} - this is that inverse. */
    private static String versionOf(Path jar) {
        String filename = jar.getFileName().toString();
        return filename.endsWith(".jar") ? filename.substring(0, filename.length() - 4) : filename;
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

    /**
     * Forwards {@code sn3.appId}, the name of the data folder, exactly like {@code sn3.dataDir}: it may have
     * come from this jar's own {@code jvm.prop}, which the spawned process does not have, and a launcher
     * that fell back to the default folder would quietly split a site's data in two.
     */
    private static void addAppId(List<String> command) {
        String appId = System.getProperty("sn3.appId");
        if (appId != null && !appId.isBlank()) {
            command.add("-Dsn3.appId=" + appId);
        }
    }

    /**
     * Forwards the pinned modpack root key(s), the one signing pin the *launcher* (not this bootstrap) acts on.
     * Like {@code sn3.appId} they typically come from this jar's own {@code jvm.prop}, which the spawned process
     * does not have. Deliberately not forwarded to a replacement bootstrap ({@link #spawnBootstrap}): that jar
     * brings its own {@code jvm.prop}, and an explicit {@code -D} from the old one would override it and make a
     * key rotation impossible.
     */
    private static void addModpackRootKeys(List<String> command) {
        String keys = System.getProperty(TrustedSigningKeys.MODPACK_ROOT_PROPERTY);
        if (keys != null && !keys.isBlank()) {
            command.add("-D" + TrustedSigningKeys.MODPACK_ROOT_PROPERTY + "=" + keys);
        }
    }

    private static Process spawnLauncher(Path jar, String baseUrl, String clientId,
                                         Path readyFile, Path stderrFile) throws IOException {
        List<String> command = new ArrayList<>();
        command.add(javaBinary());
        command.add("-Dsn3.baseUrl=" + baseUrl);
        command.add("-Dsn3.clientId=" + clientId);
        if (Boolean.getBoolean("sn3.offline")) command.add("-Dsn3.offline=true");
        command.add("-D" + StartupSignal.PROPERTY + "=" + readyFile);
        // Not read by this class directly (only AppDirs cares), but a spawned process doesn't
        // inherit its parent's JVM properties on its own - forwarded here so a
        // -Dsn3.dataDir=... passed to bootstrap actually reaches the launcher it spawns too,
        // keeping them pointed at the same (possibly test/dev-only) data directory.
        String dataDir = System.getProperty("sn3.dataDir");
        if (dataDir != null && !dataDir.isBlank()) {
            command.add("-Dsn3.dataDir=" + dataDir);
        }
        addAppId(command);
        addModpackRootKeys(command);
        command.add("-jar");
        command.add(jar.toString());

        // Standard error goes to a file rather than being inherited, so that a launcher which dies
        // on the way up leaves its stack trace somewhere this process can actually read and show
        // (see BootstrapCrashDialog). A file, not a pipe: a parent that stops draining a child's
        // pipe deadlocks it once the buffer fills, and this parent spends its time polling, not
        // reading. Input and output stay inherited so running the bootstrap from a console behaves
        // as it always has.
        return new ProcessBuilder(command)
                .redirectInput(ProcessBuilder.Redirect.INHERIT)
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .redirectError(ProcessBuilder.Redirect.to(stderrFile.toFile()))
                .start();
    }

    /**
     * Starts an updated bootstrap jar and returns immediately, forwarding the same JVM properties
     * this process was given - a spawned process inherits none of them on its own, exactly as in
     * {@link #spawnLauncher}.
     *
     * <p>Standard streams are inherited rather than captured: the child owns the player's
     * experience from here on, including its own splash and any error dialog it needs to show.
     */
    private static void spawnBootstrap(Path jar, String baseUrl, String clientId, Path healTarget) throws IOException {
        List<String> command = new ArrayList<>();
        command.add(javaBinary());
        command.add("-Dsn3.baseUrl=" + baseUrl);
        command.add("-Dsn3.clientId=" + clientId);

        String dataDir = System.getProperty("sn3.dataDir");
        if (dataDir != null && !dataDir.isBlank()) {
            command.add("-Dsn3.dataDir=" + dataDir);
        }
        addAppId(command);

        // Only ever set here, for the copy that is about to take over: see InstalledJarHealer.
        if (healTarget != null) {
            command.add("-D" + InstalledJarHealer.PROPERTY + "=" + healTarget);
        }

        command.add("-jar");
        command.add(jar.toString());

        new ProcessBuilder(command).inheritIO().start();
    }

    /**
     * A per-run scratch file under the cache directory. The pid keeps two bootstraps started at
     * once (an impatient player double-clicking twice) from watching each other's handoff.
     */
    private static Path handoffFile(AppDirs dirs, String kind) throws IOException {
        Path dir = dirs.cache();
        Files.createDirectories(dir);
        return dir.resolve("launcher-" + kind + "-" + ProcessHandle.current().pid() + ".tmp");
    }

    /** Whatever the launcher wrote to standard error, or "" if it wrote nothing/unreadable. */
    private static String readQuietly(Path file) {
        try {
            return Files.exists(file) ? Files.readString(file) : "";
        } catch (Exception e) {
            return "";
        }
    }

    /** Handoff files are scratch, and never worth failing or warning a player over. */
    private static void cleanUpQuietly(Path... files) {
        for (Path file : files) {
            try {
                Files.deleteIfExists(file);
            } catch (Exception ignored) {
                // Left behind at worst; the next run writes its own pid-suffixed pair.
            }
        }
    }

    private static String javaBinary() {
        String javaHome = System.getProperty("java.home");
        String os = System.getProperty("os.name", "").toLowerCase();
        String executable = os.contains("win") ? "java.exe" : "java";
        return Path.of(javaHome, "bin", executable).toString();
    }
}
