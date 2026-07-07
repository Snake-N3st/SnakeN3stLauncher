package mc.snakenest.launcher.ui;

import mc.snakenest.launcher.ui.common.Buttons;
import mc.snakenest.launcher.ui.common.Icons;
import mc.snakenest.launcher.ui.common.RoundedImageIcon;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;

/**
 * Page title on the left, account button on the right. A page (e.g. a
 * modpack's detail view) can temporarily replace the title with a back
 * button via {@link #showBackButton}, mirroring the third mockup.
 *
 * <p>{@code leftPanel} uses {@link BorderLayout} (not a {@code FlowLayout})
 * specifically so the title/back-button can be vertically centered within
 * the bar's full height via {@link SwingConstants#CENTER} - a
 * {@code FlowLayout} row only ever takes its components' preferred height,
 * which left the title sitting near the top instead of centered whenever
 * the bar itself was taller (e.g. to match the logo next to it).
 */
final class TopBar extends JPanel {

    private static final int ACCOUNT_ICON_SIZE = 34;

    private final JPanel leftPanel = new JPanel(new BorderLayout(8, 0));
    private final JLabel titleLabel = new JLabel();
    private final JButton accountButton;
    private JButton backButton;

    TopBar(Consumer<JComponent> onOpenAccount) {
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(4, 16, 4, 16));
        leftPanel.setOpaque(false);

        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 22f));
        titleLabel.setVerticalAlignment(SwingConstants.CENTER);
        leftPanel.add(titleLabel, BorderLayout.CENTER);
        add(leftPanel, BorderLayout.WEST);

        JButton button = new JButton(Icons.userCircle(ACCOUNT_ICON_SIZE));
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
        leftPanel.add(backButton, BorderLayout.WEST);
        titleLabel.setText(title);
        revalidate();
        repaint();
    }

    /** Safe to call with {@code null} (reverts to the generic account icon). */
    void setAccountAvatar(BufferedImage image) {
        accountButton.setIcon(image != null ? new RoundedImageIcon(image, ACCOUNT_ICON_SIZE) : Icons.userCircle(ACCOUNT_ICON_SIZE));
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
