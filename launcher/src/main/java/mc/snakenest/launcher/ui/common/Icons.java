package mc.snakenest.launcher.ui.common;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;

/**
 * Hand-drawn (Java2D) stand-ins for a real bundled icon set. Generic UI
 * glyphs (play/document/gear/back-arrow/account/folder/download) are fine
 * as permanent placeholders - they're original shapes, not a copy of
 * anything.
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
            // Vertical extent (top/bottom below) is symmetric around 0.5*s on purpose - the
            // previous version spanned 0.32..0.917 (center ~0.62), which read as noticeably
            // low/off-center within the icon's own canvas.
            float top = s * 0.2f;
            float bottom = s * 0.8f;
            float tabTop = s * 0.28f;
            Path2D folder = new Path2D.Float();
            folder.moveTo(pad, top);
            folder.lineTo(pad, bottom);
            folder.lineTo(s - pad, bottom);
            folder.lineTo(s - pad, tabTop);
            folder.lineTo(s / 2.0, tabTop);
            folder.lineTo(s * 0.4, top);
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

    /**
     * "⭮" (U+2B6E, CLOCKWISE TRIANGLE-HEADED OPEN CIRCLE ARROW) - the topbar's "Actualiser"
     * button.
     *
     * <p>Three different hand-drawn vector attempts (an open chevron on an arc, a filled
     * triangular arrowhead on an arc, then two point-symmetric arrow+arc pairs) all read as an
     * ambiguous blob/hook rather than a recognizable "circular arrow" at toolbar sizes
     * (~20-24px) - there just isn't enough room in a handful of polyline segments at that pixel
     * count to carry the shape clearly. A real font's hinting/rendering at small sizes does a
     * much better job of this exact problem than a few dozen manually-plotted points can, so
     * this one glyph is drawn via {@link Graphics2D#drawString} instead of {@link Path2D} - the
     * one icon in this class that isn't a Java2D shape (see the class Javadoc). Falls back to
     * whatever glyph the platform's font substitution finds for this codepoint if the exact
     * "open circle, triangle head" glyph isn't in any installed font - confirmed present via
     * logical font fallback (Dialog/SansSerif/Serif) on this dev machine (Symbola/Noto Sans
     * Symbols2), not verified on Windows/macOS.
     */
    public static Icon refresh(int size) {
        return icon(size, (g, s) -> {
            String glyph = "⭮";
            g.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, Math.round(s * 0.92f)));
            java.awt.FontMetrics metrics = g.getFontMetrics();
            int x = (s - metrics.stringWidth(glyph)) / 2;
            int y = (s - metrics.getHeight()) / 2 + metrics.getAscent();
            g.drawString(glyph, x, y);
        });
    }

    /** A simple X mark - the detail page's/quick-action's "Annuler" (cancel) state. */
    public static Icon cancel(int size) {
        return icon(size, (g, s) -> {
            g.setStroke(stroke(s));
            int pad = s / 4;
            g.drawLine(pad, pad, s - pad, s - pad);
            g.drawLine(s - pad, pad, pad, s - pad);
        });
    }

    /** A filled rounded square - the detail page's/quick-action's "Arrêter" (stop) state. */
    public static Icon stop(int size) {
        return icon(size, (g, s) -> {
            int pad = s / 4;
            g.fill(new java.awt.geom.RoundRectangle2D.Float(pad, pad, s - pad * 2, s - pad * 2, s / 6f, s / 6f));
        });
    }

    /**
     * A simple skull - the detail page's/quick-action's "Tuer" (force-kill) state, shown after
     * "Arrêter" has already been clicked once (see {@code ModpackDetailPage.ButtonState#STOPPING}).
     * Outlined (not filled) like {@code gear()}/{@code folder()} so the eye sockets/nose read as
     * solid dots against it without needing to know the surrounding background color.
     */
    public static Icon skull(int size) {
        return icon(size, (g, s) -> {
            g.setStroke(stroke(s));
            double cx = s / 2.0;
            float craniumWidth = s * 0.64f;
            float craniumX = (s - craniumWidth) / 2f;
            float craniumTop = s * 0.12f;
            float craniumHeight = s * 0.46f;
            g.draw(new java.awt.geom.RoundRectangle2D.Float(craniumX, craniumTop, craniumWidth, craniumHeight,
                    craniumWidth * 0.7f, craniumWidth * 0.7f));

            Path2D jaw = new Path2D.Float();
            jaw.moveTo(s * 0.32, s * 0.5);
            jaw.lineTo(s * 0.32, s * 0.68);
            jaw.quadTo(cx, s * 0.8, s * 0.68, s * 0.68);
            jaw.lineTo(s * 0.68, s * 0.5);
            g.draw(jaw);

            double eyeR = s * 0.07;
            g.fill(new Ellipse2D.Double(s * 0.36 - eyeR, s * 0.34 - eyeR, eyeR * 2, eyeR * 2));
            g.fill(new Ellipse2D.Double(s * 0.64 - eyeR, s * 0.34 - eyeR, eyeR * 2, eyeR * 2));

            Path2D nose = new Path2D.Float();
            nose.moveTo(cx - s * 0.05, s * 0.44);
            nose.lineTo(cx + s * 0.05, s * 0.44);
            nose.lineTo(cx, s * 0.52);
            nose.closePath();
            g.fill(nose);

            float toothY1 = s * 0.68f;
            float toothY2 = s * 0.75f;
            g.draw(new java.awt.geom.Line2D.Float(s * 0.44f, toothY1, s * 0.44f, toothY2));
            g.draw(new java.awt.geom.Line2D.Float((float) cx, toothY1, (float) cx, toothY2));
            g.draw(new java.awt.geom.Line2D.Float(s * 0.56f, toothY1, s * 0.56f, toothY2));
        });
    }

    private static BasicStroke stroke(int size) {
        return new BasicStroke(Math.max(1.5f, size / 10f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    }

    private static Icon icon(int size, java.util.function.BiConsumer<Graphics2D, Integer> painter) {
        return new VectorIcon(size, painter);
    }
}
