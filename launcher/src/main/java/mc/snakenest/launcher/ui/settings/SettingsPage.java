package mc.snakenest.launcher.ui.settings;

import mc.snakenest.launcher.config.Theme;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.ButtonGroup;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.function.Consumer;

/** Theme toggle, Discord status toggle, data folder shortcut, logout - see mockups for the general "settings-ish" tone. */
public final class SettingsPage extends JPanel {

    public SettingsPage(Theme currentTheme, Consumer<Theme> onThemeChanged, boolean discordEnabled, Consumer<Boolean> onDiscordToggled,
                         Runnable onOpenDataFolder, Runnable onLogout) {
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(24, 24, 24, 24));

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        content.add(section("Apparence", themeRow(currentTheme, onThemeChanged)));
        content.add(javax.swing.Box.createVerticalStrut(24));

        content.add(section("Discord", discordRow(discordEnabled, onDiscordToggled)));
        content.add(javax.swing.Box.createVerticalStrut(24));

        JPanel dataFolderRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        dataFolderRow.setOpaque(false);
        JButton openFolder = new JButton("Ouvrir le dossier de données");
        openFolder.addActionListener(e -> onOpenDataFolder.run());
        dataFolderRow.add(openFolder);
        content.add(section("Données", dataFolderRow));
        content.add(javax.swing.Box.createVerticalStrut(24));

        JPanel logoutRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        logoutRow.setOpaque(false);
        JButton logout = new JButton("Se déconnecter");
        logout.addActionListener(e -> onLogout.run());
        logoutRow.add(logout);
        content.add(section("Compte", logoutRow));

        content.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(content, BorderLayout.NORTH);
    }

    private JPanel section(String title, javax.swing.JComponent row) {
        JPanel section = new JPanel();
        section.setOpaque(false);
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 15f));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.add(titleLabel);
        section.add(javax.swing.Box.createVerticalStrut(8));

        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.add(row);
        return section;
    }

    private JPanel themeRow(Theme currentTheme, Consumer<Theme> onThemeChanged) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        row.setOpaque(false);

        JRadioButton light = new JRadioButton("Clair", currentTheme == Theme.LIGHT);
        JRadioButton dark = new JRadioButton("Sombre", currentTheme == Theme.DARK);
        ButtonGroup group = new ButtonGroup();
        group.add(light);
        group.add(dark);

        light.addActionListener(e -> onThemeChanged.accept(Theme.LIGHT));
        dark.addActionListener(e -> onThemeChanged.accept(Theme.DARK));

        row.add(light);
        row.add(dark);
        return row;
    }

    private JPanel discordRow(boolean discordEnabled, Consumer<Boolean> onDiscordToggled) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row.setOpaque(false);

        JCheckBox checkbox = new JCheckBox("Afficher mon statut sur Discord", discordEnabled);
        checkbox.addActionListener(e -> onDiscordToggled.accept(checkbox.isSelected()));
        row.add(checkbox);
        return row;
    }
}
