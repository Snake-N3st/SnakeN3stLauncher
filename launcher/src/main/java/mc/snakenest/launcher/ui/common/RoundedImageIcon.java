package mc.snakenest.launcher.ui.common;

import javax.swing.Icon;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * Renders a {@link BufferedImage} clipped to a rounded square, sized to fit
 * a button/label icon slot - used for the account avatar (see
 * {@code ui.TopBar}/{@code ui.account.AccountPopover}), which unlike
 * {@code ui.LogoPanel} is a plain {@link Icon} rather than its own
 * component (it just replaces a button's icon).
 *
 * <p>Square, not circular: Azuriom sites conventionally show square
 * avatars (a player's Minecraft head render is square), so clipping to a
 * circle here would look inconsistent with the site the player came from.
 *
 * <p>Doesn't assume the source image itself is exactly square, though -
 * force-stretching a slightly-non-square avatar (a custom upload, a
 * differently-cropped provider, etc.) to fill {@code size x size} distorts
 * it and can make it read as off-center even though the icon's own bounds
 * are centered. Scales preserving aspect ratio and centers the result
 * within the square instead (same "contain, don't stretch" rule already
 * applied to `&lt;img&gt;` tags on the site side - see CONTEXT.md).
 */
public final class RoundedImageIcon implements Icon {

    private final BufferedImage image;
    private final int size;

    public RoundedImageIcon(BufferedImage image, int size) {
        this.image = image;
        this.size = size;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.clip(new RoundRectangle2D.Float(x, y, size, size, size / 5f, size / 5f));

            double scale = Math.min((double) size / image.getWidth(), (double) size / image.getHeight());
            int drawW = (int) Math.round(image.getWidth() * scale);
            int drawH = (int) Math.round(image.getHeight() * scale);
            int drawX = x + (size - drawW) / 2;
            int drawY = y + (size - drawH) / 2;
            g2.drawImage(image, drawX, drawY, drawW, drawH, null);
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
