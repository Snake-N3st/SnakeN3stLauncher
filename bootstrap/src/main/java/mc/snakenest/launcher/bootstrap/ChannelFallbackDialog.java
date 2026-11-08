package mc.snakenest.launcher.bootstrap;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;

/**
 * Shown when the launcher's configured distribution channel is restricted and this installation
 * can't (yet) prove eligibility - see {@link BootstrapMain}'s restricted-channel fallback flow.
 * Same headless-safe, blocking-{@code JOptionPane} style as {@link BootstrapSplash}'s own error
 * dialogs.
 */
final class ChannelFallbackDialog {

    enum Choice {
        /** Retry the fetch with no channel param - the server resolves that to its own default. */
        USE_DEFAULT_CHANNEL,
        /** Run {@link DeviceAuthClient}, then retry the original restricted-channel fetch with the new key. */
        RECONNECT,
        /** Skip the fetch entirely and launch whatever jar last actually started successfully. */
        LAUNCH_LAST_JAR,
        /** Headless environment, or the player closed the dialog - {@link BootstrapMain} treats this as a fatal error. */
        NONE
    }

    private ChannelFallbackDialog() {
    }

    static Choice show(String channelName) {
        if (GraphicsEnvironment.isHeadless()) {
            return Choice.NONE;
        }
        String message = "Le canal de distribution \"" + channelName + "\" est restreint et cette installation "
                + "n'a pas (ou plus) accès. Que veux-tu faire ?";
        Object[] options = {"Utiliser le canal par défaut", "Se reconnecter", "Lancer le dernier jar exécuté avec succès", "Annuler"};
        int[] choice = {JOptionPane.CLOSED_OPTION};
        try {
            SwingUtilities.invokeAndWait(() -> choice[0] = JOptionPane.showOptionDialog(
                    null, message, "SnakeN3st Launcher",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
                    null, options, options[0]));
        } catch (Exception e) {
            return Choice.NONE;
        }
        return switch (choice[0]) {
            case 0 -> Choice.USE_DEFAULT_CHANNEL;
            case 1 -> Choice.RECONNECT;
            case 2 -> Choice.LAUNCH_LAST_JAR;
            default -> Choice.NONE;
        };
    }

    /**
     * A plain, non-blocking status window with a Cancel button, shown while {@link
     * DeviceAuthClient#run} polls in the background - {@code isCancelled()} is what lets the
     * player's click actually interrupt that polling loop from a different thread, and {@code
     * close()} must be called once the flow finishes either way (success, failure, or timeout) so
     * the window doesn't linger.
     */
    interface ReconnectingWindow {
        boolean isCancelled();

        void close();
    }

    static ReconnectingWindow showReconnecting() {
        if (GraphicsEnvironment.isHeadless()) {
            return new ReconnectingWindow() {
                @Override
                public boolean isCancelled() {
                    return false;
                }

                @Override
                public void close() {
                }
            };
        }
        var cancelled = new java.util.concurrent.atomic.AtomicBoolean(false);
        var frame = new javax.swing.JFrame("SnakeN3st Launcher");
        try {
            SwingUtilities.invokeAndWait(() -> {
                frame.setUndecorated(true);
                frame.setAlwaysOnTop(true);
                frame.setDefaultCloseOperation(javax.swing.JFrame.DO_NOTHING_ON_CLOSE);

                var content = new javax.swing.JPanel(new java.awt.BorderLayout(0, 12));
                content.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                        javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0, 60)),
                        javax.swing.BorderFactory.createEmptyBorder(24, 32, 24, 32)));
                content.add(new javax.swing.JLabel("En attente de la confirmation dans le navigateur...",
                        javax.swing.SwingConstants.CENTER), java.awt.BorderLayout.CENTER);

                var cancelButton = new javax.swing.JButton("Annuler");
                cancelButton.addActionListener(e -> cancelled.set(true));
                var buttonRow = new javax.swing.JPanel();
                buttonRow.add(cancelButton);
                content.add(buttonRow, java.awt.BorderLayout.SOUTH);

                frame.setContentPane(content);
                frame.setSize(380, 140);
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            });
        } catch (Exception ignored) {
            // Best-effort only - the reconnect flow still works without this window, just less visibly.
        }
        return new ReconnectingWindow() {
            @Override
            public boolean isCancelled() {
                return cancelled.get();
            }

            @Override
            public void close() {
                SwingUtilities.invokeLater(frame::dispose);
            }
        };
    }
}
