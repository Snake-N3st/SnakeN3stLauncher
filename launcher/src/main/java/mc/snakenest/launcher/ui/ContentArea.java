package mc.snakenest.launcher.ui;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.CardLayout;
import java.util.EnumMap;
import java.util.Map;

/** Thin {@link CardLayout} host switching between the pages reachable from the sidebar. */
final class ContentArea extends JPanel {

    private final CardLayout cardLayout = new CardLayout();
    private final Map<NavTarget, String> cardNames = new EnumMap<>(NavTarget.class);

    ContentArea() {
        setLayout(cardLayout);
    }

    void addPage(NavTarget target, JComponent page) {
        String name = target.name();
        cardNames.put(target, name);
        add(page, name);
    }

    void show(NavTarget target) {
        String name = cardNames.get(target);
        if (name != null) {
            cardLayout.show(this, name);
        }
    }
}
