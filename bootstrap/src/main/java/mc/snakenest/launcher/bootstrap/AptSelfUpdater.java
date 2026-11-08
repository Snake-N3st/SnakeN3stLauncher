package mc.snakenest.launcher.bootstrap;

import mc.snakenest.launcher.util.Log;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Updates a bootstrap that was installed from the site's {@code .deb} by installing the new
 * {@code .deb}, rather than by writing to the jar dpkg owns.
 *
 * <p><b>Why this exists.</b> {@code BootstrapMain#selfUpdateIfNeeded} already refuses to overwrite a
 * package-managed jar - running the launcher once as root used to replace it with a root-owned 0600
 * file that no other account could then read. Its fallback keeps a private copy in the player's own
 * data directory, which works but leaves the machine in a split state: every user account carries
 * its own copy, {@code apt} still reports the old version, and the packaged jar drifts further
 * behind with every release. Going through the package manager keeps dpkg's view of the system and
 * the actual bytes on disk in agreement, and one install serves every account on the machine.
 *
 * <p><b>Why only pkexec, and not the sudo/su cascade the shell installer uses.</b> That cascade
 * exists for a script a player pasted into a terminal, where a password prompt has somewhere to be
 * typed. This runs from a desktop shortcut with no terminal attached at all: {@code sudo} would
 * block forever on a prompt nobody can see, and {@code su} needs a controlling tty that does not
 * exist here. {@code pkexec} is the one that opens its own window. When it is missing - or the
 * player declines - nothing is forced: the caller falls back to the private per-user copy, so the
 * bootstrap still ends up current either way.
 *
 * <p>Everything here is best-effort by construction. A failure at any step returns {@code false}
 * and is logged; it never propagates, because the worst outcome allowed is "updated the old way".
 */
final class AptSelfUpdater {

    /**
     * Set by the {@code bootstrap.properties} file the package installs next to its jar (see the
     * site's {@code DebPackageGenerator}), read into a system property by
     * {@code BootstrapMain#loadPropertiesFileNextToJar}. A marker file rather than a runtime probe
     * such as {@code dpkg -S}: it costs nothing, works before anything else is initialised, and
     * answers the question actually being asked - "did *this* package put the jar here" - which a
     * path check cannot.
     */
    private static final String MARKER_PROPERTY = "sn3.aptInstall";

    /**
     * Generous, because most of it is spent waiting on a human: pkexec's authentication window
     * stays up until the player types a password or dismisses it. Bounded all the same, so a dialog
     * left untouched on a locked screen eventually gives the launcher back instead of hanging the
     * startup forever.
     */
    private static final long INSTALL_TIMEOUT_MINUTES = 5;

    private AptSelfUpdater() {
    }

    /** Whether this bootstrap was installed by the Debian package. */
    static boolean isAptInstall() {
        return Boolean.parseBoolean(System.getProperty(MARKER_PROPERTY));
    }

    /**
     * Downloads the published package, verifies it, and hands it to {@code apt-get} with whatever
     * privilege escalation this session allows.
     *
     * @return {@code true} only if the install command itself reported success. The caller still
     * has to confirm the packaged jar actually changed - see {@code BootstrapMain}, which re-hashes
     * it. An exit code is a claim; the hash is the proof, and without that second check a package
     * manager that silently kept the old version would send the bootstrap into a reinstall loop on
     * every single start.
     */
    static boolean install(LauncherReleaseClient releaseClient, LauncherReleaseInfo latest, BootstrapSplash splash) {
        if (latest.deb() == null) {
            Log.info(AptSelfUpdater.class, "The site published no Debian package for this bootstrap release.");
            return false;
        }

        List<String> elevation = elevationPrefix();
        if (elevation == null) {
            return false;
        }

        if (!aptGetAvailable()) {
            Log.info(AptSelfUpdater.class, "apt-get is not on PATH; leaving the packaged jar alone.");
            return false;
        }

        Path workDir = null;
        try {
            // From here on the player is asked things - first by this class, then by pkexec - and
            // an always-on-top splash would sit over both. See BootstrapSplash#setAlwaysOnTop.
            yieldFront(splash, true);

            if (!confirmWithPlayer(latest.version())) {
                Log.info(AptSelfUpdater.class, "The player declined the package update.");
                return false;
            }

            workDir = createReadableWorkDirectory();
            Path deb = workDir.resolve("snake-n3st-launcher.deb");

            setStatus(splash, "Téléchargement de la mise à jour du bootstrap...");
            releaseClient.downloadDeb(latest.deb(), deb);
            // Written 0600 by AtomicFiles (it creates its temp file with the JDK's own restrictive
            // default), and apt drops to the "_apt" user to fetch even a local file - it has to be
            // able to read this one, or apt falls back with the alarming "download is performed
            // unsandboxed as root" warning. Nothing secret here: this is the package the site hands
            // to anyone who asks for it.
            makeReadable(deb, "rw-r--r--");

            setStatus(splash, "Installation de la mise à jour (droits administrateur)...");
            return runInstall(elevation, deb);
        } catch (Exception e) {
            Log.warn(AptSelfUpdater.class, "Could not update through the package manager: " + e.getMessage());
            return false;
        } finally {
            yieldFront(splash, false);
            deleteQuietly(workDir);
        }
    }

    /**
     * What to put in front of {@code apt-get}, or {@code null} when this session has no way to
     * reach root at all.
     */
    private static List<String> elevationPrefix() {
        if (isRoot()) {
            // Nothing to elevate to. Notably the case on the root-account distributions that ship
            // no sudo at all, which is where the "packaged jar rewritten as 0600" bug came from in
            // the first place.
            return List.of();
        }

        String pkexec = onPath("pkexec");
        if (pkexec == null) {
            Log.info(AptSelfUpdater.class, "No pkexec available to install the package; falling back to a per-user copy.");
            return null;
        }

        if (GraphicsEnvironment.isHeadless()) {
            // pkexec needs a polkit agent to show its prompt, and there is none without a session
            // to show it in - it would just fail after a pause.
            Log.info(AptSelfUpdater.class, "Headless session, so pkexec has nowhere to prompt; falling back to a per-user copy.");
            return null;
        }

        return List.of(pkexec);
    }

    /**
     * Asked before pkexec is, deliberately. An authentication window appearing unannounced while a
     * game launcher starts is exactly the shape of thing players are told not to type their password
     * into; this explains where it came from a second before it appears. Declining is free - the
     * caller updates the private copy instead.
     */
    private static boolean confirmWithPlayer(String version) {
        if (isRoot()) {
            // No password will be asked for, and there is no third party to reassure: this session
            // already has the rights. Interrupting a startup to announce a silent, safe upgrade
            // would be noise.
            return true;
        }

        if (GraphicsEnvironment.isHeadless()) {
            return false;
        }

        String message = """
                Une mise à jour du launcher (version %s) est disponible.

                Elle est installée via le gestionnaire de paquets, pour tous les
                comptes de cette machine : une fenêtre d'authentification va s'ouvrir.

                Tu peux refuser : la mise à jour sera alors installée uniquement
                pour ton compte.""".formatted(version);

        try {
            BootstrapTheme.applyToDialogs();
            Object[] options = {"Mettre à jour", "Plus tard"};
            int[] choice = {JOptionPane.CLOSED_OPTION};
            SwingUtilities.invokeAndWait(() -> choice[0] = JOptionPane.showOptionDialog(
                    null, message, "Mise à jour du launcher",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                    null, options, options[0]));
            return choice[0] == 0;
        } catch (Exception e) {
            // Closing the window, or anything at all going wrong while asking, reads as "no" -
            // declining costs the player nothing but a per-user copy.
            return false;
        }
    }

    private static void yieldFront(BootstrapSplash splash, boolean yield) {
        if (splash != null) {
            splash.setAlwaysOnTop(!yield);
        }
    }

    /**
     * Runs the install and waits for it, capturing its output into the log so a failure leaves
     * something to read rather than a bare exit code.
     */
    private static boolean runInstall(List<String> elevationPrefix, Path deb) throws IOException, InterruptedException {
        List<String> command = installCommand(elevationPrefix, deb);
        Log.info(AptSelfUpdater.class, "Installing the Debian package: " + String.join(" ", command));

        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();

        // Drained on its own thread, and that is not a style choice: a child whose output nobody
        // reads deadlocks once the pipe buffer fills (apt is not quiet), while reading it inline
        // would block until the child closes the stream - which is to say until it exits, making
        // the timeout below unreachable. Both hazards at once, and only a separate thread avoids
        // both.
        CompletableFuture<String> output = new CompletableFuture<>();
        Thread drain = new Thread(() -> {
            try (var in = process.getInputStream()) {
                output.complete(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                output.complete("");
            }
        }, "apt-install-output");
        drain.setDaemon(true);
        drain.start();

        if (!process.waitFor(INSTALL_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
            process.destroyForcibly();
            Log.warn(AptSelfUpdater.class, "The package install did not finish within "
                    + INSTALL_TIMEOUT_MINUTES + " minutes; giving up on it.");
            return false;
        }

        // The process has exited, but the reader may not have seen the end of the stream yet.
        drain.join(TimeUnit.SECONDS.toMillis(5));
        String text = output.getNow("").strip();

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            // 126/127 are pkexec's own codes for "the player dismissed the prompt" and
            // "authorisation refused" - ordinary outcomes, not faults, and the caller handles them
            // exactly like any other failure.
            Log.warn(AptSelfUpdater.class, "The package install exited with " + exitCode + ": " + text);
            return false;
        }

        Log.info(AptSelfUpdater.class, "Package install finished: " + text);
        return true;
    }

    /**
     * The full command line. Extracted so the shape of it can be asserted without running a package
     * manager: the absolute path and the {@code -y} are not cosmetic, see below.
     */
    static List<String> installCommand(List<String> elevationPrefix, Path deb) {
        List<String> command = new ArrayList<>(elevationPrefix);
        command.add("apt-get");
        command.add("install");
        // Nothing can be typed at this process, so any question apt might ask has to be answered in
        // advance; -y is what turns "do you want to continue?" from a hang into a yes.
        command.add("-y");
        // apt only treats an argument as a file when it looks like a path rather than a package
        // name - an absolute one always does. Installing the file (rather than "apt-get install
        // snake-n3st-launcher") is the whole point: this exact package is the one just verified,
        // and it may well be newer than anything a configured repository would offer.
        command.add(deb.toAbsolutePath().toString());
        return command;
    }

    /**
     * A private directory that {@code _apt} can nonetheless walk into. Created 0700 by the JDK and
     * widened to 0755 rather than created world-writable: the directory stays owned by this user and
     * writable only by them, so nothing else on the machine can swap the package out between the
     * hash check and the moment root installs it.
     */
    private static Path createReadableWorkDirectory() throws IOException {
        Path dir = Files.createTempDirectory("sn3-bootstrap-deb-");
        makeReadable(dir, "rwxr-xr-x");
        return dir;
    }

    private static void makeReadable(Path path, String posixPermissions) {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(posixPermissions));
        } catch (Exception e) {
            // Only reachable on a filesystem without POSIX permissions, which is not a filesystem
            // apt runs on - and apt's own sandbox warning is cosmetic anyway.
            Log.info(AptSelfUpdater.class, "Could not set permissions on " + path + ": " + e);
        }
    }

    private static boolean isRoot() {
        // "user.name" rather than a native uid call: the bootstrap deliberately carries no JNI or
        // extra dependency, and this is only ever used to decide whether to *skip* asking for
        // rights - a wrong answer costs an unnecessary pkexec prompt, never a lost update.
        return "root".equals(System.getProperty("user.name"));
    }

    private static boolean aptGetAvailable() {
        return onPath("apt-get") != null;
    }

    /** The absolute path of {@code executable} on {@code PATH}, or {@code null}. */
    private static String onPath(String executable) {
        String path = System.getenv("PATH");
        if (path == null) {
            return null;
        }
        for (String directory : path.split(java.io.File.pathSeparator)) {
            if (directory.isBlank()) {
                continue;
            }
            Path candidate = Path.of(directory, executable);
            if (Files.isExecutable(candidate)) {
                return candidate.toString();
            }
        }
        return null;
    }

    private static void setStatus(BootstrapSplash splash, String status) {
        if (splash != null) {
            splash.setStatus(status);
        }
    }

    /** The package is a scratch file; failing to remove it is never worth a word to the player. */
    private static void deleteQuietly(Path directory) {
        if (directory == null) {
            return;
        }
        try (var entries = Files.list(directory)) {
            for (Path entry : entries.toList()) {
                Files.deleteIfExists(entry);
            }
        } catch (IOException ignored) {
            // Falls through to the directory delete below, which then simply fails too.
        }
        try {
            Files.deleteIfExists(directory);
        } catch (IOException ignored) {
            // Left in /tmp at worst, which the system clears on its own schedule.
        }
    }
}
