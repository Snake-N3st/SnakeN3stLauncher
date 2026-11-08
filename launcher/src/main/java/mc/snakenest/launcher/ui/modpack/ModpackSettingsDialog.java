package mc.snakenest.launcher.ui.modpack;

import mc.snakenest.launcher.auth.ChannelInfo;
import mc.snakenest.launcher.modpack.ModpackSettings;
import mc.snakenest.launcher.ui.common.JavaBinaryPicker;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.EmptyBorder;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * The "Gérer" action's memory allocation / custom JVM args / distribution channel form - a plain
 * {@link JOptionPane} confirm dialog rather than a hand-rolled {@code
 * JDialog}: a handful of fields and an OK/Cancel pair don't need more than that.
 */
final class ModpackSettingsDialog {

    private static final int MIN_MEMORY_MB = 512;
    private static final int MAX_MEMORY_MB = 32768;
    private static final int MEMORY_STEP_MB = 512;

    private ModpackSettingsDialog() {
    }

    /**
     * @param availableChannels whatever the caller's channel fetch has resolved to by the time
     *                          this dialog opens - possibly empty if that fetch hasn't finished
     *                          yet, in which case only "canal par défaut" is offered (this modal
     *                          dialog can't itself wait on a network round-trip).
     */
    static void show(Component parent, ModpackSettings current, List<ChannelInfo> availableChannels, Consumer<ModpackSettings> onSave) {
        JSpinner memorySpinner = new JSpinner(new SpinnerNumberModel(
                clamp(current.memoryMb()), MIN_MEMORY_MB, MAX_MEMORY_MB, MEMORY_STEP_MB));
        JTextField argsField = new JTextField(current.extraJvmArgs());
        JComboBox<ChannelOption> channelCombo = new JComboBox<>();
        channelCombo.addItem(new ChannelOption(null, "Canal par défaut"));
        for (ChannelInfo channel : availableChannels) {
            String label = channel.name() + (channel.restricted() ? " (restreint)" : "") + (channel.isDefault() ? " — défaut" : "");
            channelCombo.addItem(new ChannelOption(channel.name(), label));
        }
        selectChannel(channelCombo, current.channel());

        JavaBinaryPicker javaPicker = new JavaBinaryPicker(current.javaPath(),
                "Par défaut (réglage du launcher)");

        // GridBagLayout rather than GridLayout: the Java picker is two lines tall (field row plus
        // its status line), and GridLayout would have imposed that height on every other row,
        // leaving the spinner and text fields absurdly stretched.
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(0, 0, 8, 0));

        int row = 0;
        row = addRow(form, row, "Mémoire allouée (Mo) :", memorySpinner);
        row = addRow(form, row, "Arguments JVM personnalisés :", argsField);
        row = addRow(form, row, "Canal de distribution :", channelCombo);
        addRow(form, row, "Java utilisé pour ce modpack :", javaPicker);

        int result = JOptionPane.showConfirmDialog(parent, form, "Gérer le modpack",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            ChannelOption selectedChannel = (ChannelOption) channelCombo.getSelectedItem();
            onSave.accept(new ModpackSettings((Integer) memorySpinner.getValue(), argsField.getText().trim(),
                    selectedChannel != null ? selectedChannel.value() : null,
                    javaPicker.selectedPath()));
        }
    }

    /**
     * One label-above-control row, full width. Returns the next free row index so callers read as a
     * sequence rather than juggling indices.
     */
    private static int addRow(JPanel form, int row, String label, java.awt.Component control) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.LINE_START;
        constraints.insets = new Insets(row == 0 ? 0 : 10, 0, 2, 0);
        form.add(new JLabel(label), constraints);

        constraints.gridy = row + 1;
        constraints.insets = new Insets(0, 0, 0, 0);
        form.add(control, constraints);

        return row + 2;
    }

    private static void selectChannel(JComboBox<ChannelOption> combo, String value) {
        for (int i = 0; i < combo.getItemCount(); i++) {
            ChannelOption option = combo.getItemAt(i);
            if (Objects.equals(option.value(), value)) {
                combo.setSelectedItem(option);
                return;
            }
        }
        // The saved channel isn't in the fetched list (deleted, hand-edited settings file, or the
        // list just hadn't loaded yet when this dialog was last used to set it) - falls back to
        // visibly showing "default" rather than silently keeping a channel no longer offered.
        combo.setSelectedIndex(0);
    }

    private static int clamp(int memoryMb) {
        return Math.max(MIN_MEMORY_MB, Math.min(MAX_MEMORY_MB, memoryMb));
    }

    private record ChannelOption(String value, String label) {
        @Override
        public String toString() {
            return label;
        }
    }
}
