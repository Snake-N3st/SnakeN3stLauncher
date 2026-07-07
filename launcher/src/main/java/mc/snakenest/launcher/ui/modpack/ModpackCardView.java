package mc.snakenest.launcher.ui.modpack;

import mc.snakenest.launcher.modpack.ModpackSummary;
import mc.snakenest.launcher.ui.common.AvatarPanel;
import mc.snakenest.launcher.ui.common.Icons;
import mc.snakenest.launcher.util.HumanSize;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * One row of {@link ModpackListPage}: logo, name, size, and a quick-action
 * icon. Clicking the row itself opens the detail page ({@code onOpen});
 * clicking the icon performs the action directly - the two used to be the
 * same callback, which meant the icon didn't actually do anything a click
 * anywhere else on the row didn't already do.
 *
 * <p>The icon mirrors {@code ModpackDetailPage}'s three-state button
 * (play/download/"update" at rest depending on install state, cancel while
 * a quick-installed sync/install is in flight, stop while a quick-launched
 * game is running) so a quick action
 * started from the list reads the same way whether or not its detail page
 * happens to be open - {@code LauncherApp} pushes state changes here via
 * {@code ModpackListViewModel#setCardBusy}/{@code #setCardRunning} as they
 * happen, instead of the icon being fixed at whatever it was when the list
 * was last (re)built.
 */
final class ModpackCardView extends JPanel {

    private static final int ARC = 18;
    private static final int ACTION_ICON_SIZE = 28;
    private static final int ACTION_BUTTON_SIZE = 44;

    private enum ButtonState { IDLE, BUSY, RUNNING }

    // Paints its own circular background (rollover/pressed only - unlike ui.common.IconButton,
    // there's no "selected" state, this is a fire-once action button, not a toggle), same
    // reasoning as the sidebar's IconButton: a round nav/action icon reads better as an actual
    // circle than FlatLaf's default "toolBarButton" rounded-square hover shape.
    private final JButton actionButton = new JButton() {
        @Override
        protected void paintComponent(Graphics g) {
            Color background = (getModel().isRollover() || getModel().isPressed())
                    ? UIManager.getColor("Component.borderColor")
                    : null;
            if (background != null) {
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    int d = Math.min(getWidth(), getHeight());
                    int x = (getWidth() - d) / 2;
                    int y = (getHeight() - d) / 2;
                    g2.setColor(background);
                    g2.fill(new Ellipse2D.Float(x, y, d, d));
                } finally {
                    g2.dispose();
                }
            }
            super.paintComponent(g);
        }
    };
    private final Runnable onQuickAction;
    private final Runnable onCancel;
    private final Runnable onStop;
    private boolean installed;
    private boolean updateAvailable;
    private ButtonState buttonState = ButtonState.IDLE;

    ModpackCardView(ModpackSummary modpack, boolean installed, boolean updateAvailable, BufferedImage logo, Runnable onOpen,
                     Runnable onQuickAction, Runnable onCancel, Runnable onStop) {
        this.installed = installed;
        this.updateAvailable = updateAvailable;
        this.onQuickAction = onQuickAction;
        this.onCancel = onCancel;
        this.onStop = onStop;
        setLayout(new BorderLayout(16, 0));
        setOpaque(false);
        setBorder(new RoundedBorder(UIManager.getColor("Component.borderColor")));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // Wrapped (rather than added to BorderLayout.WEST directly) purely for the left
        // margin - AvatarPanel itself already centers vertically within whatever height
        // BorderLayout.WEST stretches it to.
        AvatarPanel avatar = new AvatarPanel(modpack.name(), 56);
        avatar.setImage(logo);
        JPanel avatarWrapper = new JPanel(new BorderLayout());
        avatarWrapper.setOpaque(false);
        avatarWrapper.setBorder(new EmptyBorder(0, 10, 0, 0));
        avatarWrapper.add(avatar, BorderLayout.CENTER);
        add(avatarWrapper, BorderLayout.WEST);

        // BorderLayout.CENTER stretches this panel to the full card height regardless of its
        // own preferred size - without the glue on both sides, BoxLayout.Y_AXIS pins the
        // labels to the top of that extra space instead of centering them.
        JPanel textPanel = new JPanel();
        textPanel.setOpaque(false);
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setBorder(new EmptyBorder(10, 0, 10, 0));

        JLabel nameLabel = new JLabel(modpack.name());
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 17f));

        JLabel sizeLabel = new JLabel(HumanSize.format(modpack.totalSize()));
        sizeLabel.setForeground(UIManager.getColor("Label.disabledForeground"));

        textPanel.add(Box.createVerticalGlue());
        textPanel.add(nameLabel);
        textPanel.add(sizeLabel);
        textPanel.add(Box.createVerticalGlue());

        add(textPanel, BorderLayout.CENTER);

        actionButton.setFocusPainted(false);
        actionButton.setBorderPainted(false);
        actionButton.setContentAreaFilled(false);
        actionButton.setOpaque(false);
        // Explicit fixed size, same reasoning as ui.common.IconButton's Javadoc - and this one
        // additionally needs GridBagLayout (not BorderLayout) below, since BorderLayout.CENTER
        // stretches its child to fill all remaining space regardless of that child's own max
        // size, which would silently defeat a fixed circle diameter here too.
        java.awt.Dimension actionButtonSize = new java.awt.Dimension(ACTION_BUTTON_SIZE, ACTION_BUTTON_SIZE);
        actionButton.setPreferredSize(actionButtonSize);
        actionButton.setMinimumSize(actionButtonSize);
        actionButton.setMaximumSize(actionButtonSize);
        applyActionButton();

        JPanel actionWrapper = new JPanel(new java.awt.GridBagLayout());
        actionWrapper.setOpaque(false);
        actionWrapper.setBorder(new EmptyBorder(0, 0, 0, 12));
        actionWrapper.add(actionButton);
        add(actionWrapper, BorderLayout.EAST);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                onOpen.run();
            }
        });
    }

    /**
     * Icon/tooltip/action for the current {@link #buttonState} (and, at rest,
     * {@link #installed}/{@link #updateAvailable} - same three-way logic as
     * {@code ModpackDetailPage#applyButtonLabel}).
     */
    private void applyActionButton() {
        for (ActionListener listener : actionButton.getActionListeners()) {
            actionButton.removeActionListener(listener);
        }
        switch (buttonState) {
            case IDLE -> {
                if (!installed) {
                    actionButton.setIcon(Icons.download(ACTION_ICON_SIZE));
                    actionButton.setToolTipText("Télécharger");
                } else if (updateAvailable) {
                    actionButton.setIcon(Icons.download(ACTION_ICON_SIZE));
                    actionButton.setToolTipText("Mettre à jour");
                } else {
                    actionButton.setIcon(Icons.play(ACTION_ICON_SIZE));
                    actionButton.setToolTipText("Démarrer");
                }
                actionButton.addActionListener(e -> onQuickAction.run());
            }
            case BUSY -> {
                actionButton.setIcon(Icons.cancel(ACTION_ICON_SIZE));
                actionButton.setToolTipText("Annuler");
                actionButton.addActionListener(e -> onCancel.run());
            }
            case RUNNING -> {
                actionButton.setIcon(Icons.stop(ACTION_ICON_SIZE));
                actionButton.setToolTipText("Arrêter");
                actionButton.addActionListener(e -> onStop.run());
            }
        }
    }

    /** Flips the icon from download to play (or back) once install/uninstall finishes. Safe from any thread. */
    void setInstalled(boolean installed) {
        SwingUtilities.invokeLater(() -> {
            this.installed = installed;
            if (buttonState == ButtonState.IDLE) {
                applyActionButton();
            }
        });
    }

    /** Flips the icon between play and "Mettre à jour" once an install/update finishes. Safe from any thread. */
    void setUpdateAvailable(boolean updateAvailable) {
        SwingUtilities.invokeLater(() -> {
            this.updateAvailable = updateAvailable;
            if (buttonState == ButtonState.IDLE) {
                applyActionButton();
            }
        });
    }

    /** While busy, the icon reads "Annuler" instead of its resting play/download icon. Safe from any thread. */
    void setBusy(boolean busy) {
        SwingUtilities.invokeLater(() -> {
            if (busy) {
                buttonState = ButtonState.BUSY;
            } else if (buttonState == ButtonState.BUSY) {
                buttonState = ButtonState.IDLE;
            }
            applyActionButton();
        });
    }

    /** While the game is running, the icon reads "Arrêter" instead of its resting play/download icon. Safe from any thread. */
    void setRunning(boolean running) {
        SwingUtilities.invokeLater(() -> {
            if (running) {
                buttonState = ButtonState.RUNNING;
            } else if (buttonState == ButtonState.RUNNING) {
                buttonState = ButtonState.IDLE;
            }
            applyActionButton();
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        // Not opaque (see constructor) - the container's own background is filled here as a
        // rounded rect instead, otherwise the square panel background would poke out past
        // the rounded border's corners.
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(UIManager.getColor("Panel.background"));
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, ARC, ARC));
        } finally {
            g2.dispose();
        }
        super.paintComponent(g);
    }

    /** Anti-aliased rounded outline - {@code LineBorder}'s built-in "rounded" flag looks too subtle to read as a card. */
    private static final class RoundedBorder extends AbstractBorder {

        private final Color color;

        RoundedBorder(Color color) {
            this.color = color;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.draw(new RoundRectangle2D.Float(x, y, width - 1, height - 1, ARC, ARC));
            } finally {
                g2.dispose();
            }
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(4, 4, 4, 4);
        }
    }
}
