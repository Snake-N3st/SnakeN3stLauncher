package mc.snakenest.launcher.ui.bugreport;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.UIManager;
import java.awt.Component;
import java.awt.Dimension;

/** The "Signaler un bug" entry point in Settings - a voluntary report, no crash involved. */
public final class ManualBugReportDialog {

    private ManualBugReportDialog() {
    }

    public record Input(String summary, String description) {
    }

    /** @return the filled-in form, or {@code null} if cancelled or left without a summary. */
    public static Input ask(Component parent) {
        JTextField summary = new JTextField(28);
        JTextArea description = new JTextArea(6, 28);
        description.setLineWrap(true);
        description.setWrapStyleWord(true);

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.add(hint("Décris le problème rencontré - un rapport technique (version, système) est joint automatiquement."));
        form.add(javax.swing.Box.createVerticalStrut(8));
        JLabel summaryLabel = new JLabel("Résumé");
        summaryLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(summaryLabel);
        summary.setAlignmentX(Component.LEFT_ALIGNMENT);
        summary.setMaximumSize(new Dimension(400, 28));
        form.add(summary);
        form.add(javax.swing.Box.createVerticalStrut(8));
        JLabel descriptionLabel = new JLabel("Description (optionnelle)");
        descriptionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(descriptionLabel);
        JScrollPane scroll = new JScrollPane(description);
        scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        scroll.setMaximumSize(new Dimension(400, 120));
        form.add(scroll);

        int result = JOptionPane.showConfirmDialog(parent, form, "Signaler un bug",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return null;
        }
        String summaryText = summary.getText().trim();
        if (summaryText.isEmpty()) {
            return null;
        }
        return new Input(summaryText, description.getText().trim());
    }

    private static JLabel hint(String text) {
        JLabel label = new JLabel("<html>" + text + "</html>");
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setFont(label.getFont().deriveFont(12f));
        label.setForeground(UIManager.getColor("Label.disabledForeground"));
        label.setPreferredSize(new Dimension(360, 34));
        return label;
    }
}
