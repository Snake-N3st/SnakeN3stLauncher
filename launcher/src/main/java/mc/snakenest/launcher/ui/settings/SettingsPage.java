package mc.snakenest.launcher.ui.settings;

import mc.snakenest.launcher.config.AssetStorageMode;
import mc.snakenest.launcher.config.Theme;
import mc.snakenest.launcher.ui.about.LicensesDialog;
import mc.snakenest.launcher.ui.common.Borders;
import mc.snakenest.launcher.util.AppVersion;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
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
                         AssetStorageMode currentAssetStorageMode, Consumer<AssetStorageMode> onAssetStorageModeChanged,
                         boolean pinEnabled, java.util.function.Predicate<Boolean> onPinToggled,
                         Runnable onMigrateAssetStorage,
                         Runnable onOpenDataFolder, Runnable onCleanup,
                         boolean bugReportsEnabled, Runnable onReportBug, Runnable onLogout) {
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(24, 24, 24, 24));

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        content.add(section("Apparence", themeRow(currentTheme, onThemeChanged)));
        content.add(javax.swing.Box.createVerticalStrut(24));

        content.add(section("Discord", discordRow(discordEnabled, onDiscordToggled)));
        content.add(javax.swing.Box.createVerticalStrut(24));

        content.add(section("Stockage des ressources vanilla",
                assetStorageModeRows(currentAssetStorageMode, onAssetStorageModeChanged, onMigrateAssetStorage)));
        content.add(javax.swing.Box.createVerticalStrut(24));

        content.add(section("Sécurité", pinRows(pinEnabled, onPinToggled)));
        content.add(javax.swing.Box.createVerticalStrut(24));

        content.add(section("Données", dataRows(onOpenDataFolder, onCleanup)));
        content.add(javax.swing.Box.createVerticalStrut(24));

        // Hidden entirely rather than shown-but-disabled when the site has turned reporting off
        // for this client: a greyed-out button invites "why can't I report this?", an absent
        // section just isn't a feature this launcher has.
        if (bugReportsEnabled) {
            content.add(section("Support", supportRows(onReportBug)));
            content.add(javax.swing.Box.createVerticalStrut(24));
        }

        JPanel logoutRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        logoutRow.setOpaque(false);
        JButton logout = new JButton("Se déconnecter");
        logout.addActionListener(e -> onLogout.run());
        logoutRow.add(logout);
        content.add(section("Compte", logoutRow));
        content.add(javax.swing.Box.createVerticalStrut(24));

        content.add(section("A propos", aboutRows()));

        content.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Scrollable, because this page has outgrown a small window: the sections below the fold
        // (notably "Compte" and the GPL source link, which has to stay reachable) were simply
        // unreachable before, since a BorderLayout.NORTH child just gets clipped with no way to
        // scroll to it. The wrapper keeps content top-aligned exactly as before.
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(content, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(top,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(Borders.none());
        // Swing's default of 1px per notch makes a long page feel broken.
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        add(scroll, BorderLayout.CENTER);
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

        JLabel version = new JLabel("SnakeN3st Launcher " + AppVersion.current());
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

    /**
     * Stacked (rather than side-by-side like {@link #themeRow}) because each option needs a line
     * of explanation - the disk-space/breakage trade-offs aren't guessable from the labels alone.
     */
    private JPanel assetStorageModeRows(AssetStorageMode current, Consumer<AssetStorageMode> onChanged, Runnable onMigrate) {
        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));

        ButtonGroup group = new ButtonGroup();

        column.add(assetStorageModeOption(group, current, onChanged, AssetStorageMode.LAUNCHER_SHARED,
                "Stocker dans les données du launcher",
                "Partagé entre tous les modpacks. Recommandé."));
        column.add(javax.swing.Box.createVerticalStrut(10));

        column.add(assetStorageModeOption(group, current, onChanged, AssetStorageMode.OFFICIAL_MINECRAFT,
                "Stocker dans les données du lanceur officiel / TLauncher",
                "Économise encore plus d'espace disque, mais peut casser les ressources du lanceur officiel "
                        + "(elles se retéléchargent toutes seules si ça arrive)."));
        column.add(javax.swing.Box.createVerticalStrut(10));

        column.add(assetStorageModeOption(group, current, onChanged, AssetStorageMode.PER_MODPACK,
                "Stocker les données séparément dans chaque modpack",
                "Aucun partage : prend beaucoup plus d'espace disque."));
        column.add(javax.swing.Box.createVerticalStrut(14));

        // Changing the radio button alone only affects future installs - this is what applies it to
        // what's already on disk (see game.assets.SharedAssetsLinker#migrate).
        JPanel migrateRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        migrateRow.setOpaque(false);
        migrateRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton migrate = new JButton("Migrer les modpacks déjà installés");
        migrate.addActionListener(e -> onMigrate.run());
        migrateRow.add(migrate);
        column.add(migrateRow);

        JLabel migrateHelp = new JLabel("Applique l'option choisie aux modpacks déjà installés (sinon elle ne vaut que pour les prochaines installations).");
        migrateHelp.setAlignmentX(Component.LEFT_ALIGNMENT);
        migrateHelp.setFont(migrateHelp.getFont().deriveFont(Font.PLAIN, 12f));
        migrateHelp.setForeground(javax.swing.UIManager.getColor("Label.disabledForeground"));
        migrateHelp.setBorder(new EmptyBorder(4, 0, 0, 0));
        column.add(migrateHelp);

        return column;
    }

    private JPanel assetStorageModeOption(ButtonGroup group, AssetStorageMode current, Consumer<AssetStorageMode> onChanged,
                                          AssetStorageMode mode, String label, String description) {
        JPanel option = new JPanel();
        option.setOpaque(false);
        option.setLayout(new BoxLayout(option, BoxLayout.Y_AXIS));
        option.setAlignmentX(Component.LEFT_ALIGNMENT);

        JRadioButton button = new JRadioButton(label, current == mode);
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.addActionListener(e -> onChanged.accept(mode));
        group.add(button);
        option.add(button);

        JLabel help = new JLabel(description);
        help.setAlignmentX(Component.LEFT_ALIGNMENT);
        help.setFont(help.getFont().deriveFont(Font.PLAIN, 12f));
        help.setForeground(javax.swing.UIManager.getColor("Label.disabledForeground"));
        // Indented under its own radio button rather than flush left, so it reads as belonging to
        // that option and not to the section as a whole.
        help.setBorder(new EmptyBorder(0, 24, 0, 0));
        option.add(help);

        return option;
    }

    private JPanel dataRows(Runnable onOpenDataFolder, Runnable onCleanup) {
        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton openFolder = new JButton("Ouvrir le dossier de données");
        openFolder.addActionListener(e -> onOpenDataFolder.run());
        buttons.add(openFolder);

        JButton cleanup = new JButton("Nettoyer");
        cleanup.addActionListener(e -> onCleanup.run());
        buttons.add(cleanup);
        column.add(buttons);

        JLabel help = new JLabel("Supprime les logs, les rapports de crash et les caches. "
                + "Ne touche pas aux mods, configs, mondes, ni à ta session.");
        help.setAlignmentX(Component.LEFT_ALIGNMENT);
        help.setFont(help.getFont().deriveFont(Font.PLAIN, 12f));
        help.setForeground(javax.swing.UIManager.getColor("Label.disabledForeground"));
        help.setBorder(new EmptyBorder(4, 0, 0, 0));
        column.add(help);

        return column;
    }

    private JPanel supportRows(Runnable onReportBug) {
        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        buttons.setOpaque(false);
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton reportBug = new JButton("Signaler un bug");
        reportBug.addActionListener(e -> onReportBug.run());
        buttons.add(reportBug);
        column.add(buttons);

        JLabel help = new JLabel("Envoie un rapport (avec ta description et des infos techniques) à l'équipe.");
        help.setAlignmentX(Component.LEFT_ALIGNMENT);
        help.setFont(help.getFont().deriveFont(Font.PLAIN, 12f));
        help.setForeground(javax.swing.UIManager.getColor("Label.disabledForeground"));
        help.setBorder(new EmptyBorder(4, 0, 0, 0));
        column.add(help);

        return column;
    }

    /**
     * The checkbox reflects whatever {@code onPinToggled} <em>returns</em>, not what was clicked:
     * enabling/disabling a PIN can be cancelled at the dialog, or rejected on a wrong code, and in
     * those cases the box has to snap back instead of lying about the real state.
     */
    private JPanel pinRows(boolean pinEnabled, java.util.function.Predicate<Boolean> onPinToggled) {
        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));

        JCheckBox checkbox = new JCheckBox("Protéger ma session par un code PIN", pinEnabled);
        checkbox.setAlignmentX(Component.LEFT_ALIGNMENT);
        checkbox.addActionListener(e -> checkbox.setSelected(onPinToggled.test(checkbox.isSelected())));
        column.add(checkbox);

        JLabel help = new JLabel("<html>Chiffre ta session avec un code demandé au démarrage, pour limiter le risque de vol de session.<br>"
                + "Chiffre aussi les fichiers sensibles des modpacks (ceux listés dans un fichier <code>.cryptlist</code>).<br>"
                + "<b>Le code est irrécupérable en cas d'oubli</b> - il faudra alors se reconnecter.</html>");
        help.setAlignmentX(Component.LEFT_ALIGNMENT);
        help.setFont(help.getFont().deriveFont(Font.PLAIN, 12f));
        help.setForeground(javax.swing.UIManager.getColor("Label.disabledForeground"));
        help.setBorder(new EmptyBorder(0, 24, 0, 0));
        column.add(help);

        return column;
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
