package mc.snakenest.launcher.ui.account;

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

/** Small popover opened from the top bar's account button: who's logged in, and a logout action. */
public final class AccountPopover {

    private AccountPopover() {
    }

    public static void show(Component invoker, String username, String role, String email, Runnable onLogout) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(12, 16, 12, 16));

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

        JButton logoutButton = new JButton("Se deconnecter");
        logoutButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        logoutButton.addActionListener(e -> {
            popup.setVisible(false);
            onLogout.run();
        });
        JPanel logoutWrapper = new JPanel();
        logoutWrapper.setLayout(new BoxLayout(logoutWrapper, BoxLayout.Y_AXIS));
        logoutWrapper.setBorder(new EmptyBorder(0, 16, 12, 16));
        logoutWrapper.add(logoutButton);
        popup.add(logoutWrapper, java.awt.BorderLayout.SOUTH);

        popup.show(invoker, -popup.getPreferredSize().width + invoker.getWidth(), invoker.getHeight());
    }
}
