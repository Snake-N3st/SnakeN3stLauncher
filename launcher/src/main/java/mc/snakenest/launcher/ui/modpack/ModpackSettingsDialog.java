package mc.snakenest.launcher.ui.modpack;

import mc.snakenest.launcher.modpack.ModpackSettings;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.EmptyBorder;
import java.awt.Component;
import java.awt.GridLayout;
import java.util.function.Consumer;

/**
 * The "Gérer" action's memory allocation / custom JVM args form - a plain
 * {@link JOptionPane} confirm dialog rather than a hand-rolled {@code
 * JDialog}: two fields and an OK/Cancel pair don't need more than that.
 */
final class ModpackSettingsDialog {

    private static final int MIN_MEMORY_MB = 512;
    private static final int MAX_MEMORY_MB = 32768;
    private static final int MEMORY_STEP_MB = 512;

    private ModpackSettingsDialog() {
    }

    static void show(Component parent, ModpackSettings current, Consumer<ModpackSettings> onSave) {
        JSpinner memorySpinner = new JSpinner(new SpinnerNumberModel(
                clamp(current.memoryMb()), MIN_MEMORY_MB, MAX_MEMORY_MB, MEMORY_STEP_MB));
        JTextField argsField = new JTextField(current.extraJvmArgs());

        JPanel form = new JPanel(new GridLayout(0, 1, 4, 4));
        form.setBorder(new EmptyBorder(0, 0, 8, 0));
        form.add(new JLabel("Mémoire allouée (Mo) :"));
        form.add(memorySpinner);
        form.add(new JLabel("Arguments JVM personnalisés :"));
        form.add(argsField);

        int result = JOptionPane.showConfirmDialog(parent, form, "Gérer le modpack",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            onSave.accept(new ModpackSettings((Integer) memorySpinner.getValue(), argsField.getText().trim()));
        }
    }

    private static int clamp(int memoryMb) {
        return Math.max(MIN_MEMORY_MB, Math.min(MAX_MEMORY_MB, memoryMb));
    }
}
