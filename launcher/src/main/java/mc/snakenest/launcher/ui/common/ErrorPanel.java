package mc.snakenest.launcher.ui.common;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

/**
 * A centered error message + "Copier la stacktrace"/"Réessayer"/"Retour" buttons, shown in place
 * of a page's content when it couldn't be loaded - e.g. a modpack's detail page after the server
 * rate-limited a spammed "Actualiser" click (429). Previously a failure here bounced the player
 * straight back to the list plus a modal {@code JOptionPane}, which read as the page being
 * stuck/broken rather than as a recoverable, retryable state - this replaces the page's content
 * instead, the same "loading" vs "here's what's actually on screen" reasoning as
 * {@link LoadingPanel}. The copy button exists so a player reporting a bug can paste the actual
 * stack trace instead of just the one-line message shown on screen.
 */
public final class ErrorPanel extends JPanel {

    // Wide enough to comfortably fit a typical exception message on 2-3 lines rather than one
    // unbroken line running off the edge of the content area.
    private static final int WRAP_WIDTH_PX = 420;

    public ErrorPanel(String message, String stackTrace, Runnable onRetry, Runnable onBack) {
        this(message, stackTrace, onRetry, onBack, null);
    }

    public ErrorPanel(String message, String stackTrace, Runnable onRetry, Runnable onBack, Runnable onOffline) {
        setLayout(new GridBagLayout());
        setOpaque(false);

        JPanel inner = new JPanel();
        inner.setOpaque(false);
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));

        // HTML (escaped - message ultimately comes from an exception's getMessage(), not
        // trusted input) rather than a plain JLabel, purely to get word wrapping at a fixed
        // width - a plain JLabel never wraps and just runs off the edge for a long message.
        JLabel label = new JLabel("<html><div style='width:" + WRAP_WIDTH_PX + "px;text-align:center'>"
                + escapeHtml(message) + "</div></html>", SwingConstants.CENTER);
        label.setForeground(Colors.danger());
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        inner.add(label);
        inner.add(Box.createVerticalStrut(16));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        buttons.setOpaque(false);
        buttons.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton backButton = new JButton("Retour");
        backButton.addActionListener(e -> onBack.run());
        buttons.add(backButton);

        JButton copyButton = new JButton("Copier la stacktrace");
        copyButton.addActionListener(e -> copyToClipboard(copyButton, stackTrace));
        buttons.add(copyButton);

        JButton retryButton = new JButton("Réessayer");
        retryButton.addActionListener(e -> onRetry.run());
        buttons.add(retryButton);

        if (onOffline != null) {
            JButton offline = new JButton("Mode hors connexion");
            offline.addActionListener(e -> onOffline.run()); buttons.add(offline);
        }
        inner.add(buttons);
        add(inner);
    }

    /** Briefly swaps the button's own label to "Copié !" rather than a separate toast/dialog - enough feedback for a one-off action. */
    private static void copyToClipboard(JButton button, String text) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
        String original = button.getText();
        button.setText("Copié !");
        button.setEnabled(false);
        Timer timer = new Timer(1500, e -> {
            button.setText(original);
            button.setEnabled(true);
        });
        timer.setRepeats(false);
        timer.start();
    }

    private static String escapeHtml(String text) {
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
