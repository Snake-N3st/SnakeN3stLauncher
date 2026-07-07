package mc.snakenest.launcher.ui;

import mc.snakenest.launcher.auth.DeviceAuthListener;
import mc.snakenest.launcher.auth.DeviceAuthService;
import mc.snakenest.launcher.auth.DeviceAuthState;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.util.function.LongConsumer;

/**
 * The very first screen: nothing is shown of the real app until the
 * device-flow login succeeds (see {@code auth.DeviceAuthService}). Not
 * part of {@link LauncherFrame} - there's no session yet at this point, so
 * there's nothing for the sidebar/pages to show.
 *
 * <p>Branding (title/logo) is generic by default, or already the real
 * client's name/logo if {@link #setClientInfo} is called before
 * {@link #setVisible} - the composition root ({@code LauncherApp}) fetches
 * that over the network <em>before</em> constructing this frame at all, so
 * the window never flashes a placeholder then the real branding.
 */
public final class LoginFrame extends JFrame {

    private final DeviceAuthService authService;
    private final LogoPanel logoPanel = new LogoPanel(88);
    private final JLabel titleLabel = new JLabel("SnakeN3st Launcher", SwingConstants.CENTER);
    private final JLabel welcomeLabel = new JLabel("<html><center>Content de te revoir ! Connecte-toi pour retrouver tes modpacks.</center></html>", SwingConstants.CENTER);
    private final JLabel statusLabel = new JLabel(" ", SwingConstants.CENTER);
    private final JProgressBar progressBar = new JProgressBar();
    private final JButton connectButton = new JButton("Se connecter");
    private final JButton cancelButton = new JButton("Annuler");

    public LoginFrame(DeviceAuthService authService, LongConsumer onSuccess) {
        super("SnakeN3st Launcher - Connexion");
        this.authService = authService;

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(440, 320);
        setMinimumSize(new Dimension(380, 300));
        setLocationRelativeTo(null);

        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 20f));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        welcomeLabel.setFont(welcomeLabel.getFont().deriveFont(13f));
        welcomeLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        welcomeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        statusLabel.setFont(statusLabel.getFont().deriveFont(13f));
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);
        progressBar.setMaximumSize(new Dimension(220, 6));
        progressBar.setAlignmentX(Component.CENTER_ALIGNMENT);

        connectButton.putClientProperty("JButton.buttonType", "roundRect");
        connectButton.addActionListener(e -> connect(onSuccess));
        cancelButton.setVisible(false);
        cancelButton.addActionListener(e -> authService.cancel());

        JPanel buttons = new JPanel();
        buttons.add(connectButton);
        buttons.add(cancelButton);
        buttons.setAlignmentX(Component.CENTER_ALIGNMENT);

        logoPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setBorder(new EmptyBorder(32, 24, 24, 24));
        center.add(logoPanel);
        center.add(Box.createVerticalStrut(16));
        center.add(titleLabel);
        center.add(Box.createVerticalStrut(8));
        center.add(welcomeLabel);
        center.add(Box.createVerticalStrut(20));
        center.add(statusLabel);
        center.add(Box.createVerticalStrut(8));
        center.add(progressBar);
        center.add(Box.createVerticalGlue());
        center.add(buttons);

        setLayout(new BorderLayout());
        add(center, BorderLayout.CENTER);
    }

    /**
     * Fills in the real client name/logo. Must be called from the EDT (like
     * any other Swing mutation here) - by the time {@code LauncherApp} calls
     * this, the network fetch behind it has already completed off the EDT,
     * so there's no need for this method to hop threads itself. Safe to
     * skip entirely (keeps the generic title/placeholder logo).
     */
    public void setClientInfo(String name, BufferedImage logo) {
        if (name != null && !name.isBlank()) {
            titleLabel.setText(name);
            setTitle(name + " - Connexion");
        }
        logoPanel.setImage(logo);
    }

    private void connect(LongConsumer onSuccess) {
        connectButton.setEnabled(false);
        cancelButton.setVisible(true);

        authService.start(new DeviceAuthListener() {
            @Override
            public void onStateChanged(DeviceAuthState state) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setForeground(state == DeviceAuthState.FAILED ? dangerColor() : UIManager.getColor("Label.foreground"));
                    statusLabel.setText(describe(state));
                    progressBar.setVisible(isBusy(state));
                    if (state == DeviceAuthState.FAILED || state == DeviceAuthState.CANCELLED) {
                        connectButton.setEnabled(true);
                        cancelButton.setVisible(false);
                    }
                });
            }

            @Override
            public void onBrowserOpenFailed(String loginUrl) {
                SwingUtilities.invokeLater(() -> statusLabel.setText("<html><center>Ouvre cette page dans ton navigateur :<br>" + loginUrl + "</center></html>"));
            }

            @Override
            public void onSucceeded(long playerId) {
                SwingUtilities.invokeLater(() -> {
                    dispose();
                    authService.shutdown();
                    onSuccess.accept(playerId);
                });
            }

            @Override
            public void onFailed(String reasonMessage) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setForeground(dangerColor());
                    statusLabel.setText(reasonMessage);
                });
            }
        });
    }

    private boolean isBusy(DeviceAuthState state) {
        return state == DeviceAuthState.REQUESTING_CHALLENGE
                || state == DeviceAuthState.AWAITING_USER_CONFIRMATION
                || state == DeviceAuthState.POLLING;
    }

    private Color dangerColor() {
        Color color = UIManager.getColor("Component.error.focusedBorderColor");
        return color != null ? color : Color.RED.darker();
    }

    private String describe(DeviceAuthState state) {
        return switch (state) {
            case IDLE -> " ";
            case REQUESTING_CHALLENGE -> "Demande de connexion en cours...";
            case AWAITING_USER_CONFIRMATION -> "Confirme la connexion dans ton navigateur...";
            case POLLING -> "En attente de confirmation...";
            case SUCCEEDED -> "Connecte !";
            case FAILED -> "Echec de la connexion.";
            case CANCELLED -> "Connexion annulee.";
        };
    }
}
