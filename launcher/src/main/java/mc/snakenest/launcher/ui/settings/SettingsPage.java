package mc.snakenest.launcher.ui.settings;

import mc.snakenest.launcher.auth.ChannelInfo;
import mc.snakenest.launcher.config.AssetStorageMode;
import mc.snakenest.launcher.config.LaunchWindowAction;
import mc.snakenest.launcher.config.Theme;
import mc.snakenest.launcher.game.JavaRuntimes;
import mc.snakenest.launcher.ui.about.LicensesDialog;
import mc.snakenest.launcher.ui.common.Borders;
import mc.snakenest.launcher.ui.common.Colors;
import mc.snakenest.launcher.ui.common.JavaBinaryPicker;
import mc.snakenest.launcher.util.AppVersion;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.ButtonGroup;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Font;
import java.net.URI;
import java.util.List;
import java.util.function.Consumer;

/** Theme toggle, Discord status toggle, data folder shortcut, logout - see mockups for the general "settings-ish" tone. */
public final class SettingsPage extends JPanel {

    private static final String SOURCE_URL = "https://github.com/Snake-N3st/SnakeN3stLauncher";

    private final JComboBox<ChannelOption> channelCombo = new JComboBox<>();

    public SettingsPage(Theme currentTheme, Consumer<Theme> onThemeChanged, boolean discordEnabled, Consumer<Boolean> onDiscordToggled,
                         AssetStorageMode currentAssetStorageMode, Consumer<AssetStorageMode> onAssetStorageModeChanged,
                         LaunchWindowAction currentLaunchWindowAction, Consumer<LaunchWindowAction> onLaunchWindowActionChanged,
                         String currentChannel, Consumer<String> onChannelChanged,
                         String currentDefaultJavaPath, Consumer<String> onDefaultJavaPathChanged,
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

        content.add(section("Fenêtre du launcher au lancement d'un jeu",
                launchWindowActionRows(currentLaunchWindowAction, onLaunchWindowActionChanged)));
        content.add(javax.swing.Box.createVerticalStrut(24));

        content.add(section("Canal de distribution du launcher", channelRow(currentChannel, onChannelChanged)));
        content.add(javax.swing.Box.createVerticalStrut(24));

        content.add(section("Java utilisé par les modpacks",
                defaultJavaRow(currentDefaultJavaPath, onDefaultJavaPathChanged)));
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

    /**
     * The launcher-wide Java default. Empty means the launcher's own JVM, which is what every
     * install did before this setting existed - so the field names that JVM explicitly rather than
     * leaving "default" as something the player has to guess at.
     */
    private JPanel defaultJavaRow(String currentPath, Consumer<String> onChanged) {
        JavaBinaryPicker picker = new JavaBinaryPicker(currentPath,
                "Java du launcher (" + JavaRuntimes.launcherJavaBinary() + ")");
        picker.onChange(onChanged);

        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));

        JLabel hint = new JLabel("Un modpack peut choisir son propre Java depuis « Gérer ».");
        hint.setForeground(Colors.muted());
        hint.setFont(hint.getFont().deriveFont(java.awt.Font.PLAIN, 11f));

        // BoxLayout centres children whose alignmentX disagree, which left this hint floating in
        // the middle while every other hint on the page sits flush left.
        picker.setAlignmentX(LEFT_ALIGNMENT);
        hint.setAlignmentX(LEFT_ALIGNMENT);

        column.add(picker);
        column.add(hint);

        return column;
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

