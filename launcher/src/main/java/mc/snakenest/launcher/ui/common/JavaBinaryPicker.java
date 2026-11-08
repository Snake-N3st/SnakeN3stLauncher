package mc.snakenest.launcher.ui.common;

import mc.snakenest.launcher.game.JavaRuntimes;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.File;
import java.util.function.Consumer;

/**
 * Path field + "Parcourir" + "Vérifier" for choosing a {@code java} binary, shared by the
 * per-modpack settings dialog and the launcher-wide settings page.
 *
 * <p>Leaving the field empty is a real choice, not an omission - it means "inherit" (the
 * launcher-wide default for a modpack, the launcher's own JVM for the global setting), so the empty
 * state is labelled explicitly rather than left blank for the player to interpret.
 *
 * <p>The "Vérifier" button runs the candidate and shows what it reports. That matters more here
 * than the usual file-exists check: a plausible-looking path can be a broken symlink into a JDK
 * that was uninstalled, the wrong architecture, or simply not a JVM - and without this the player
 * would only discover it later, as a game that refuses to start for no visible reason.
 */
public final class JavaBinaryPicker extends JPanel {

    /** Columns rather than a pixel width: gives the dialog a sane preferred size in any font. */
    private final JTextField pathField = new JTextField(24);
    private final JLabel status = new JLabel(" ");
    private final String emptyMeaning;

    /** Notified when the value is committed - never on every keystroke. */
    private Consumer<String> onChange = path -> {
    };

    /**
     * @param initialPath  the saved path, or {@code null}/blank to start on "inherit"
     * @param emptyMeaning what leaving it empty falls back to, shown to the player
     */
    public JavaBinaryPicker(String initialPath, String emptyMeaning) {
        super(new BorderLayout(6, 4));

        this.emptyMeaning = emptyMeaning;

        pathField.setText(JavaRuntimes.normalize(initialPath) != null ? initialPath.trim() : "");

        JButton browse = new JButton("Parcourir...");
        browse.addActionListener(e -> browse());

        JButton verify = new JButton("Vérifier");
        verify.addActionListener(e -> verify());

        JPanel buttons = new JPanel(new BorderLayout(6, 0));
        buttons.add(browse, BorderLayout.WEST);
        buttons.add(verify, BorderLayout.EAST);

        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.add(pathField, BorderLayout.CENTER);
        row.add(buttons, BorderLayout.EAST);

        status.setFont(status.getFont().deriveFont(Font.PLAIN, 11f));

        add(row, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);

        // Committed on Enter and when the field loses focus, rather than per keystroke: a
        // half-typed path is never a value worth saving, still less worth running.
        pathField.addActionListener(e -> commit());
        pathField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                commit();
            }
        });

        showInheritHint();
    }

    /**
     * Registers the listener told about committed values. Used by the launcher-wide settings page,
     * which saves as you go; the per-modpack dialog reads {@link #selectedPath()} on OK instead.
     */
    public void onChange(Consumer<String> listener) {
        this.onChange = listener != null ? listener : path -> {
        };
    }

    /** The chosen binary, or {@code null} when the field is empty ("inherit"). */
    public String selectedPath() {
        return JavaRuntimes.normalize(pathField.getText());
    }

    private void commit() {
        onChange.accept(selectedPath());
    }

    private void browse() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choisir un exécutable Java");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

        String current = selectedPath();
        if (current != null) {
            File file = new File(current);
            chooser.setCurrentDirectory(file.getParentFile() != null ? file.getParentFile() : file);
        }

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            pathField.setText(chooser.getSelectedFile().getAbsolutePath());
            commit();
            // Chosen from disk rather than typed, so verifying it immediately costs the player
            // nothing and catches "right name, wrong JDK" straight away.
            verify();
        }
    }

    private void verify() {
        String candidate = selectedPath();

        if (candidate == null) {
            showInheritHint();
            return;
        }

        status.setForeground(Colors.muted());
        status.setText("Vérification...");

        // Off the EDT: running an unknown binary can take a moment, and the whole point of this
        // button is that the binary might be broken.
        new SwingWorker<String, Void>() {
            private String error;

            @Override
            protected String doInBackground() {
                try {
                    return JavaRuntimes.describe(candidate);
                } catch (Exception e) {
                    error = e.getMessage();
                    return null;
                }
            }

            @Override
            protected void done() {
                String description = null;
                try {
                    description = get();
                } catch (Exception ignored) {
                    // Falls through to the error branch below.
                }

                if (description != null) {
                    status.setForeground(Colors.muted());
                    status.setText("OK — " + description);
                } else {
                    status.setForeground(Colors.danger());
                    status.setText("Java inutilisable : " + (error != null ? error : "vérification impossible."));
                }
            }
        }.execute();
    }

    private void showInheritHint() {
        SwingUtilities.invokeLater(() -> {
            status.setForeground(Colors.muted());
            status.setText("Vide = " + emptyMeaning + ".");
        });
    }
}
