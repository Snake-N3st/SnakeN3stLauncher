package mc.snakenest.launcher.ui.account;

import mc.snakenest.launcher.ui.common.RoundedImageIcon;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import java.awt.Component;
import java.awt.Font;
import java.awt.image.BufferedImage;

/**
 * Small popover opened from the top bar's account button: who's logged in,
 * a "Gérer le profil" action (opens the account's profile page on the site
 * in the default browser), and a logout action. Purely a view - the
 * username/role/email/avatar it shows are already-fetched data handed in by
 * the caller ({@code LauncherApp}), never fetched here; this popover must
 * never trigger a network call just from being opened (see
 * {@code ui/README.md}).
 */
public final class AccountPopover {

    private static final int AVATAR_SIZE = 64;

    private AccountPopover() {
    }

    public static void show(Component invoker, String username, String role, String email, BufferedImage avatar,
                             Runnable onManageProfile, Runnable onLogout) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(12, 16, 12, 16));

        if (avatar != null) {
            JLabel avatarLabel = new JLabel(new RoundedImageIcon(avatar, AVATAR_SIZE));
            avatarLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            avatarLabel.setBorder(new EmptyBorder(0, 0, 8, 0));
            panel.add(avatarLabel);
        }

        JLabel usernameLabel = new JLabel(username);
        usernameLabel.setFont(usernameLabel.getFont().deriveFont(Font.BOLD, 15f));
        usernameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(usernameLabel);

        if (role != null && !role.isBlank()) {
            JLabel roleLabel = new JLabel(role);
            roleLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
            roleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(roleLabel);
        }

        if (email != null && !email.isBlank()) {
            JLabel emailLabel = new JLabel(email);
            emailLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
            emailLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(emailLabel);
        }

        panel.add(javax.swing.Box.createVerticalStrut(8));
        JSeparator separator = new JSeparator();
        separator.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(separator);
        panel.add(javax.swing.Box.createVerticalStrut(8));

        JPopupMenu popup = new JPopupMenu();
        popup.setLayout(new java.awt.BorderLayout());
        popup.add(panel, java.awt.BorderLayout.NORTH);

        JButton manageButton = new JButton("Gérer le profil");
        manageButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        manageButton.addActionListener(e -> {
            popup.setVisible(false);
            onManageProfile.run();
        });

        JButton logoutButton = new JButton("Se déconnecter");
        logoutButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        logoutButton.addActionListener(e -> {
            popup.setVisible(false);
            onLogout.run();
        });

        // Each JButton otherwise sizes itself to its own text ("Gérer le profil" vs "Se
        // déconnecter" aren't the same length) - match them to the wider of the two so the pair
        // reads as a deliberate, aligned button group instead of two mismatched widths.
        int buttonWidth = Math.max(manageButton.getPreferredSize().width, logoutButton.getPreferredSize().width);
        matchWidth(manageButton, buttonWidth);
        matchWidth(logoutButton, buttonWidth);

        JPanel actionsWrapper = new JPanel();
        actionsWrapper.setLayout(new BoxLayout(actionsWrapper, BoxLayout.Y_AXIS));
        actionsWrapper.setBorder(new EmptyBorder(0, 16, 12, 16));
        actionsWrapper.add(manageButton);
        actionsWrapper.add(javax.swing.Box.createVerticalStrut(6));
        actionsWrapper.add(logoutButton);
        popup.add(actionsWrapper, java.awt.BorderLayout.SOUTH);

        popup.show(invoker, -popup.getPreferredSize().width + invoker.getWidth(), invoker.getHeight());
    }

    private static void matchWidth(JButton button, int width) {
        java.awt.Dimension size = new java.awt.Dimension(width, button.getPreferredSize().height);
        button.setPreferredSize(size);
        button.setMaximumSize(size);
    }
}
