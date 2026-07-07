package mc.snakenest.launcher.bootstrap;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;

/**
 * A minimal "loading..." popup shown for however long the bootstrap needs to check for/download
 * a launcher update before handing off - the same idea as the splash screen GIMP or IntelliJ
 * IDEA show while they start up, so the player isn't staring at a blank taskbar entry (or
 * nothing at all) for however long that takes.
 *
 * <p>Deliberately self-contained rather than reusing the launcher module's own {@code
 * ui.common.LoadingPanel} - this module must stay free of the launcher module's GPL
 * dependencies (see this package's Javadoc/{@code pom.xml} comment), and depending on a
 * launcher-module class here at all would blur that boundary even though {@code LoadingPanel}
 * itself carries no GPL code today.
 *
 * <p>Swing/AWT only, same as the rest of this project - and entirely optional: every entry
 * point here is a no-op (or returns {@code null}) in a headless environment instead of failing
 * the bootstrap over a UI nicety.
 */
final class BootstrapSplash {

    private final JFrame frame = new JFrame();
    private final JLabel statusLabel = new JLabel("Démarrage...", SwingConstants.CENTER);

    private BootstrapSplash() {
        frame.setUndecorated(true);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setAlwaysOnTop(true);

        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBackground(Color.WHITE);
        content.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0, 0, 0, 60)),
                BorderFactory.createEmptyBorder(28, 36, 28, 36)));

        JLabel title = new JLabel("SnakeN3st Launcher", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        content.add(title, BorderLayout.NORTH);

        statusLabel.setFont(statusLabel.getFont().deriveFont(13f));
        content.add(statusLabel, BorderLayout.CENTER);

        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        content.add(progressBar, BorderLayout.SOUTH);

        frame.setContentPane(content);
        frame.setSize(380, 150);
        frame.setLocationRelativeTo(null);
    }

    /**
     * Builds and shows the splash, or returns {@code null} in a headless environment (or if
     * anything about showing a window goes wrong) - callers must null-check before use.
     */
    static BootstrapSplash showIfPossible() {
        if (GraphicsEnvironment.isHeadless()) {
            return null;
        }
        try {
            BootstrapSplash[] holder = new BootstrapSplash[1];
            SwingUtilities.invokeAndWait(() -> {
                holder[0] = new BootstrapSplash();
                holder[0].frame.setVisible(true);
            });
            return holder[0];
        } catch (Exception e) {
            return null;
        }
    }

    /** Safe from any thread. */
    void setStatus(String status) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(status));
    }

    /** Safe from any thread. */
    void close() {
        SwingUtilities.invokeLater(frame::dispose);
    }

    /**
     * A blocking error dialog for when the bootstrap can't hand off to the launcher at all -
     * the console message (always printed regardless) is the fallback if this can't show for
     * any reason (headless, no display server, etc.).
     */
    static void showFatalError(String message) {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        try {
            SwingUtilities.invokeAndWait(() ->
                    JOptionPane.showMessageDialog(null, message, "SnakeN3st Launcher", JOptionPane.ERROR_MESSAGE));
        } catch (Exception ignored) {
            // Best-effort only.
        }
    }
}
