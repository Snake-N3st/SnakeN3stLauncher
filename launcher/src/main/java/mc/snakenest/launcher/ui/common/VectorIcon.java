package mc.snakenest.launcher.ui.common;

import javax.swing.Icon;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.function.BiConsumer;

/**
 * A small, resolution-independent icon drawn directly with Java2D instead
 * of loaded from a raster/SVG asset - there's no bundled icon set yet (see
 * {@code ui.README.md}), and a hand-drawn vector shape sidesteps that
 * entirely while automatically following the current theme's foreground
 * color (light/dark switch "for free", unlike a fixed-color PNG).
 */
final class VectorIcon implements Icon {

    private final int size;
    private final BiConsumer<Graphics2D, Integer> painter;

    VectorIcon(int size, BiConsumer<Graphics2D, Integer> painter) {
        this.size = size;
        this.painter = painter;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.translate(x, y);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(c.getForeground());
            painter.accept(g2, size);
        } finally {
            g2.dispose();
        }
    }

    @Override
    public int getIconWidth() {
        return size;
    }

    @Override
    public int getIconHeight() {
        return size;
    }
}
