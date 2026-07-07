package mc.snakenest.launcher.ui.about;

import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Font;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * "Voir les licences tierces" from {@code ui.settings.SettingsPage}'s "A propos" section: a
 * dependency list next to the full text of whichever one is selected, read back from the jar's
 * own {@code licenses/} resources (see {@code THIRD-PARTY-NOTICES.md}, which this list is kept in
 * sync with by hand) rather than duplicating the license texts themselves in source.
 */
public final class LicensesDialog {

    private static final List<LicensedDependency> DEPENDENCIES = List.of(
            new LicensedDependency("FlowUpdater", "1.9.4", "GPL-3.0", "GPL-3.0.txt"),
            new LicensedDependency("FlowMultitools", "1.4.5", "GPL-3.0", "GPL-3.0.txt"),
            new LicensedDependency("OpenLauncherLib", "3.2.11", "GPL-3.0", "GPL-3.0.txt"),
            new LicensedDependency("commons-compress", "1.28.0", "Apache-2.0", "Apache-2.0.txt"),
            new LicensedDependency("commons-codec", "1.19.0", "Apache-2.0", "Apache-2.0.txt"),
            new LicensedDependency("commons-io", "2.20.0", "Apache-2.0", "Apache-2.0.txt"),
            new LicensedDependency("commons-lang3", "3.18.0", "Apache-2.0", "Apache-2.0.txt"),
            new LicensedDependency("JetBrains Annotations", "26.1.0", "Apache-2.0", "Apache-2.0.txt"),
            new LicensedDependency("org.json", "20240303", "Public Domain", "PublicDomain-JSON.txt"),
            new LicensedDependency("eddsa", "0.3.0", "CC0-1.0", "CC0-1.0.txt"),
            new LicensedDependency("Gson", "2.14.0", "Apache-2.0", "Apache-2.0.txt"),
            new LicensedDependency("FlatLaf", "3.7.1", "Apache-2.0", "Apache-2.0.txt"),
            new LicensedDependency("DiscordIPC", "a8d6631", "Apache-2.0", "Apache-2.0.txt"),
            new LicensedDependency("junixsocket", "2.6.2", "Apache-2.0", "Apache-2.0.txt"),
            new LicensedDependency("SLF4J API", "2.0.7", "MIT", "MIT.txt")
    );

    private LicensesDialog() {
    }

    public static void show(Component parent) {
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(parent), "Licences tierces", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setLayout(new BorderLayout());

        JTextArea textArea = new JTextArea();
        textArea.setEditable(false);
        textArea.setLineWrap(false);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        textArea.setBorder(new EmptyBorder(8, 8, 8, 8));

        JList<LicensedDependency> list = new JList<>(DEPENDENCIES.toArray(new LicensedDependency[0]));
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && list.getSelectedValue() != null) {
                textArea.setText(loadLicenseText(list.getSelectedValue().licenseResource()));
                textArea.setCaretPosition(0);
            }
        });

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(list), new JScrollPane(textArea));
        split.setDividerLocation(220);

        dialog.add(split, BorderLayout.CENTER);
        dialog.add(closeRow(dialog), BorderLayout.SOUTH);

        dialog.setPreferredSize(new Dimension(680, 480));
        dialog.pack();
        dialog.setLocationRelativeTo(parent);

        list.setSelectedIndex(0);
        dialog.setVisible(true);
    }

    private static JPanel closeRow(JDialog dialog) {
        JPanel row = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 8));
        javax.swing.JButton close = new javax.swing.JButton("Fermer");
        close.addActionListener(e -> dialog.dispose());
        row.add(close);
        return row;
    }

    private static String loadLicenseText(String resource) {
        try (InputStream in = LicensesDialog.class.getResourceAsStream("/licenses/" + resource)) {
            if (in == null) {
                return "(licence introuvable dans le jar : " + resource + ")";
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "(erreur de lecture de la licence " + resource + " : " + e.getMessage() + ")";
        }
    }
}
