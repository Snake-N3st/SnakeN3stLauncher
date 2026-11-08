package mc.snakenest.launcher.ui.modpack;

import mc.snakenest.launcher.modrinth.ModrinthVersion;
import mc.snakenest.launcher.ui.common.Borders;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagLayout;

/** One row of {@link ModManagerDialog}'s dedicated version-selection page. */
final class ModVersionRowView extends JPanel {

    ModVersionRowView(ModrinthVersion version, boolean compatible, boolean installed, Runnable onAction) {
        setLayout(new BorderLayout(12, 0));
        setOpaque(false);
        setBorder(Borders.card());

        Color mutedColor = UIManager.getColor("Label.disabledForeground");

        JPanel textPanel = new JPanel();
        textPanel.setOpaque(false);
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setBorder(new EmptyBorder(10, 14, 10, 0));

        String name = version.name() == null || version.name().isBlank() ? version.versionNumber() : version.name();
        JLabel nameLabel = new JLabel(name);
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 14f));

        // Same badge as ModCardView's search results - the button label alone (Installer vs.
        // Retirer) isn't an obvious enough signal for which exact version is the one currently
        // on disk when several versions are listed one after another.
        JPanel nameRow = new JPanel();
        nameRow.setOpaque(false);
        nameRow.setLayout(new BoxLayout(nameRow, BoxLayout.X_AXIS));
        nameRow.add(nameLabel);
        if (installed) {
            nameRow.add(Box.createHorizontalStrut(8));
            JLabel installedBadge = new JLabel("Installée");
            installedBadge.setFont(installedBadge.getFont().deriveFont(Font.BOLD, 11f));
            installedBadge.setForeground(UIManager.getColor("Component.accentColor"));
            nameRow.add(installedBadge);
        }

        JLabel metaLabel = new JLabel(String.join(", ", version.loaders()) + "  —  " + String.join(", ", version.gameVersions()));
        metaLabel.setForeground(mutedColor);

        nameRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        metaLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        textPanel.add(nameRow);
        textPanel.add(metaLabel);
        add(textPanel, BorderLayout.CENTER);

        if (!compatible) {
            nameLabel.setForeground(mutedColor);
            metaLabel.setForeground(mutedColor);
        }

        JButton actionButton = new JButton(installed ? "Retirer" : "Installer");
        actionButton.addActionListener(e -> onAction.run());

        JPanel buttonWrapper = new JPanel(new GridBagLayout());
        buttonWrapper.setOpaque(false);
        buttonWrapper.setBorder(new EmptyBorder(0, 0, 0, 12));
        buttonWrapper.add(actionButton);
        add(buttonWrapper, BorderLayout.EAST);
    }
}
