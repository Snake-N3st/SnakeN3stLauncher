package mc.snakenest.launcher.ui;

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
 * Left navigation column: Modpacks / Actualités (mutually exclusive, like
 * tabs), Settings pinned to the bottom. No external links (Twitch/Discord)
 * live here anymore - removed as clutter, see the top-level plan/CONTEXT.md.
 */
final class Sidebar extends JPanel {

    // The two main nav buttons read as too small even after an earlier size pass - bumped again.
    // The Settings (gear) icon stays as a smaller, secondary utility icon on purpose, but its
    // selection/hover background circle (SELECTION_DIAMETER) is shared by all three buttons -
    // it used to be derived from each icon's own size, making Settings' circle noticeably
    // smaller than the other two for no good reason. That sharing was previously undermined by
    // an IconButton bug (see its Javadoc) that silently shrank every circle well below this
    // constant regardless of its value - fixed now, so this is finally the real rendered size.
    // 68 (a deliberate +4 on top of the originally-intended 64) read as too big once the fix
    // above actually let it take effect; trying 60 next.
    private static final int NAV_ICON_SIZE = 34;
    private static final int SETTINGS_ICON_SIZE = 26;
    private static final int SELECTION_DIAMETER = 60;

    // Package-visible (not private): LauncherFrame's header logo sits directly above this
    // column and is meant to be centered within a zone exactly this wide, so the two visually
    // line up as one "left rail" - see LauncherFrame's logoWrapper.
    static final int WIDTH = 82;

    private final Map<NavTarget, IconButton> navButtons = new EnumMap<>(NavTarget.class);

    Sidebar(Consumer<NavTarget> onNavigate) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(new MatteBorder(0, 0, 0, 1, new Color(0, 0, 0, 40)));
        setPreferredSize(new java.awt.Dimension(WIDTH, 0));

        ButtonGroup navGroup = new ButtonGroup();
        add(Box.createVerticalStrut(12));
        addNavButton(NavTarget.MODPACKS, Icons.play(NAV_ICON_SIZE), "Modpacks", navGroup, onNavigate);
        addNavButton(NavTarget.NEWS, Icons.document(NAV_ICON_SIZE), "Actualités", navGroup, onNavigate);

        add(Box.createVerticalGlue());
        addNavButton(NavTarget.SETTINGS, Icons.gear(SETTINGS_ICON_SIZE), "Paramètres", navGroup, onNavigate);
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
        IconButton button = new IconButton(icon, tooltip, SELECTION_DIAMETER);
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
