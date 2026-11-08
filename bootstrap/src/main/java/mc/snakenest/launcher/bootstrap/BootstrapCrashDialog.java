package mc.snakenest.launcher.bootstrap;

import com.google.gson.JsonObject;
import mc.snakenest.launcher.util.Log;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Shown when the launcher process dies with a non-zero status before ever putting a window on
 * screen - see {@link LauncherStartupWatcher}.
 *
 * <p>Without this the bootstrap simply exited alongside it, so the player watched a splash appear,
 * vanish, and nothing happen; the only trace was a log file they had no reason to know about. Here
 * they get the exit status, the launcher's own dying words (its standard error, which is where a
 * JVM that fails to start prints its stack trace), and one button to send it in.
 *
 * <p><b>Sending is always an explicit click.</b> Nothing is transmitted automatically: the output
 * shown here can contain file paths, usernames and other things off the player's machine, so the
 * player sees exactly what would be sent, in full, before deciding. "Copier" is offered for anyone
 * who would rather paste it into Discord than post it to the site.
 */
final class BootstrapCrashDialog {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    /** Matches {@code BugReport::TYPE_LAUNCHER_CRASH} on the site. */
    private static final String REPORT_TYPE = "launcher_crash";

    /** Trailing characters of the captured stderr kept for the report - see {@link #tail}. */
    private static final int MAX_DETAILS = 15_000;

    private BootstrapCrashDialog() {
    }

    /**
     * Blocks until the player closes the window. A no-op in a headless environment, where the
     * caller has already logged and printed the same information.
     *
     * @param exitCode the launcher's exit status
     * @param stderr   whatever the launcher wrote to standard error, possibly empty
     */
    static void show(int exitCode, String stderr, URI baseUrl, String clientId) {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }

        try {
            BootstrapTheme.applyToDialogs();
            SwingUtilities.invokeAndWait(() -> build(exitCode, tail(stderr), baseUrl, clientId));
        } catch (Exception e) {
            Log.error(BootstrapCrashDialog.class, "Could not show the crash dialog", e);
        }
    }

    private static void build(int exitCode, String details, URI baseUrl, String clientId) {
        JDialog dialog = new JDialog((java.awt.Frame) null, "SnakeN3st Launcher", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel content = new JPanel(new BorderLayout(0, 14));
        content.setBackground(BootstrapTheme.current().background);
        content.setBorder(BorderFactory.createEmptyBorder(22, 24, 20, 24));

        content.add(headings(exitCode), BorderLayout.NORTH);
        content.add(detailsArea(details), BorderLayout.CENTER);

        JLabel status = new JLabel(" ");
        status.setForeground(BootstrapTheme.current().muted);

        content.add(buttons(dialog, details, status, exitCode, baseUrl, clientId), BorderLayout.SOUTH);

        dialog.setContentPane(content);
        dialog.setSize(720, 480);
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
    }

    private static JPanel headings(int exitCode) {
        JPanel box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Le launcher n'a pas réussi à démarrer");
        title.setForeground(BootstrapTheme.current().danger);
        title.setFont(BootstrapTheme.font(title.getFont(), Font.BOLD, 16f));
        title.setAlignmentX(0f);
        box.add(title);

        box.add(Box.createVerticalStrut(6));

        JLabel lead = new JLabel("Il s'est arrêté avec le code " + exitCode
                + " avant d'afficher sa fenêtre. Voici ce qu'il a signalé :");
        lead.setForeground(BootstrapTheme.current().muted);
        lead.setAlignmentX(0f);
        box.add(lead);

        return box;
    }

    private static JScrollPane detailsArea(String details) {
        JTextArea area = new JTextArea(details.isBlank()
                ? "(le launcher n'a rien écrit sur sa sortie d'erreur)"
                : details);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBackground(BootstrapTheme.current().recessed);
        area.setForeground(BootstrapTheme.current().text);
        area.setCaretPosition(0);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(BorderFactory.createLineBorder(BootstrapTheme.current().border));
        scroll.setPreferredSize(new Dimension(0, 240));
        return scroll;
    }

    private static JPanel buttons(JDialog dialog, String details, JLabel status, int exitCode,
                                  URI baseUrl, String clientId) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);

        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.X_AXIS));

        JButton send = new JButton("Envoyer le rapport");
        JButton copy = new JButton("Copier");
        JButton close = new JButton("Fermer");

        send.addActionListener(e -> {
            send.setEnabled(false);
            status.setText("Envoi en cours...");
            // Off the EDT: a hung site must not freeze the one window telling the player what
            // went wrong.
            new Thread(() -> {
                boolean ok = post(baseUrl, clientId, exitCode, details);
                SwingUtilities.invokeLater(() -> {
                    status.setText(ok ? "Rapport envoyé, merci." : "L'envoi a échoué. Utilise « Copier ».");
                    status.setForeground(ok ? BootstrapTheme.current().muted : BootstrapTheme.current().danger);
                    send.setEnabled(!ok);
                });
            }, "crash-report-send").start();
        });

        copy.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(details), null);
            status.setText("Copié dans le presse-papiers.");
        });

        close.addActionListener(e -> dialog.dispose());

        right.add(send);
        right.add(Box.createHorizontalStrut(8));
        right.add(copy);
        right.add(Box.createHorizontalStrut(8));
        right.add(close);

        row.add(status, BorderLayout.CENTER);
        row.add(right, BorderLayout.EAST);
        return row;
    }

    /**
     * Posts to the site's unauthenticated bug-report endpoint - unsigned precisely because a
     * launcher crash can happen before any login has ever succeeded, so there is no key to sign
     * with. Returns whether the site accepted it; the caller turns that into the one line of
     * feedback the player sees.
     */
    private static boolean post(URI baseUrl, String clientId, int exitCode, String details) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("client_id", clientId);
            body.addProperty("type", REPORT_TYPE);
            body.addProperty("summary", "Le launcher n'a pas démarré (code " + exitCode + ")");
            body.addProperty("details", details);
            body.addProperty("system_info", systemInfo());

            HttpRequest request = HttpRequest
                    .newBuilder(baseUrl.resolve("/api/launcher-auth/bug-reports"))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            boolean accepted = response.statusCode() / 100 == 2;

            if (!accepted) {
                Log.warn(BootstrapCrashDialog.class,
                        "The site rejected the crash report (HTTP " + response.statusCode() + ").");
            }

            return accepted;
        } catch (Exception e) {
            Log.error(BootstrapCrashDialog.class, "Could not send the crash report", e);
            return false;
        }
    }

    private static String systemInfo() {
        return System.getProperty("os.name") + " " + System.getProperty("os.version")
                + " (" + System.getProperty("os.arch") + "), Java "
                + System.getProperty("java.version");
    }

    /**
     * Keeps only the last {@link #MAX_DETAILS} characters. A JVM that fails to start can print a
     * great deal, and it is the end - the actual error - that matters; the site caps the field
     * anyway.
     */
    private static String tail(String text) {
        if (text == null) {
            return "";
        }

        String trimmed = text.strip();

        return trimmed.length() <= MAX_DETAILS
                ? trimmed
                : "[...]\n" + trimmed.substring(trimmed.length() - MAX_DETAILS);
    }
}
