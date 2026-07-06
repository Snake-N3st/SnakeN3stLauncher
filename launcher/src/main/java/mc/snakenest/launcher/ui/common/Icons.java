package mc.snakenest.launcher.ui.common;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Hand-drawn (Java2D) stand-ins for a real bundled icon set. Generic UI
 * glyphs (play/document/gear/back-arrow/account/folder/download) are fine
 * as permanent placeholders - they're original shapes, not a copy of
 * anything. {@link #twitch} and {@link #discord} are deliberately generic
 * ("screen" / "chat bubble") rather than an attempt at the real trademarked
 * logos - swapping in the real Simple Icons marks (CC0) is a follow-up, not
 * done here.
 */
public final class Icons {

    private Icons() {
    }

    public static Icon play(int size) {
        return icon(size, (g, s) -> {
            int pad = s / 4;
            Path2D triangle = new Path2D.Float();
            triangle.moveTo(pad, pad * 0.7);
            triangle.lineTo(pad, s - pad * 0.7);
            triangle.lineTo(s - pad * 0.6, s / 2.0);
            triangle.closePath();
            g.fill(triangle);
        });
    }

    public static Icon document(int size) {
        return icon(size, (g, s) -> {
            int pad = s / 5;
            g.setStroke(stroke(s));
            g.drawRoundRect(pad, pad / 2, s - pad * 2, s - pad, s / 6, s / 6);
            int lineY1 = s / 2 - s / 10;
            int lineY2 = s / 2 + s / 6;
            g.drawLine(pad + s / 8, lineY1, s - pad - s / 8, lineY1);
            g.drawLine(pad + s / 8, lineY2, s - pad - s / 8, lineY2);
        });
    }

    public static Icon gear(int size) {
        return icon(size, (g, s) -> {
            g.setStroke(stroke(s));
            double cx = s / 2.0;
            double cy = s / 2.0;
            double outer = s * 0.42;
            double inner = s * 0.24;
            double toothLen = s * 0.12;
            int teeth = 8;
            for (int i = 0; i < teeth; i++) {
                double angle = (Math.PI * 2 * i) / teeth;
                double x1 = cx + Math.cos(angle) * outer;
                double y1 = cy + Math.sin(angle) * outer;
                double x2 = cx + Math.cos(angle) * (outer + toothLen);
                double y2 = cy + Math.sin(angle) * (outer + toothLen);
                g.draw(new java.awt.geom.Line2D.Double(x1, y1, x2, y2));
            }
            g.draw(new Ellipse2D.Double(cx - outer, cy - outer, outer * 2, outer * 2));
            g.draw(new Ellipse2D.Double(cx - inner, cy - inner, inner * 2, inner * 2));
        });
    }

    public static Icon backArrow(int size) {
        return icon(size, (g, s) -> {
            g.setStroke(stroke(s));
            int mid = s / 2;
            Path2D chevron = new Path2D.Float();
            chevron.moveTo(s * 0.62, s * 0.22);
            chevron.lineTo(s * 0.32, mid);
            chevron.lineTo(s * 0.62, s * 0.78);
            g.draw(chevron);
        });
    }

    public static Icon userCircle(int size) {
        return icon(size, (g, s) -> {
            g.setStroke(stroke(s));
            double outer = s * 0.42;
            g.draw(new Ellipse2D.Double(s / 2.0 - outer, s / 2.0 - outer, outer * 2, outer * 2));
            double headR = s * 0.14;
            g.draw(new Ellipse2D.Double(s / 2.0 - headR, s * 0.30, headR * 2, headR * 2));
            Path2D shoulders = new Path2D.Float();
            shoulders.moveTo(s * 0.28, s * 0.76);
            shoulders.curveTo(s * 0.28, s * 0.56, s * 0.72, s * 0.56, s * 0.72, s * 0.76);
            g.draw(shoulders);
        });
    }

    public static Icon folder(int size) {
        return icon(size, (g, s) -> {
            g.setStroke(stroke(s));
            int pad = s / 6;
            Path2D folder = new Path2D.Float();
            folder.moveTo(pad, s * 0.32);
            folder.lineTo(pad, s - pad / 2.0);
            folder.lineTo(s - pad, s - pad / 2.0);
            folder.lineTo(s - pad, s * 0.4);
            folder.lineTo(s / 2.0, s * 0.4);
            folder.lineTo(s * 0.4, s * 0.32);
            folder.closePath();
            g.draw(folder);
        });
    }

    public static Icon download(int size) {
        return icon(size, (g, s) -> {
            g.setStroke(stroke(s));
            int mid = s / 2;
            g.drawLine(mid, s / 5, mid, s * 2 / 3);
            Path2D arrowHead = new Path2D.Float();
            arrowHead.moveTo(s * 0.32, s * 0.5);
            arrowHead.lineTo(mid, s * 2 / 3.0);
            arrowHead.lineTo(s * 0.68, s * 0.5);
            g.draw(arrowHead);
            g.drawLine(s / 5, s * 5 / 6, s * 4 / 5, s * 5 / 6);
        });
    }

    /** Generic "screen" glyph, not the real Twitch mark - see class Javadoc. */
    public static Icon twitch(int size) {
        return icon(size, (g, s) -> {
            g.setStroke(stroke(s));
            int pad = s / 6;
            g.draw(new RoundRectangle2D.Float(pad, pad, s - pad * 2f, s * 0.62f, s / 8f, s / 8f));
            g.drawLine(s / 2, (int) (pad + s * 0.62), s / 2, s - pad / 2);
            g.drawLine(s / 3, s - pad / 2, s * 2 / 3, s - pad / 2);
        });
    }

    /** Generic "chat bubble" glyph, not the real Discord mark - see class Javadoc. */
    public static Icon discord(int size) {
        return icon(size, (g, s) -> {
            g.setStroke(stroke(s));
            int pad = s / 6;
            RoundRectangle2D bubble = new RoundRectangle2D.Float(pad, pad, s - pad * 2f, s * 0.68f, s / 4f, s / 4f);
            g.draw(bubble);
            Path2D tail = new Path2D.Float();
            tail.moveTo(s * 0.32, pad + s * 0.68);
            tail.lineTo(s * 0.28, s - pad / 2.0);
            tail.lineTo(s * 0.48, pad + s * 0.68);
            g.draw(tail);
        });
    }

    private static BasicStroke stroke(int size) {
        return new BasicStroke(Math.max(1.5f, size / 10f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    }

    private static Icon icon(int size, java.util.function.BiConsumer<Graphics2D, Integer> painter) {
        return new VectorIcon(size, painter);
    }
}
