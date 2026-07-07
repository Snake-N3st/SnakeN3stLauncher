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
import java.awt.event.ActionListener;
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

    private static final int ACCOUNT_ICON_SIZE = 40;
    private static final int ACCOUNT_BUTTON_SIZE = 46;
    private static final int REFRESH_ICON_SIZE = 22;

    private final JPanel leftPanel = new JPanel(new BorderLayout(8, 0));
    private final JLabel titleLabel = new JLabel();
    private final JButton accountButton;
    private final JButton refreshButton;
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
        // Explicit fixed size, same reasoning as ui.common.IconButton's Javadoc: leaving this to
        // the look-and-feel's own icon-derived sizing risks an inconsistent/off-center result
        // once the icon itself is swapped for a real (possibly not pixel-identical) avatar image.
        java.awt.Dimension buttonSize = new java.awt.Dimension(ACCOUNT_BUTTON_SIZE, ACCOUNT_BUTTON_SIZE);
        button.setPreferredSize(buttonSize);
        button.setMinimumSize(buttonSize);
        button.setMaximumSize(buttonSize);
        accountButton = button;

        // Hidden by default (see setOnRefresh) - only whichever page currently has something
        // meaningful to refresh (modpack list/detail, news list) makes it visible.
        refreshButton = Buttons.flatIcon(Icons.refresh(REFRESH_ICON_SIZE), "Actualiser", () -> {
        });
        refreshButton.setVisible(false);

        // GridBagLayout, not FlowLayout: FlowLayout only ever centers a row within its own
        // natural height, then pins that row to the TOP of whatever extra height the container
        // is actually given (confirmed by dumping real bounds: accountButton sat flush at
        // rightPanel's y=0 with 100% of the slack pushed below it, not split top/bottom) -
        // GridBagLayout's default constraints (anchor=CENTER) center properly regardless of any
        // size mismatch between the content and the container, the same "always exactly
        // centered" behavior leftPanel/titleLabel already gets from BorderLayout+setVerticalAlignment.
        JPanel rightPanel = new JPanel(new java.awt.GridBagLayout());
        rightPanel.setOpaque(false);
        java.awt.GridBagConstraints gbc = new java.awt.GridBagConstraints();
        gbc.gridy = 0;
        gbc.insets = new java.awt.Insets(0, 0, 0, 4);
        gbc.gridx = 0;
        rightPanel.add(refreshButton, gbc);
        gbc.gridx = 1;
        gbc.insets = new java.awt.Insets(0, 0, 0, 0);
        rightPanel.add(accountButton, gbc);
        add(rightPanel, BorderLayout.EAST);
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

    /** {@code null} hides the button - whatever page is currently shown has nothing to refresh. */
    void setOnRefresh(Runnable onRefresh) {
        for (ActionListener listener : refreshButton.getActionListeners()) {
            refreshButton.removeActionListener(listener);
        }
        if (onRefresh == null) {
            refreshButton.setVisible(false);
            return;
        }
        refreshButton.addActionListener(e -> onRefresh.run());
        refreshButton.setVisible(true);
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
