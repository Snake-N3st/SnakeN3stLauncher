package mc.snakenest.launcher.ui.settings;

import mc.snakenest.launcher.config.Theme;
import mc.snakenest.launcher.ui.about.LicensesDialog;

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
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Font;
import java.net.URI;
import java.util.function.Consumer;

/** Theme toggle, Discord status toggle, data folder shortcut, logout - see mockups for the general "settings-ish" tone. */
public final class SettingsPage extends JPanel {

    private static final String SOURCE_URL = "https://github.com/Snake-N3st/SnakeN3stLauncher";

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
        content.add(javax.swing.Box.createVerticalStrut(24));

        content.add(section("A propos", aboutRows()));

        content.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(content, BorderLayout.NORTH);
    }

    /**
     * Version + link to the public source repo (this jar is GPL-3.0 - see
     * {@code THIRD-PARTY-NOTICES.md} - so a recipient needs an easy way to find the Corresponding
     * Source from inside the app itself) plus the third-party license list, stacked as their own
     * mini rows since neither is a single-row "settings toggle" like the sections above.
     */
    private JPanel aboutRows() {
        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));

        JLabel version = new JLabel("SnakeN3st Launcher " + appVersion());
        version.setAlignmentX(Component.LEFT_ALIGNMENT);
        column.add(version);
        column.add(javax.swing.Box.createVerticalStrut(6));

        JLabel sourceLink = new JLabel("Code source (GitHub)");
        sourceLink.setAlignmentX(Component.LEFT_ALIGNMENT);
        sourceLink.setForeground(new java.awt.Color(0x4A9EFF));
        sourceLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        sourceLink.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                openSourceUrl();
            }
        });
        column.add(sourceLink);
        column.add(javax.swing.Box.createVerticalStrut(10));

        JPanel licensesRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        licensesRow.setOpaque(false);
        licensesRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton licenses = new JButton("Voir les licences tierces");
        licenses.addActionListener(e -> LicensesDialog.show(this));
        licensesRow.add(licenses);
        column.add(licensesRow);

        return column;
    }

    private static String appVersion() {
        String version = SettingsPage.class.getPackage().getImplementationVersion();
        return version != null ? version : "dev";
    }

    private void openSourceUrl() {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(SOURCE_URL));
            }
        } catch (Exception e) {
            javax.swing.JOptionPane.showMessageDialog(this,
                    "Impossible d'ouvrir le navigateur. Lien : " + SOURCE_URL,
                    "Code source", javax.swing.JOptionPane.INFORMATION_MESSAGE);
        }
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
