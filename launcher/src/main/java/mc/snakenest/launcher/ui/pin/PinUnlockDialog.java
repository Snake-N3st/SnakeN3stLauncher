package mc.snakenest.launcher.ui.pin;

import mc.snakenest.launcher.crypto.PinProtection;
import mc.snakenest.launcher.ui.common.Colors;
import mc.snakenest.launcher.util.Log;
import mc.snakenest.launcher.util.StartupSignal;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

/**
 * The PIN gate shown at startup when {@link PinProtection#isEnabled()}. Blocking: the launcher has
 * nothing to show until the token is decryptable, so this is deliberately its own window rather
 * than a page inside the shell (same reasoning as {@code ui.LoginFrame}).
 *
 * <p><b>A frame, not a dialog.</b> It used to be a modal {@code JDialog} with no owner, which on
 * every desktop means a window with no taskbar entry - a player who clicked away from it had no way
 * back to it and reasonably concluded the launcher had failed to start. A {@code JFrame} gets its
 * own taskbar button. The modality that used to make {@code setVisible(true)} block is replaced by
 * the latch in {@link #showAndAwait}, which is why that method must not run on the EDT.
 *
 * <p>Verification is done by the caller, not here - this dialog can't tell a right PIN from a
 * wrong one on its own (deriving a key always "works"; only decrypting the token fails, see
 * {@code crypto.PinProtection}). So the caller passes a {@link Verifier}, and a rejection just
 * shows an error and lets the player try again.
 *
 * <p>Includes the "Code oublié ?" escape hatch: there is genuinely no recovery for a forgotten PIN
 * (that's the point of not storing the key), so the only thing it can offer is wiping the token
 * and the encrypted modpack files and starting over from the login screen.
 */
public final class PinUnlockDialog extends JFrame {

    /** What the caller does with a candidate PIN: derive the key and try to actually use it. */
    public interface Verifier {
        /** @return true if this PIN unlocked the token */
        boolean tryUnlock(char[] pin);
    }

    /** What the player chose. */
    public enum Outcome {
        /** {@link Verifier#tryUnlock} accepted a PIN - the caller already holds the derived key. */
        UNLOCKED,
        /** "Code oublié ?" was confirmed - the caller must wipe the token and encrypted files. */
        FORGOTTEN,
        /** The window was closed - the launcher should exit rather than run without a session. */
        CANCELLED
    }

    private final JPasswordField pinField = new JPasswordField(12);
    private final JLabel errorLabel = new JLabel(" ", SwingConstants.CENTER);
    private final CountDownLatch finished = new CountDownLatch(1);
    private volatile Outcome outcome = Outcome.CANCELLED;

    private PinUnlockDialog(Verifier verifier, BufferedImage icon) {
        super("SnakeN3st Launcher - Code PIN");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        if (icon != null) {
            setIconImage(icon);
        }

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(24, 28, 20, 28));

