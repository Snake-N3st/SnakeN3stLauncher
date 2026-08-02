package mc.snakenest.launcher.ui.bugreport;

import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Component;
import java.awt.Dimension;

/**
 * Shown after an automatically-detected crash (launcher or game) - never sends anything on its
 * own. Built from {@link JOptionPane} like the PIN dialogs (same reasoning: this is a one-shot
 * confirmation, not a page), but through {@code createDialog} rather than the
 * {@code showConfirmDialog} convenience method, specifically so the resulting {@link JDialog} can
 * be tracked and force-closed - see {@link #closeActiveDialog()}, used by
 * {@code bugreport.CrashLoopWatchdog} to stop a dialog pile-up when exceptions are recurring
 * faster than a player could ever dismiss them one at a time.
 */
public final class CrashPromptDialog {

    // At most one at a time in practice (CrashReportHandler already gates concurrent prompts),
    // but tracked as the single source of truth for "is one open right now" rather than assuming
    // that invariant here too.
    private static volatile JDialog activeDialog;

    private CrashPromptDialog() {
    }

    /**
     * @param summaryLine one-line description of what happened, shown verbatim (already safe -
     *                     built by the caller, never raw exception text)
     * @return the player's optional extra description (possibly blank) if they agreed to send,
     *         or {@code null} if they declined, including if the dialog was force-closed by
     *         {@link #closeActiveDialog()} - {@code null} vs blank matters to the caller, since
     *         only {@code null} means "don't submit anything at all"
     */
    public static String ask(Component parent, String summaryLine) {
        JTextArea extra = new JTextArea(4, 32);
        extra.setLineWrap(true);
        extra.setWrapStyleWord(true);

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.add(hint("Un problème est survenu :"));
        JLabel summary = new JLabel("<html><b>" + escape(summaryLine) + "</b></html>");
        summary.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(summary);
        form.add(javax.swing.Box.createVerticalStrut(8));
        form.add(hint("Envoyer un rapport anonyme à l'équipe pour aider à corriger le problème ?"));
        form.add(hint("Rien n'est envoyé sans ta confirmation."));
        form.add(javax.swing.Box.createVerticalStrut(8));
        JLabel descriptionLabel = new JLabel("Que faisais-tu au moment du problème ? (optionnel)");
        descriptionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(descriptionLabel);
        JScrollPane scroll = new JScrollPane(extra);
        scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        scroll.setMaximumSize(new Dimension(400, 90));
        form.add(scroll);

        JOptionPane pane = new JOptionPane(form, JOptionPane.WARNING_MESSAGE, JOptionPane.YES_NO_OPTION);
        JDialog dialog = pane.createDialog(parent, "Signaler le problème ?");
        activeDialog = dialog;
        try {
            dialog.setVisible(true); // blocks (modal) until dismissed, answered, or disposed
        } finally {
            activeDialog = null;
        }

        Object value = pane.getValue();
        return value instanceof Integer i && i == JOptionPane.YES_OPTION ? extra.getText().trim() : null;
    }

    /**
     * Force-closes whatever crash prompt is currently on screen, if any - a no-op otherwise.
     * Safe to call from any thread; the actual disposal is marshalled onto the EDT, same as the
     * modal dialog it's closing. Disposing a modal dialog is what makes its blocking
     * {@code setVisible(true)} call in {@link #ask} return, exactly as if the player had closed it.
     */
    public static void closeActiveDialog() {
        JDialog dialog = activeDialog;
        if (dialog != null) {
            SwingUtilities.invokeLater(dialog::dispose);
        }
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static JLabel hint(String text) {
        JLabel label = new JLabel(text);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setFont(label.getFont().deriveFont(12f));
        label.setForeground(UIManager.getColor("Label.disabledForeground"));
        return label;
    }
}
