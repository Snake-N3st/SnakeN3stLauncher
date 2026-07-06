package mc.snakenest.launcher.ui;

import mc.snakenest.launcher.ui.common.Buttons;
import mc.snakenest.launcher.ui.common.IconButton;
import mc.snakenest.launcher.ui.common.Icons;

import javax.swing.AbstractButton;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.border.MatteBorder;
import java.awt.Color;
import java.awt.Component;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Left navigation column: Modpacks / Actualites (mutually exclusive, like
 * tabs), Twitch / Discord (external links, not pages), Settings pinned to
 * the bottom.
 */
final class Sidebar extends JPanel {

    private static final int ICON_SIZE = 22;
    private static final int WIDTH = 64;

    private final Map<NavTarget, IconButton> navButtons = new EnumMap<>(NavTarget.class);

    Sidebar(Consumer<NavTarget> onNavigate, Runnable onOpenTwitch, Runnable onOpenDiscord) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(new MatteBorder(0, 0, 0, 1, new Color(0, 0, 0, 40)));
        setPreferredSize(new java.awt.Dimension(WIDTH, 0));

        ButtonGroup navGroup = new ButtonGroup();
        addNavButton(NavTarget.MODPACKS, Icons.play(ICON_SIZE), "Modpacks", navGroup, onNavigate);
        addNavButton(NavTarget.NEWS, Icons.document(ICON_SIZE), "Actualites", navGroup, onNavigate);

        add(Box.createVerticalStrut(24));
        addCentered(Buttons.flatIcon(Icons.twitch(ICON_SIZE), "Twitch", onOpenTwitch));
        addCentered(Buttons.flatIcon(Icons.discord(ICON_SIZE), "Discord", onOpenDiscord));

        add(Box.createVerticalGlue());
        addNavButton(NavTarget.SETTINGS, Icons.gear(ICON_SIZE), "Parametres", navGroup, onNavigate);
        add(Box.createVerticalStrut(12));
    }

    /** Reflects an externally-triggered navigation (e.g. clicking a card) in the sidebar's own selection. */
    void select(NavTarget target) {
        IconButton button = navButtons.get(target);
        if (button != null) {
            button.setSelected(true);
        }
    }

    private void addNavButton(NavTarget target, javax.swing.Icon icon, String tooltip, ButtonGroup group, Consumer<NavTarget> onNavigate) {
        IconButton button = new IconButton(icon, tooltip);
        button.addActionListener(e -> onNavigate.accept(target));
        group.add(button);
        navButtons.put(target, button);
        addCentered(button);
        add(Box.createVerticalStrut(4));
    }

    private void addCentered(AbstractButton button) {
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        add(button);
    }
}
