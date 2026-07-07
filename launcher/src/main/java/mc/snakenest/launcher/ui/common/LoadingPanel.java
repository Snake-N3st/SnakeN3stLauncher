package mc.snakenest.launcher.ui.common;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagLayout;

/**
 * A centered label + indeterminate progress bar, shown as an immediate
 * placeholder while something loads over the network - e.g. a modpack's
 * detail page while its manifest is being fetched, or the modpack list
 * while it's being reloaded.
 *
 * <p>Exists specifically so a page transition (clicking a modpack card,
 * hitting "Actualiser") shows *something* changed right away instead of
 * appearing to do nothing until the network call completes, which read as
 * the app hanging.
 */
public final class LoadingPanel extends JPanel {

    public LoadingPanel(String message) {
        setLayout(new GridBagLayout());
        setOpaque(false);

        JPanel inner = new JPanel();
        inner.setOpaque(false);
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));

        JLabel label = new JLabel(message, SwingConstants.CENTER);
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        inner.add(label);
        inner.add(Box.createVerticalStrut(10));

        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setMaximumSize(new Dimension(180, 6));
        progressBar.setAlignmentX(Component.CENTER_ALIGNMENT);
        inner.add(progressBar);

        add(inner);
    }
}