    /**
     * Global, not per-modpack (see {@link LaunchWindowAction}'s own Javadoc for why) - one choice
     * applies no matter which modpack is launched, and correctly accounts for several running in
     * parallel (the window only re-shows/closes once every one of them has exited).
     */
    private JPanel launchWindowActionRows(LaunchWindowAction current, Consumer<LaunchWindowAction> onChanged) {
        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));

        ButtonGroup group = new ButtonGroup();

        column.add(launchWindowActionOption(group, current, onChanged, LaunchWindowAction.STAY_OPEN,
                "Rester ouvert",
                "Le launcher reste affiché pendant que le jeu tourne. Recommandé."));
        column.add(javax.swing.Box.createVerticalStrut(10));

        column.add(launchWindowActionOption(group, current, onChanged, LaunchWindowAction.HIDE_AND_RESHOW,
                "Se cacher",
                "Le launcher se cache au lancement et réapparaît automatiquement à la fermeture du jeu."));
        column.add(javax.swing.Box.createVerticalStrut(10));

        column.add(launchWindowActionOption(group, current, onChanged, LaunchWindowAction.CLOSE_ON_LAUNCH,
                "Se fermer",
                "Le launcher se ferme au lancement du jeu."));

        JLabel help = new JLabel("<html>Si plusieurs modpacks tournent en même temps, le launcher ne réapparaît/ne se ferme "
                + "qu'une fois qu'ils sont <b>tous</b> terminés.</html>");
        help.setAlignmentX(Component.LEFT_ALIGNMENT);
        help.setFont(help.getFont().deriveFont(Font.PLAIN, 12f));
        help.setForeground(javax.swing.UIManager.getColor("Label.disabledForeground"));
        help.setBorder(new EmptyBorder(10, 0, 0, 0));
        column.add(help);

        return column;
    }

    private JPanel launchWindowActionOption(ButtonGroup group, LaunchWindowAction current, Consumer<LaunchWindowAction> onChanged,
                                             LaunchWindowAction action, String label, String description) {
        JPanel option = new JPanel();
        option.setOpaque(false);
        option.setLayout(new BoxLayout(option, BoxLayout.Y_AXIS));
        option.setAlignmentX(Component.LEFT_ALIGNMENT);

        JRadioButton button = new JRadioButton(label, current == action);
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.addActionListener(e -> onChanged.accept(action));
        group.add(button);
        option.add(button);

        JLabel help = new JLabel(description);
        help.setAlignmentX(Component.LEFT_ALIGNMENT);
        help.setFont(help.getFont().deriveFont(Font.PLAIN, 12f));
        help.setForeground(javax.swing.UIManager.getColor("Label.disabledForeground"));
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

    /**
     * Starts disabled with a single "Chargement..." placeholder - the real list needs a network
     * round-trip (see {@code auth.LauncherAuthApiClient#fetchChannels}), fetched by the caller and
     * handed back via {@link #setAvailableChannels} once it arrives, rather than blocking this
     * page's construction on it. Read by the bootstrap on its *next* run (see
     * {@code config.LauncherConfig#channel}'s own Javadoc) - this page never fetches a jar itself.
     */
    private JPanel channelRow(String currentChannel, Consumer<String> onChannelChanged) {
        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));

        channelCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
        channelCombo.setEnabled(false);
        channelCombo.addItem(new ChannelOption(currentChannel, "Chargement..."));
        // Only the player's own choice should ever fire the callback - repopulating the combo's
        // items below (setAvailableChannels) would otherwise also fire selection-changed events
        // for the placeholder-to-real-list swap, persisting a channel the player never picked.
        channelCombo.addActionListener(e -> {
            if (channelCombo.isEnabled()) {
                ChannelOption selected = (ChannelOption) channelCombo.getSelectedItem();
                if (selected != null) {
                    onChannelChanged.accept(selected.value());
                }
            }
        });
        column.add(channelCombo);

        JLabel help = new JLabel("<html>Détermine quelle version du launcher le lanceur (bootstrap) télécharge au prochain démarrage. "
                + "Un canal restreint peut nécessiter une reconnexion.</html>");
        help.setAlignmentX(Component.LEFT_ALIGNMENT);
        help.setFont(help.getFont().deriveFont(Font.PLAIN, 12f));
        help.setForeground(javax.swing.UIManager.getColor("Label.disabledForeground"));
        help.setBorder(new EmptyBorder(6, 0, 0, 0));
        column.add(help);

        return column;
    }

    /**
     * Replaces the "Chargement..." placeholder with the real channel list once the async fetch
     * (kicked off by the caller right after constructing this page) resolves. Safe from any
     * thread. {@code currentChannel} is re-passed (rather than read back from the combo) since the
     * placeholder item never actually carried the real value anywhere the player could see it.
     */
    public void setAvailableChannels(List<ChannelInfo> channels, String currentChannel) {
        SwingUtilities.invokeLater(() -> {
            channelCombo.removeAllItems();
            channelCombo.addItem(new ChannelOption(null, "Canal par défaut"));
            for (ChannelInfo channel : channels) {
                String label = channel.name() + (channel.restricted() ? " (restreint)" : "") + (channel.isDefault() ? " — défaut" : "");
                channelCombo.addItem(new ChannelOption(channel.name(), label));
            }
            selectChannel(currentChannel);
            channelCombo.setEnabled(true);
        });
    }

    private void selectChannel(String value) {
        for (int i = 0; i < channelCombo.getItemCount(); i++) {
            ChannelOption option = channelCombo.getItemAt(i);
            if (java.util.Objects.equals(option.value(), value)) {
                channelCombo.setSelectedItem(option);
                return;
            }
        }
        // The player's saved channel no longer exists (deleted, or hand-edited config) - falls
        // back to visibly showing "default" rather than silently selecting some other channel.
        channelCombo.setSelectedIndex(0);
    }

    private record ChannelOption(String value, String label) {
        @Override
        public String toString() {
            return label;
        }
    }
}
