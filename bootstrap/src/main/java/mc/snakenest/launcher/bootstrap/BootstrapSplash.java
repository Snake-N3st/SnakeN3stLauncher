package mc.snakenest.launcher.bootstrap;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;

/**
 * The window shown while the bootstrap checks for/downloads a launcher update before handing off -
 * the same idea as the splash GIMP or IntelliJ IDEA show while they start.
 *
 * <p>This is the first thing a player ever sees of the launcher, and for a first run (which
 * downloads the whole launcher jar) it can be on screen for a while, so it is branded with the
 * site's own wordmark and painted in the launcher's palette rather than looking like a stock Swing
 * dialog. See {@link BootstrapTheme} for why those colours are duplicated here.
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

    /** The wide wordmark shipped in this module's resources, drawn as the splash header. */
    private static final String LOGO_RESOURCE = "/logo_large.png";

    /** Width the logo is scaled to. The source is 314x51; the height follows from its aspect ratio. */
    private static final int LOGO_WIDTH = 300;

    private final JFrame frame = new JFrame();
    private final JLabel statusLabel = new JLabel("Démarrage...", SwingConstants.CENTER);
    private final Sweep sweep = new Sweep();

    private BootstrapSplash() {
        frame.setUndecorated(true);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setAlwaysOnTop(true);
        frame.setTitle("SnakeN3st Launcher");

        JPanel content = new JPanel(new BorderLayout(0, 18));
        content.setBackground(BootstrapTheme.current().background);
        content.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BootstrapTheme.current().border),
                BorderFactory.createEmptyBorder(30, 34, 26, 34)));

        content.add(header(), BorderLayout.NORTH);

        statusLabel.setForeground(BootstrapTheme.current().muted);
        statusLabel.setFont(BootstrapTheme.font(statusLabel.getFont(), Font.PLAIN, 13f));
        content.add(statusLabel, BorderLayout.CENTER);

        content.add(sweep, BorderLayout.SOUTH);

        frame.setContentPane(content);
        frame.setSize(440, 210);
        frame.setLocationRelativeTo(null);
    }

    /**
     * The wordmark, or a plain text title if the image can't be loaded for any reason - the splash
     * is worth showing either way, and an unbranded splash beats no splash at all.
     */
    private static JComponent header() {
        BufferedImage logo = loadLogo();

        if (logo == null) {
            JLabel title = new JLabel("SnakeN3st Launcher", SwingConstants.CENTER);
            title.setForeground(BootstrapTheme.current().text);
            title.setFont(BootstrapTheme.font(title.getFont(), Font.BOLD, 20f));
            return title;
        }

        int height = Math.max(1, Math.round(logo.getHeight() * (LOGO_WIDTH / (float) logo.getWidth())));
        // SCALE_SMOOTH rather than the default: the wordmark is pixel art with hard edges, and a
        // nearest-neighbour downscale of it visibly breaks up the lettering.
        Image scaled = logo.getScaledInstance(LOGO_WIDTH, height, Image.SCALE_SMOOTH);

        JLabel label = new JLabel(new ImageIcon(scaled), SwingConstants.CENTER);
        label.setOpaque(false);
        return label;
    }

    private static BufferedImage loadLogo() {
        try (InputStream in = BootstrapSplash.class.getResourceAsStream(LOGO_RESOURCE)) {
            return in == null ? null : ImageIO.read(in);
        } catch (Exception e) {
            return null;
        }
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
            BootstrapTheme.applyToDialogs();
            BootstrapSplash[] holder = new BootstrapSplash[1];
            SwingUtilities.invokeAndWait(() -> {
                holder[0] = new BootstrapSplash();
                holder[0].frame.setVisible(true);
                holder[0].sweep.start();
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

    /**
     * Lets the splash stop insisting on being the frontmost window, and take it up again afterwards.
     *
     * <p>Always-on-top is right for a splash and wrong for anything that has to be read: it sits
     * above dialogs belonging to other processes too, and the one that matters here is pkexec's
     * authentication prompt (see {@link AptSelfUpdater}). A password window half-hidden behind a
     * splash is not a cosmetic problem - it is a player who cannot see what they are being asked to
     * authorise. Yielding rather than closing, so the splash is still there to go back to.
     *
     * <p>Safe from any thread.
     */
    void setAlwaysOnTop(boolean alwaysOnTop) {
        SwingUtilities.invokeLater(() -> frame.setAlwaysOnTop(alwaysOnTop));
    }

    /** Safe from any thread. */
    void close() {
        SwingUtilities.invokeLater(() -> {
            sweep.stop();
            frame.dispose();
        });
    }

    /**
     * A slim indeterminate progress bar: a rounded accent segment sweeping across a darker trough.
     *
     * <p>Hand-painted rather than a {@link javax.swing.JProgressBar} because the stock
     * indeterminate bar is rendered by whichever look-and-feel is installed, and this module ships
     * none - so on the default Metal L&F it would be the dated 1990s bar, right in the middle of
     * the one window that is supposed to make a good first impression.
     */
    private static final class Sweep extends JComponent {

        private static final int HEIGHT = 6;
        private static final int SEGMENT_WIDTH = 110;
        private static final int STEP_PIXELS = 6;
        private static final int FRAME_DELAY_MS = 16;

        /** Declared before the timer below, whose lambda reads it. */
        private int offset;

        private final Timer timer = new Timer(FRAME_DELAY_MS, e -> {
            offset += STEP_PIXELS;
            repaint();
        });

        Sweep() {
            setOpaque(false);
            setPreferredSize(new Dimension(0, HEIGHT));
        }

        void start() {
            timer.start();
        }

        void stop() {
            timer.stop();
        }

        @Override
        protected void paintComponent(Graphics g) {
            int width = getWidth();
            if (width <= 0) {
                return;
            }

            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                g2.setColor(BootstrapTheme.current().recessed);
                g2.fillRoundRect(0, 0, width, HEIGHT, HEIGHT, HEIGHT);

                // The segment travels one full segment-width beyond each edge, so it slides fully
                // out of view before reappearing rather than snapping back at the boundary.
                int span = width + SEGMENT_WIDTH;
                int x = (offset % span) - SEGMENT_WIDTH;

                // Clipping to the trough keeps the segment's rounded ends from poking outside it
                // while it is partway off either edge.
                g2.setClip(new java.awt.geom.RoundRectangle2D.Float(0, 0, width, HEIGHT, HEIGHT, HEIGHT));
                g2.setColor(BootstrapTheme.current().accent);
                g2.fillRoundRect(x, 0, SEGMENT_WIDTH, HEIGHT, HEIGHT, HEIGHT);
            } finally {
                g2.dispose();
            }
        }
    }

    /**
     * A blocking, non-retryable error dialog for a configuration problem retrying wouldn't fix
     * (e.g. no valid clientId at all) - the console message (always printed regardless) is the
     * fallback if this can't show for any reason (headless, no display server, etc.).
     */
    static void showFatalError(String message) {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        try {
            BootstrapTheme.applyToDialogs();
            SwingUtilities.invokeAndWait(() ->
                    JOptionPane.showMessageDialog(null, message, "SnakeN3st Launcher", JOptionPane.ERROR_MESSAGE));
        } catch (Exception ignored) {
            // Best-effort only.
        }
    }

    /**
     * Same as {@link #showFatalError}, but for a failure retrying might actually fix (a network
     * hiccup, the site being briefly down, a transient download error) - offers "Réessayer" next
     * to "Fermer" instead of a single "OK". Returns whether the player chose to retry; {@code
     * BootstrapMain#main} loops back to the start of the startup attempt in that case. Bounded
     * only by the player themselves clicking "Fermer" - there's a human in the loop, so this
     * can't spin forever the way silently retrying on a timer could.
     *
     * @return {@code true} if the player chose to retry, {@code false} otherwise (including
     * headless/no-display environments, where there's no one to click anything, so retrying
     * automatically would risk looping forever on a persistent failure)
     */
    static boolean confirmOfflineFallback() {
        if (GraphicsEnvironment.isHeadless()) return false;
        try {
            BootstrapTheme.applyToDialogs();
            int[] choice = {JOptionPane.CLOSED_OPTION};
            SwingUtilities.invokeAndWait(() -> choice[0] = JOptionPane.showOptionDialog(null,
                    "Le site est inaccessible. Utiliser le launcher déjà présent et les modpacks installés sur cet appareil ?",
                    "Connexion indisponible", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null,
                    new String[]{"Mode hors connexion", "Annuler"}, "Mode hors connexion"));
            return choice[0] == 0;
        } catch (Exception ignored) { return false; }
    }

    static boolean showFatalErrorWithRetry(String message) {
        if (GraphicsEnvironment.isHeadless()) {
            return false;
        }
        try {
            BootstrapTheme.applyToDialogs();
            Object[] options = {"Réessayer", "Fermer"};
            int[] choice = {JOptionPane.CLOSED_OPTION};
            SwingUtilities.invokeAndWait(() -> choice[0] = JOptionPane.showOptionDialog(
                    null, message, "SnakeN3st Launcher",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE,
                    null, options, options[0]));
            return choice[0] == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * A blocking info dialog telling the player a self-update was just applied and they need to
     * relaunch manually - see {@code BootstrapMain#selfUpdateIfNeeded}'s Javadoc for why the
     * bootstrap doesn't just re-exec itself instead. A no-op in a headless environment, same as
     * every other entry point here - the caller ({@code BootstrapMain#main}) already logs the
     * same message regardless.
     */
    static void showRestartPrompt(String message) {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        try {
            BootstrapTheme.applyToDialogs();
            SwingUtilities.invokeAndWait(() ->
                    JOptionPane.showMessageDialog(null, message, "SnakeN3st Launcher", JOptionPane.INFORMATION_MESSAGE));
        } catch (Exception ignored) {
            // Best-effort only.
        }
    }
}
