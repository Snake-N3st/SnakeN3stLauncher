package mc.snakenest.launcher.ui.modpack;

import mc.snakenest.launcher.ui.common.AvatarPanel;
import mc.snakenest.launcher.ui.common.Buttons;
import mc.snakenest.launcher.ui.common.Icons;
import mc.snakenest.launcher.util.HumanSize;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Font;

/**
 * One modpack's detail view: logo/description on the left, install/launch
 * progress and the "Demarrer" action along the bottom (see the third
 * mockup). Progress updates arrive asynchronously (install/sync run off the
 * EDT) via {@link #setStatus}/{@link #setProgress} - safe to call from any
 * thread, both hop to the EDT themselves.
 */
public final class ModpackDetailPage extends JPanel {

    // Deliberately bigger than a bare "same number as the primary button's icon" - these
    // sat too small (both the icon itself and the button around it) compared to "Demarrer".
    private static final int FOOTER_ICON_SIZE = 26;

    private final JButton demarrerButton;
    private final JLabel statusLabel = new JLabel(" ");
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private boolean installed;

    public ModpackDetailPage(ModpackDetailViewModel viewModel) {
        this.installed = viewModel.installed();
        setLayout(new BorderLayout(24, 16));
        setBorder(new EmptyBorder(24, 24, 24, 24));

        JPanel header = new JPanel(new BorderLayout(20, 0));
        header.setOpaque(false);
        AvatarPanel avatar = new AvatarPanel(viewModel.name(), 96);
        avatar.setImage(viewModel.logo());
        header.add(avatar, BorderLayout.WEST);

        JPanel headerText = new JPanel();
        headerText.setOpaque(false);
        headerText.setLayout(new BoxLayout(headerText, BoxLayout.Y_AXIS));

        JLabel nameLabel = new JLabel(viewModel.name());
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 24f));
        headerText.add(nameLabel);

        headerText.add(new JLabel(viewModel.fileCount() + " fichiers"));
        headerText.add(new JLabel(HumanSize.format(viewModel.totalSizeBytes())));
        header.add(headerText, BorderLayout.CENTER);

        add(header, BorderLayout.NORTH);

        JTextArea description = new JTextArea(viewModel.description() == null ? "" : viewModel.description());
        description.setEditable(false);
        description.setOpaque(false);
        description.setLineWrap(true);
        description.setWrapStyleWord(true);
        description.setFont(description.getFont().deriveFont(14f));
        add(description, BorderLayout.CENTER);

        demarrerButton = new JButton();
        demarrerButton.putClientProperty("JButton.buttonType", "roundRect");
        demarrerButton.addActionListener(e -> viewModel.onDemarrer().run());
        applyDemarrerLabel();

        add(buildFooter(viewModel), BorderLayout.SOUTH);
    }

    private JPanel buildFooter(ModpackDetailViewModel viewModel) {
        JPanel footer = new JPanel();
        footer.setOpaque(false);
        footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));

        progressBar.setVisible(false);
        statusLabel.setVisible(false);
        footer.add(statusLabel);
        footer.add(progressBar);
        footer.add(javax.swing.Box.createVerticalStrut(8));

        JPanel buttons = new JPanel(new BorderLayout(8, 0));
        buttons.setOpaque(false);

        JButton settingsButton = Buttons.iconButton(Icons.gear(FOOTER_ICON_SIZE), "Gerer le modpack", () -> {
        });
        settingsButton.addActionListener(e -> showSettingsMenu(settingsButton, viewModel));

        JPanel leftButtons = new JPanel();
        leftButtons.setOpaque(false);
        leftButtons.add(settingsButton);
        leftButtons.add(Buttons.iconButton(Icons.folder(FOOTER_ICON_SIZE), "Ouvrir le dossier", viewModel.onOpenFolder()));
        buttons.add(leftButtons, BorderLayout.WEST);
        buttons.add(demarrerButton, BorderLayout.CENTER);

        footer.add(buttons);
        return footer;
    }

    /** Gerer (memory/JVM args)/Reparer/Desinstaller - see ModpackDetailViewModel's Javadoc for what each does. */
    private void showSettingsMenu(JButton anchor, ModpackDetailViewModel viewModel) {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem manage = new JMenuItem("Gerer...");
        manage.addActionListener(e -> ModpackSettingsDialog.show(this, viewModel.settings(), viewModel.onSaveSettings()));
        menu.add(manage);

        JMenuItem repair = new JMenuItem("Reparer");
        repair.setEnabled(installed);
        repair.addActionListener(e -> viewModel.onRepair().run());
        menu.add(repair);

        JMenuItem uninstall = new JMenuItem("Desinstaller");
        uninstall.setEnabled(installed);
        uninstall.addActionListener(e -> {
            int choice = JOptionPane.showConfirmDialog(this,
                    "Supprimer completement les fichiers installes de \"" + viewModel.name() + "\" ?",
                    "Desinstaller", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice == JOptionPane.YES_OPTION) {
                viewModel.onUninstall().run();
            }
        });
        menu.add(uninstall);

        menu.show(anchor, 0, anchor.getHeight());
    }

    /** "Telecharger" (not installed yet - this action installs it) vs "Demarrer" (already installed). */
    private void applyDemarrerLabel() {
        demarrerButton.setText(installed ? "Demarrer" : "Telecharger");
        demarrerButton.setIcon(installed ? Icons.play(18) : Icons.download(18));
    }

    /** Switches the button from "Telecharger" to "Demarrer" once install finishes. Safe from any thread. */
    public void setInstalled(boolean installed) {
        SwingUtilities.invokeLater(() -> {
            this.installed = installed;
            applyDemarrerLabel();
        });
    }

    /** Shows/hides and sets the label above the progress bar. Safe from any thread. */
    public void setStatus(String label) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText(label);
            statusLabel.setVisible(label != null && !label.isBlank());
        });
    }

    /** @param fraction 0.0 to 1.0, or a negative value to hide the bar (e.g. once done) */
    public void setProgress(double fraction) {
        SwingUtilities.invokeLater(() -> {
            progressBar.setVisible(fraction >= 0);
            progressBar.setValue((int) Math.round(Math.max(0, Math.min(1, fraction)) * 100));
        });
    }

    public void setDemarrerEnabled(boolean enabled) {
        SwingUtilities.invokeLater(() -> demarrerButton.setEnabled(enabled));
    }
}
