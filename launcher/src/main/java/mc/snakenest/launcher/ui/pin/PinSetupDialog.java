package mc.snakenest.launcher.ui.pin;

import mc.snakenest.launcher.crypto.PinProtection;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.UIManager;
import java.awt.Component;
import java.awt.Dimension;
import java.util.Arrays;

/**
 * Asks for a new PIN twice (to catch a typo in something that can't be recovered if mistyped) when
 * the player turns the setting on, and for the current one when they turn it off.
 *
 * <p>Both are {@link JOptionPane}-based rather than a hand-built {@link javax.swing.JDialog} - same
 * choice as {@code ui.modpack.ModpackSettingsDialog} for a form this small. The startup gate is a
 * real window instead ({@link PinUnlockDialog}), because that one has to stand on its own with no
 * shell behind it.
 */
public final class PinSetupDialog {

    private PinSetupDialog() {
    }

    /**
     * @return the chosen PIN, or {@code null} if cancelled. The caller owns the array and should
     *         zero it once it has derived a key from it.
     */
    public static char[] askNewPin(Component parent) {
        JPasswordField first = new JPasswordField(12);
        JPasswordField confirm = new JPasswordField(12);

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.add(hint("Choisis un code de " + PinProtection.MIN_LENGTH + " à " + PinProtection.MAX_LENGTH + " chiffres."));
        form.add(hint("Il sera demandé à chaque démarrage du launcher."));
        form.add(hint("Attention : il est impossible de le récupérer si tu l'oublies."));
        form.add(javax.swing.Box.createVerticalStrut(10));
        form.add(labelled("Code PIN", first));
        form.add(javax.swing.Box.createVerticalStrut(6));
        form.add(labelled("Confirmation", confirm));

        while (true) {
            int result = JOptionPane.showConfirmDialog(parent, form, "Activer le code PIN",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (result != JOptionPane.OK_OPTION) {
                Arrays.fill(first.getPassword(), '\0');
                Arrays.fill(confirm.getPassword(), '\0');
                return null;
            }

            char[] pin = first.getPassword();
            char[] again = confirm.getPassword();
            try {
                if (!PinProtection.isValidPin(pin)) {
                    JOptionPane.showMessageDialog(parent,
                            "Le code doit contenir entre " + PinProtection.MIN_LENGTH + " et " + PinProtection.MAX_LENGTH + " chiffres.",
                            "Code invalide", JOptionPane.WARNING_MESSAGE);
                    continue;
                }
                if (!Arrays.equals(pin, again)) {
                    JOptionPane.showMessageDialog(parent, "Les deux codes ne correspondent pas.",
                            "Code invalide", JOptionPane.WARNING_MESSAGE);
                    continue;
                }
                return pin.clone();
            } finally {
                Arrays.fill(pin, '\0');
                Arrays.fill(again, '\0');
            }
        }
    }

    /**
     * @return the entered PIN, or {@code null} if cancelled. Correctness isn't checked here - the
     *         caller has to actually try decrypting with it (see {@code crypto.PinProtection}).
     */
    public static char[] askCurrentPin(Component parent, String title, String message) {
        JPasswordField field = new JPasswordField(12);
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.add(hint(message));
        form.add(javax.swing.Box.createVerticalStrut(10));
        form.add(labelled("Code PIN", field));

        int result = JOptionPane.showConfirmDialog(parent, form, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        char[] pin = field.getPassword();
        if (result != JOptionPane.OK_OPTION) {
            Arrays.fill(pin, '\0');
            return null;
        }
        return pin;
    }

    private static JPanel labelled(String label, JPasswordField field) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel caption = new JLabel(label + " : ");
        caption.setPreferredSize(new Dimension(100, 24));
        row.add(caption);
        field.setMaximumSize(new Dimension(160, 26));
        row.add(field);
        return row;
    }

    private static JLabel hint(String text) {
        JLabel label = new JLabel(text);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setFont(label.getFont().deriveFont(12f));
        label.setForeground(UIManager.getColor("Label.disabledForeground"));
        return label;
    }
}
