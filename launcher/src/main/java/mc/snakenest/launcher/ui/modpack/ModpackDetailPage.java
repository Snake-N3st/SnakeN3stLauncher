package mc.snakenest.launcher.ui.modpack;

import mc.snakenest.launcher.modpack.ModpackSettings;
import mc.snakenest.launcher.ui.common.AvatarPanel;
import mc.snakenest.launcher.ui.common.Buttons;
import mc.snakenest.launcher.ui.common.Colors;
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
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Font;

/**
 * One modpack's detail view: logo/description on the left, install/launch
 * progress and the main action button along the bottom (see the third
 * mockup). Progress updates arrive asynchronously (install/sync run off the
 * EDT) via {@link #setStatus}/{@link #setProgress} - safe to call from any
 * thread, both hop to the EDT themselves.
 *
 * <p>The main button reads differently depending on {@link ButtonState}:
 * "Télécharger"/"Démarrer" at rest (depending on {@code installed}),
 * "Annuler" while a sync/install is in flight ({@link #setBusy}), or
 * "Arrêter" while the game process is running ({@link #setRunning}) - one
 * button, three possible actions, never more than one relevant at a time.
 */
public final class ModpackDetailPage extends JPanel {

    // Deliberately bigger than a bare "same number as the primary button's icon" - these
    // sat too small (both the icon itself and the button around it) compared to the main button.
    private static final int FOOTER_ICON_SIZE = 26;

    private enum ButtonState { IDLE, BUSY, RUNNING }

    private final JButton demarrerButton;
    private final JLabel statusLabel = new JLabel(" ");
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private boolean installed;
    private boolean updateAvailable;
    private ButtonState buttonState = ButtonState.IDLE;
    // viewModel.settings() is frozen at construction time (immutable record) - without tracking
    // the live value here separately, reopening "Gerer" right after saving showed the stale
    // pre-save settings again, since the dialog always read straight from the ViewModel.
    private ModpackSettings currentSettings;