        JLabel title = new JLabel("Code PIN", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(title);
        content.add(Box.createVerticalStrut(8));

        JLabel explanation = new JLabel("<html><center>Entre ton code PIN pour déverrouiller ta session.</center></html>", SwingConstants.CENTER);
        explanation.setFont(explanation.getFont().deriveFont(13f));
        explanation.setForeground(UIManager.getColor("Label.disabledForeground"));
        explanation.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(explanation);
        content.add(Box.createVerticalStrut(16));

        pinField.setMaximumSize(new Dimension(200, 32));
        pinField.setHorizontalAlignment(SwingConstants.CENTER);
        pinField.setAlignmentX(Component.CENTER_ALIGNMENT);
        pinField.addActionListener(e -> submit(verifier));
        content.add(pinField);
        content.add(Box.createVerticalStrut(8));

        errorLabel.setFont(errorLabel.getFont().deriveFont(12f));
        errorLabel.setForeground(Colors.danger());
        errorLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(errorLabel);
        content.add(Box.createVerticalStrut(12));

        JButton unlock = new JButton("Déverrouiller");
        unlock.putClientProperty("JButton.buttonType", "roundRect");
        unlock.addActionListener(e -> submit(verifier));
        JPanel buttons = new JPanel();
        buttons.setAlignmentX(Component.CENTER_ALIGNMENT);
        buttons.add(unlock);
        content.add(buttons);
        content.add(Box.createVerticalStrut(4));

        content.add(forgottenLink());

        setContentPane(new JPanel(new BorderLayout()));
        getContentPane().add(content, BorderLayout.CENTER);
        setSize(400, 300);
        setMinimumSize(new Dimension(360, 280));
        setLocationRelativeTo(null);
        getRootPane().setDefaultButton(unlock);

        // Closing the window is a decision, not an accident to ignore: without this the latch
        // below would never count down and the launcher would hang with no window at all.
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                finished.countDown();
            }
        });
    }

    /**
     * Blocks until the player unlocks, gives up, or asks to reset.
     *
     * <p><b>Must not be called on the EDT</b> - it waits on a latch that only the EDT can release.
     * The window itself is built and shown on the EDT internally.
     *
     * @param icon the cached client logo, or {@code null} - this runs before any branding fetch,
     *             so the cache (see {@code net.LogoCache}) is the only source of one available
     */
    public static Outcome showAndAwait(Verifier verifier, BufferedImage icon) {
        PinUnlockDialog[] holder = new PinUnlockDialog[1];

        try {
            // invokeAndWait, not invokeLater: it returns only once the window exists, so the latch
            // below can be awaited without first having to poll for construction to finish. Safe
            // precisely because this method is documented as never running on the EDT.
            SwingUtilities.invokeAndWait(() -> {
                holder[0] = new PinUnlockDialog(verifier, icon);
                holder[0].setVisible(true);
                holder[0].forceToFront();

                // This is the first window a PIN-protected player ever sees, so it is what the
                // bootstrap's splash should hand over to. Without this the splash stayed pinned on
                // top of the PIN prompt until its timeout expired.
                StartupSignal.signalReady();

                holder[0].pinField.requestFocusInWindow();
            });

            holder[0].finished.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Outcome.CANCELLED;
        } catch (Exception e) {
            Log.error(PinUnlockDialog.class, "Could not show the PIN window", e);
            return Outcome.CANCELLED;
        }

        return holder[0].outcome;
    }

    /**
     * Raises and focuses the window reliably, including on Windows where a plain {@code toFront()}
     * from a process that does not own the foreground is silently ignored - same workaround, and
     * same reason, as {@code LauncherApp#forceToFront}.
     */
    private void forceToFront() {
        setAlwaysOnTop(true);
        toFront();
        requestFocus();
        setAlwaysOnTop(false);
    }

    private void submit(Verifier verifier) {
        char[] pin = pinField.getPassword();
        try {
            if (!PinProtection.isValidPin(pin)) {
                showError("Le code doit contenir entre " + PinProtection.MIN_LENGTH + " et " + PinProtection.MAX_LENGTH + " chiffres.");
                return;
            }
            if (!verifier.tryUnlock(pin)) {
                showError("Code incorrect.");
                return;
            }
            outcome = Outcome.UNLOCKED;
            dispose();
        } finally {
            // The field keeps its own copy until cleared; zero ours either way so a wrong-PIN
            // retry doesn't leave a trail of them alive on the heap.
            Arrays.fill(pin, '\0');
        }
    }

    private void showError(String message) {
        errorLabel.setText(message);
        pinField.setText("");
        pinField.requestFocusInWindow();
    }

    private JPanel forgottenLink() {
        JPanel row = new JPanel();
        row.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel link = new JLabel("Code oublié ?");
        link.setFont(link.getFont().deriveFont(12f));
        link.setForeground(new java.awt.Color(0x4A9EFF));
        link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        link.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                confirmForgotten();
            }
        });
        row.add(link);
        return row;
    }

    /**
     * Spelled out rather than a bare "are you sure?": this genuinely destroys data (the session
     * has to be redone, and any encrypted modpack file is gone for good), so the player needs to
     * know that before clicking through.
     */
    private void confirmForgotten() {
        int choice = javax.swing.JOptionPane.showConfirmDialog(this,
                "Sans le code PIN, il est impossible de récupérer ta session ou tes fichiers chiffrés.\n\n"
                        + "Continuer va :\n"
                        + "  • supprimer ta session (tu devras te reconnecter)\n"
                        + "  • supprimer définitivement les fichiers chiffrés des modpacks\n"
                        + "    (ils seront retéléchargés au prochain lancement)\n"
                        + "  • désactiver le code PIN\n\n"
                        + "Continuer ?",
                "Code oublié",
                javax.swing.JOptionPane.YES_NO_OPTION,
                javax.swing.JOptionPane.WARNING_MESSAGE);

        if (choice == javax.swing.JOptionPane.YES_OPTION) {
            outcome = Outcome.FORGOTTEN;
            dispose();
        }
    }
}
