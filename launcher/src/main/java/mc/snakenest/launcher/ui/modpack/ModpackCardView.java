package mc.snakenest.launcher.ui.modpack;

import mc.snakenest.launcher.modpack.ModpackSummary;
import mc.snakenest.launcher.ui.common.AvatarPanel;
import mc.snakenest.launcher.ui.common.Buttons;
import mc.snakenest.launcher.ui.common.Icons;
import mc.snakenest.launcher.util.HumanSize;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/** One row of {@link ModpackListPage}: logo, name, size, and a play/download action. */
final class ModpackCardView extends JPanel {

    ModpackCardView(ModpackSummary modpack, boolean installed, Runnable onOpen) {
        setLayout(new BorderLayout(16, 0));
        setBorder(new CompoundBorder(new EmptyBorder(4, 4, 4, 4), BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor"), 1, true)));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        add(new AvatarPanel(modpack.name(), 56), BorderLayout.WEST);

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
        actionWrapper.add(Buttons.flatIcon(actionIcon, actionTooltip, onOpen), BorderLayout.CENTER);
        add(actionWrapper, BorderLayout.EAST);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                onOpen.run();
            }
        });
    }
}
