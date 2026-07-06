package mc.snakenest.launcher.ui.modpack;

import mc.snakenest.launcher.modpack.ModpackSummary;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;

/** Scrollable list of modpack cards - one entry point per modpack accessible to the player. */
public final class ModpackListPage extends JPanel {

    public ModpackListPage(ModpackListViewModel viewModel) {
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(16, 16, 16, 16));

        if (viewModel.modpacks().isEmpty()) {
            JLabel empty = new JLabel("Aucun modpack disponible pour le moment.", SwingConstants.CENTER);
            add(empty, BorderLayout.CENTER);
            return;
        }

        JPanel list = new JPanel();
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));

        for (ModpackSummary modpack : viewModel.modpacks()) {
            ModpackCardView card = new ModpackCardView(modpack, viewModel.isInstalled(modpack), () -> viewModel.select(modpack));
            card.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 84));
            list.add(card);
            list.add(javax.swing.Box.createVerticalStrut(12));
        }

        JScrollPane scrollPane = new JScrollPane(list);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);
    }
}
