package mc.snakenest.launcher.ui;

import mc.snakenest.launcher.auth.DeviceAuthListener;
import mc.snakenest.launcher.auth.DeviceAuthService;
import mc.snakenest.launcher.auth.DeviceAuthState;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.util.function.LongConsumer;

/**
 * The very first screen: nothing is shown of the real app until the
 * device-flow login succeeds (see {@code auth.DeviceAuthService}). Not
 * part of {@link LauncherFrame} - there's no session yet at this point, so
 * there's nothing for the sidebar/pages to show.
 */
public final class LoginFrame extends JFrame {

    private final DeviceAuthService authService;
    private final JLabel statusLabel = new JLabel(" ", SwingConstants.CENTER);
    private final JButton connectButton = new JButton("Se connecter");
    private final JButton cancelButton = new JButton("Annuler");

    public LoginFrame(DeviceAuthService authService, LongConsumer onSuccess) {
        super("SnakeN3st Launcher - Connexion");
        this.authService = authService;

        setLayout(new BorderLayout(0, 16));
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(420, 220);
        setMinimumSize(new Dimension(360, 200));
        setLocationRelativeTo(null);

        JLabel title = new JLabel("SnakeN3st Launcher", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));

        statusLabel.setFont(statusLabel.getFont().deriveFont(13f));

        connectButton.addActionListener(e -> connect(onSuccess));
        cancelButton.setVisible(false);
        cancelButton.addActionListener(e -> authService.cancel());

        JPanel buttons = new JPanel();
        buttons.add(connectButton);
        buttons.add(cancelButton);

        JPanel center = new JPanel(new BorderLayout(0, 12));
        center.add(title, BorderLayout.NORTH);
        center.add(statusLabel, BorderLayout.CENTER);
        center.add(buttons, BorderLayout.SOUTH);

        add(center, BorderLayout.CENTER);
    }

    private void connect(LongConsumer onSuccess) {
        connectButton.setEnabled(false);
        cancelButton.setVisible(true);

        authService.start(new DeviceAuthListener() {
            @Override
            public void onStateChanged(DeviceAuthState state) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText(describe(state));
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
                SwingUtilities.invokeLater(() -> statusLabel.setText(reasonMessage));
            }
        });
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
