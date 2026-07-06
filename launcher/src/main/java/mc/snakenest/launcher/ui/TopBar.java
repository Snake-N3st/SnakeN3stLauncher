package mc.snakenest.launcher.ui;

import mc.snakenest.launcher.ui.common.Buttons;
import mc.snakenest.launcher.ui.common.Icons;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.function.Consumer;

/**
 * Page title on the left, account button on the right. A page (e.g. a
 * modpack's detail view) can temporarily replace the title with a back
 * button via {@link #showBackButton}, mirroring the third mockup.
 */
final class TopBar extends JPanel {

    private final JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    private final JLabel titleLabel = new JLabel();
    private final JButton accountButton;
    private JButton backButton;

    TopBar(Consumer<JComponent> onOpenAccount) {
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(4, 16, 4, 16));
        leftPanel.setOpaque(false);

        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 22f));
        leftPanel.add(titleLabel);
        add(leftPanel, BorderLayout.WEST);

        JButton button = new JButton(Icons.userCircle(26));
        button.setToolTipText("Compte");
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(true);
        button.setOpaque(false);
        button.putClientProperty("JButton.buttonType", "toolBarButton");
        button.addActionListener(e -> onOpenAccount.accept(button));
        accountButton = button;
        add(accountButton, BorderLayout.EAST);
    }

    void setTitle(String title) {
        titleLabel.setText(title);
        clearBackButton();
    }

    void showBackButton(String title, Runnable onBack) {
        clearBackButton();
        backButton = Buttons.flatIcon(Icons.backArrow(18), "Retour", onBack);
        leftPanel.add(backButton, 0);
        titleLabel.setText(title);
        revalidate();
        repaint();
    }

    private void clearBackButton() {
        if (backButton != null) {
            leftPanel.remove(backButton);
            backButton = null;
            revalidate();
            repaint();
        }
    }

    JButton accountButton() {
        return accountButton;
    }
}
