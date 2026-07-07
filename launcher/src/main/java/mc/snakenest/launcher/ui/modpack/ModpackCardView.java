package mc.snakenest.launcher.ui.modpack;

import mc.snakenest.launcher.modpack.ModpackSummary;
import mc.snakenest.launcher.ui.common.AvatarPanel;
import mc.snakenest.launcher.ui.common.Buttons;
import mc.snakenest.launcher.ui.common.Icons;
import mc.snakenest.launcher.util.HumanSize;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * One row of {@link ModpackListPage}: logo, name, size, and a play/download
 * quick-action icon. Clicking the row itself opens the detail page ({@code
 * onOpen}); clicking the icon performs the action directly ({@code
 * onQuickAction}) - the two used to be the same callback, which meant the
 * icon didn't actually do anything a click anywhere else on the row didn't
 * already do.
 */
final class ModpackCardView extends JPanel {

    private static final int ARC = 18;

    ModpackCardView(ModpackSummary modpack, boolean installed, BufferedImage logo, Runnable onOpen, Runnable onQuickAction) {
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

        JPanel textPanel = new JPanel();
        textPanel.setOpaque(false);
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setBorder(new EmptyBorder(10, 0, 10, 0));

        JLabel nameLabel = new JLabel(modpack.name());
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 17f));
        textPanel.add(nameLabel);

        JLabel sizeLabel = new JLabel(HumanSize.format(modpack.totalSize()));
        sizeLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        textPanel.add(sizeLabel);

        add(textPanel, BorderLayout.CENTER);

        var actionIcon = installed ? Icons.play(28) : Icons.download(28);
        var actionTooltip = installed ? "Demarrer" : "Telecharger";
        JPanel actionWrapper = new JPanel(new BorderLayout());
        actionWrapper.setOpaque(false);
        actionWrapper.setBorder(new EmptyBorder(0, 0, 0, 12));
        actionWrapper.add(Buttons.flatIcon(actionIcon, actionTooltip, onQuickAction), BorderLayout.CENTER);
        add(actionWrapper, BorderLayout.EAST);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                onOpen.run();
            }
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
