package mc.snakenest.launcher.ui.modpack;

import mc.snakenest.launcher.ui.common.AvatarPanel;
import mc.snakenest.launcher.ui.common.Buttons;
import mc.snakenest.launcher.ui.common.Icons;
import mc.snakenest.launcher.util.HumanSize;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
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

    private final JButton demarrerButton;
    private final JLabel statusLabel = new JLabel(" ");
    private final JProgressBar progressBar = new JProgressBar(0, 100);

    public ModpackDetailPage(ModpackDetailViewModel viewModel) {
        setLayout(new BorderLayout(24, 16));
        setBorder(new EmptyBorder(24, 24, 24, 24));

        JPanel header = new JPanel(new BorderLayout(20, 0));
        header.setOpaque(false);
        header.add(new AvatarPanel(viewModel.name(), 96), BorderLayout.WEST);

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

        demarrerButton = new JButton("Demarrer", Icons.play(18));
        demarrerButton.putClientProperty("JButton.buttonType", "roundRect");
        demarrerButton.addActionListener(e -> viewModel.onDemarrer().run());

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

        JPanel leftButtons = new JPanel();
        leftButtons.setOpaque(false);
        leftButtons.add(Buttons.flatIcon(Icons.gear(18), "Parametres du modpack", viewModel.onOpenSettings()));
        leftButtons.add(Buttons.flatIcon(Icons.folder(18), "Ouvrir le dossier", viewModel.onOpenFolder()));
        buttons.add(leftButtons, BorderLayout.WEST);
        buttons.add(demarrerButton, BorderLayout.CENTER);

        footer.add(buttons);
        return footer;
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
