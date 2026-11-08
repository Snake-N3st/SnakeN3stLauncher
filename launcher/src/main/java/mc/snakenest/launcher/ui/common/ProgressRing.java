package mc.snakenest.launcher.ui.common;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;

/**
 * A determinate circular download-progress indicator that swaps to a cancel ("x") button on
 * hover, so a player can actually abort an in-flight download instead of just watching it - see
 * {@code ui.modpack.ModCardView}'s quick-action slot, which shows this in place of its download
 * button while installing (the two are never shown at once, by construction - they're separate
 * cards of the same {@code CardLayout}). Indeterminate (a slowly rotating short arc) only until
 * {@link #setProgress} first reports a real fraction - Modrinth always reports a file size, but
 * it isn't known until the HTTP response headers actually arrive.
 */
public final class ProgressRing extends JComponent {

    private static final int ROTATE_STEP_DEGREES = 8;
    private static final int ROTATE_INTERVAL_MS = 30;

    private final int size;
    private final Timer indeterminateTimer;
    private double progress = -1;
    private int indeterminateAngle;
    private boolean hovering;
    private Runnable onCancel;

    public ProgressRing(int size) {
        this.size = size;
        Dimension d = new Dimension(size, size);
        setPreferredSize(d);
        setMinimumSize(d);
        setMaximumSize(d);
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        indeterminateTimer = new Timer(ROTATE_INTERVAL_MS, e -> {
            indeterminateAngle = (indeterminateAngle + ROTATE_STEP_DEGREES) % 360;
            if (progress < 0) {
                repaint();
            }
        });
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                hovering = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hovering = false;
                repaint();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (hovering && onCancel != null) {
                    onCancel.run();
                }
            }
        });
    }

    public void setOnCancel(Runnable onCancel) {
        this.onCancel = onCancel;
    }

    /** 0.0-1.0, or negative while the total size isn't known yet. Safe from any thread. */
    public void setProgress(double progress) {
        SwingUtilities.invokeLater(() -> {
            this.progress = progress;
            repaint();
        });
    }

    /** Starts/stops the indeterminate-mode rotation timer and resets hover/progress for reuse across installs. Safe from any thread. */
    public void setActive(boolean active) {
        SwingUtilities.invokeLater(() -> {
            if (active) {
                indeterminateTimer.start();
            } else {
                indeterminateTimer.stop();
                hovering = false;
                progress = -1;
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (hovering) {
                paintCancelButton(g2);
            } else if (progress < 0) {
                paintIndeterminate(g2);
            } else {
                paintDeterminate(g2);
            }
        } finally {
            g2.dispose();
        }
    }

    private void paintCancelButton(Graphics2D g2) {
        Color hoverBg = UIManager.getColor("Component.borderColor");
        g2.setColor(hoverBg != null ? hoverBg : getBackground());
        g2.fill(new Ellipse2D.Float(0, 0, size, size));
        Color fg = UIManager.getColor("Label.foreground");
        g2.setColor(fg != null ? fg : getForeground());
        g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int pad = size / 3;
        g2.drawLine(pad, pad, size - pad, size - pad);
        g2.drawLine(size - pad, pad, pad, size - pad);
    }

    private void paintIndeterminate(Graphics2D g2) {
        g2.setColor(accentColor());
        float stroke = strokeWidth();
        float pad = stroke;
        g2.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(new Arc2D.Float(pad, pad, size - pad * 2, size - pad * 2, indeterminateAngle, 100, Arc2D.OPEN));
    }

    private void paintDeterminate(Graphics2D g2) {
        float stroke = strokeWidth();
        float pad = stroke;
        Color track = UIManager.getColor("Component.borderColor");
        g2.setColor(track != null ? track : getBackground());
        g2.setStroke(new BasicStroke(stroke, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        g2.draw(new Ellipse2D.Float(pad, pad, size - pad * 2, size - pad * 2));

        g2.setColor(accentColor());
        g2.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        double extent = Math.max(0, Math.min(1, progress)) * 360;
        // Starts at 12 o'clock and sweeps clockwise (Arc2D's angles run counterclockwise from
        // 3 o'clock, hence the negative extent) - reads as an ordinary loading ring.
        g2.draw(new Arc2D.Float(pad, pad, size - pad * 2, size - pad * 2, 90, (float) -extent, Arc2D.OPEN));
    }

    private Color accentColor() {
        Color accent = UIManager.getColor("Component.accentColor");
        return accent != null ? accent : getForeground();
    }

    private float strokeWidth() {
        return Math.max(2f, size / 8f);
    }
}