    public ModpackDetailPage(ModpackDetailViewModel viewModel) {
        this.installed = viewModel.installed();
        this.updateAvailable = viewModel.updateAvailable();
        this.currentSettings = viewModel.settings();
        setLayout(new BorderLayout(24, 16));
        setBorder(new EmptyBorder(24, 24, 24, 24));

        JPanel header = new JPanel(new BorderLayout(20, 0));
        header.setOpaque(false);
        AvatarPanel avatar = new AvatarPanel(viewModel.name(), 96);
        avatar.setImage(viewModel.logo());
        header.add(avatar, BorderLayout.WEST);

        // Same fix as ModpackCardView's textPanel: BorderLayout.CENTER stretches this to the
        // full header height (driven by the 96px avatar next to it), and without the glue on
        // both sides BoxLayout.Y_AXIS pins the labels to the top instead of centering them.
        JPanel headerText = new JPanel();
        headerText.setOpaque(false);
        headerText.setLayout(new BoxLayout(headerText, BoxLayout.Y_AXIS));

        JLabel nameLabel = new JLabel(viewModel.name());
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 24f));

        headerText.add(javax.swing.Box.createVerticalGlue());
        headerText.add(nameLabel);
        headerText.add(new JLabel(viewModel.fileCount() + " fichiers"));
        headerText.add(new JLabel(HumanSize.format(viewModel.totalSizeBytes())));
        headerText.add(javax.swing.Box.createVerticalGlue());
        header.add(headerText, BorderLayout.CENTER);

        add(header, BorderLayout.NORTH);

        JTextArea description = new JTextArea(viewModel.description() == null ? "" : viewModel.description());
        description.setEditable(false);
        // setEditable(false) alone still lets the text area take focus on click and show a
        // blinking caret - a read-only description isn't a text input, so it shouldn't be
        // focusable at all.
        description.setFocusable(false);
        description.setOpaque(false);
        description.setLineWrap(true);
        description.setWrapStyleWord(true);
        description.setFont(description.getFont().deriveFont(14f));
        add(description, BorderLayout.CENTER);

        demarrerButton = new JButton();
        demarrerButton.putClientProperty("JButton.buttonType", "roundRect");
        demarrerButton.addActionListener(e -> {
            switch (buttonState) {
                case IDLE -> viewModel.onDemarrer().run();
                case BUSY -> viewModel.onCancel().run();
                case RUNNING -> viewModel.onStop().run();
            }
        });
        applyButtonLabel();

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

        JButton settingsButton = Buttons.iconButton(Icons.gear(FOOTER_ICON_SIZE), "Gérer le modpack", () -> {
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

    /** Gérer (memory/JVM args)/Réparer/Désinstaller - see ModpackDetailViewModel's Javadoc for what each does. */
    private void showSettingsMenu(JButton anchor, ModpackDetailViewModel viewModel) {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem manage = new JMenuItem("Gérer...");
        manage.addActionListener(e -> ModpackSettingsDialog.show(this, currentSettings, newSettings -> {
            currentSettings = newSettings;
            viewModel.onSaveSettings().accept(newSettings);
        }));
        menu.add(manage);

        JMenuItem repair = new JMenuItem("Réparer");
        repair.setEnabled(installed);
        repair.addActionListener(e -> viewModel.onRepair().run());
        menu.add(repair);

        JMenuItem uninstall = new JMenuItem("Désinstaller");
        uninstall.setEnabled(installed);
        uninstall.addActionListener(e -> {
            int choice = JOptionPane.showConfirmDialog(this,
                    "Supprimer complètement les fichiers installés de \"" + viewModel.name() + "\" ?",
                    "Désinstaller", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice == JOptionPane.YES_OPTION) {
                viewModel.onUninstall().run();
            }
        });
        menu.add(uninstall);

        menu.show(anchor, 0, anchor.getHeight());
    }

    /**
     * Label/icon for the current {@link #buttonState} (and, at rest,
     * {@link #installed}/{@link #updateAvailable}): "Télécharger" if never
     * installed, "Mettre à jour" if installed but a newer version exists on
     * the server, "Démarrer" otherwise. {@code onDemarrer} is the same
     * sync+install(+launch) action in all three cases - only the label/icon
     * differ.
     */
    private void applyButtonLabel() {
        switch (buttonState) {
            case IDLE -> {
                if (!installed) {
                    demarrerButton.setText("Télécharger");
                    demarrerButton.setIcon(Icons.download(18));
                } else if (updateAvailable) {
                    demarrerButton.setText("Mettre à jour");
                    demarrerButton.setIcon(Icons.download(18));
                } else {
                    demarrerButton.setText("Démarrer");
                    demarrerButton.setIcon(Icons.play(18));
                }
            }
            case BUSY -> {
                demarrerButton.setText("Annuler");
                demarrerButton.setIcon(Icons.cancel(18));
            }
            case RUNNING -> {
                demarrerButton.setText("Arrêter");
                demarrerButton.setIcon(Icons.stop(18));
            }
        }
    }

    /** Switches the button from "Télécharger" to "Démarrer"/"Mettre à jour" once install finishes. Safe from any thread. */
    public void setInstalled(boolean installed) {
        SwingUtilities.invokeLater(() -> {
            this.installed = installed;
            applyButtonLabel();
        });
    }

    /** Switches the button between "Démarrer" and "Mettre à jour" once installed. Safe from any thread. */
    public void setUpdateAvailable(boolean updateAvailable) {
        SwingUtilities.invokeLater(() -> {
            this.updateAvailable = updateAvailable;
            applyButtonLabel();
        });
    }

    /**
     * While busy, the button reads "Annuler" (cancels the in-progress sync/install) instead of
     * its resting label. Safe from any thread.
     *
     * <p>{@code setBusy(false)} only resets to idle if the state is still {@code BUSY} - a
     * successful install immediately followed by launching the game calls
     * {@link #setRunning}(true) before the caller's {@code finally} block gets to call
     * {@code setBusy(false)}; without this guard that would clobber the just-set
     * {@code RUNNING} state back to idle.
     */
    public void setBusy(boolean busy) {
        SwingUtilities.invokeLater(() -> {
            if (busy) {
                buttonState = ButtonState.BUSY;
            } else if (buttonState == ButtonState.BUSY) {
                buttonState = ButtonState.IDLE;
            }
            demarrerButton.setEnabled(true);
            applyButtonLabel();
        });
    }

    /** While the game is running, the button reads "Arrêter" (kills the game process) instead of its resting label. Safe from any thread. */
    public void setRunning(boolean running) {
        SwingUtilities.invokeLater(() -> {
            if (running) {
                buttonState = ButtonState.RUNNING;
            } else if (buttonState == ButtonState.RUNNING) {
                buttonState = ButtonState.IDLE;
            }
            demarrerButton.setEnabled(true);
            applyButtonLabel();
        });
    }

    /** Shows/hides and sets the label above the progress bar (normal color). Safe from any thread. */
    public void setStatus(String label) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setForeground(UIManager.getColor("Label.foreground"));
            statusLabel.setText(label);
            statusLabel.setVisible(label != null && !label.isBlank());
        });
    }

    /** Same as {@link #setStatus}, but shown in the danger color - for an actual failure, not routine progress. Safe from any thread. */
    public void setError(String message) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setForeground(Colors.danger());
            statusLabel.setText(message);
            statusLabel.setVisible(message != null && !message.isBlank());
        });
    }

    /** @param fraction 0.0 to 1.0, or a negative value to hide the bar (e.g. once done) */
    public void setProgress(double fraction) {
        SwingUtilities.invokeLater(() -> {
            progressBar.setVisible(fraction >= 0);
            progressBar.setValue((int) Math.round(Math.max(0, Math.min(1, fraction)) * 100));
        });
    }
}
