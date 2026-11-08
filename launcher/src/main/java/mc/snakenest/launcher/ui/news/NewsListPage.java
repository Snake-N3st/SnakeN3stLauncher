package mc.snakenest.launcher.ui.news;

import mc.snakenest.launcher.news.Post;
import mc.snakenest.launcher.ui.common.Colors;
import mc.snakenest.launcher.ui.common.RemoteImages;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.StringReader;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

/** Editorial news feed; articles open directly in the user's browser. */
public final class NewsListPage extends JPanel {
    private static final Color GREEN = new Color(81, 174, 54);
    private static final Map<String, BufferedImage> IMAGES = Collections.synchronizedMap(
            new LinkedHashMap<>(32, .75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> entry) { return size() > 24; }
            });

    public NewsListPage(NewsListViewModel viewModel) {
        super(new BorderLayout());
        setBorder(new EmptyBorder(24, 28, 16, 28));
        JPanel header = new JPanel(new BorderLayout(12, 6));
        header.setOpaque(false);
        JLabel title = new JLabel("Actualités");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 29f));
        header.add(title, BorderLayout.NORTH);
        JLabel subtitle = new JLabel("La vie du serveur, les nouveautés et les prochains rendez-vous.");
        subtitle.setForeground(Colors.muted());
        header.add(subtitle, BorderLayout.CENTER);
        header.setBorder(new EmptyBorder(0, 0, 22, 0));
        add(header, BorderLayout.NORTH);

        if (viewModel.posts().isEmpty()) {
            JPanel empty = new JPanel(new GridBagLayout()); empty.setOpaque(false);
            JLabel message = new JLabel("Les prochaines nouvelles du serveur apparaîtront ici.");
            message.setForeground(Colors.muted()); empty.add(message);
            add(empty); return;
        }
        JPanel feed = new Feed(); feed.setOpaque(false);
        feed.setLayout(new BoxLayout(feed, BoxLayout.Y_AXIS));
        List<Post> posts = new ArrayList<>(viewModel.posts());
        posts.sort(Comparator.comparing((Post post) -> published(post.publishedAt())).reversed());
        for (int index = 0; index < posts.size(); index++) {
            if (index == 1) {
                JLabel label = new JLabel("Toutes les nouvelles");
                label.setFont(label.getFont().deriveFont(Font.BOLD, 17f));
                label.setBorder(new EmptyBorder(12, 0, 14, 0));
                label.setAlignmentX(Component.LEFT_ALIGNMENT); feed.add(label);
            }
            Post post = posts.get(index);
            Article card = new Article(post, index == 0, () -> viewModel.select(post));
            card.setAlignmentX(Component.LEFT_ALIGNMENT);
            feed.add(card); feed.add(Box.createVerticalStrut(14));
        }
        feed.add(Box.createVerticalGlue());
        JScrollPane scroll = new JScrollPane(feed);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        scroll.getViewport().setOpaque(false); scroll.setOpaque(false);
        add(scroll);
    }

    private static Instant published(String value) {
        try { return OffsetDateTime.parse(value).toInstant(); }
        catch (Exception ignored) { return Instant.MIN; }
    }
    private static String metadata(Post post) {
        String date = "";
        try { date = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH).format(OffsetDateTime.parse(post.publishedAt())); }
        catch (Exception ignored) { }
        String author = post.author() == null ? "" : plain(post.author().name());
        return date.isBlank() ? author : author.isBlank() ? date : date + "   ·   " + author;
    }
    private static String plain(String html) {
        if (html == null || html.isBlank()) return "";
        StringBuilder text = new StringBuilder();
        try {
            new ParserDelegator().parse(new StringReader(html), new HTMLEditorKit.ParserCallback() {
                @Override public void handleText(char[] data, int position) { text.append(data).append(' '); }
            }, true);
        } catch (Exception ignored) { return html.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim(); }
        return text.toString().replace("**", "").replace("__", "").replaceAll("\\s+", " ").trim();
    }

    private static final class Feed extends JPanel implements Scrollable {
        Feed() {
            addComponentListener(new java.awt.event.ComponentAdapter() {
                @Override public void componentResized(java.awt.event.ComponentEvent event) { revalidate(); }
            });
        }
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return 24; }
        @Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) { return Math.max(24, visible.height - 48); }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    private static final class Article extends JButton {
        private final boolean featured;
        private final String title, description, metadata;
        private BufferedImage image;
        Article(Post post, boolean featured, Runnable open) {
            this.featured = featured;
            title = plain(post.title()); description = plain(post.description()); metadata = metadata(post);
            setText(title); setToolTipText("Lire l’article dans le navigateur");
            getAccessibleContext().setAccessibleName("Lire l’article : " + title);
            getAccessibleContext().setAccessibleDescription(metadata + ". " + description);
            setContentAreaFilled(false); setBorderPainted(false); setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addActionListener(event -> open.run());
            if (post.image() != null && !post.image().isBlank()) {
                image = IMAGES.get(post.image());
                if (image == null) new SwingWorker<BufferedImage, Void>() {
                    @Override protected BufferedImage doInBackground() { return RemoteImages.tryLoad(post.image()); }
                    @Override protected void done() {
                        try { image = get(); if (image != null) IMAGES.put(post.image(), image); repaint(); }
                        catch (Exception ignored) { /* Keep the artwork-free cover. */ }
                    }
                }.execute();
            }
        }
        @Override public Dimension getPreferredSize() {
            int width = getParent() == null ? 900 : getParent().getWidth();
            return new Dimension(640, featured ? width < 640 ? 418 : 306 : 172);
        }
        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        @Override public Dimension getMinimumSize() { return new Dimension(0, getPreferredSize().height); }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                boolean dark = getBackground().getRed() + getBackground().getGreen() + getBackground().getBlue() < 400;
                Color surface = dark ? new Color(37, 41, 39) : Color.WHITE;
                Color foreground = UIManager.getColor("Label.foreground");
                if (getModel().isRollover()) surface = dark ? new Color(43, 49, 44) : new Color(246, 250, 245);
                Shape round = new RoundRectangle2D.Float(1, 1, w - 2, h - 2, 22, 22);
                g.setColor(surface); g.fill(round);
                g.clip(round);
                boolean stacked = featured && w < 640;
                int coverWidth = featured ? stacked ? w : Math.max(220, (int) (w * .43)) : w < 500 ? 0 : 168;
                int coverHeight = stacked ? 172 : h;
                if (coverWidth > 0) paintCover(g, coverWidth, coverHeight);
                int x = stacked ? 24 : coverWidth + 26, y = stacked ? coverHeight + 24 : featured ? 32 : 24;
                int textWidth = w - x - 26;
                g.setColor(dark ? new Color(133, 212, 108) : GREEN.darker());
                g.setFont(getFont().deriveFont(Font.BOLD, 11f));
                g.drawString(featured ? "À LA UNE" : "ACTUALITÉ", x, y); y += featured ? 32 : 27;
                g.setColor(foreground);
                g.setFont(getFont().deriveFont(Font.BOLD, featured ? stacked ? 22f : 26f : 18f));
                y = lines(g, title, x, y, textWidth, featured && !stacked ? 3 : 2) + 10;
                g.setColor(Colors.muted()); g.setFont(getFont().deriveFont(13f));
                if (featured) y = lines(g, description, x, y, textWidth, stacked ? 2 : 3);
                else if (!description.isBlank() && y < h - 55) lines(g, description, x, y, textWidth, 1);
                int bottom = h - 25;
                g.setFont(getFont().deriveFont(11f)); g.setColor(Colors.muted());
                if (featured) {
                    lines(g, metadata, x, bottom - 34, textWidth, 1);
                    g.setColor(dark ? new Color(133, 212, 108) : GREEN.darker());
                    g.setFont(getFont().deriveFont(Font.BOLD, 13f)); g.drawString("Lire l’article  ↗", x, bottom);
                } else {
                    lines(g, metadata, x, bottom, Math.max(30, textWidth - 135), 1);
                    g.setColor(dark ? new Color(133, 212, 108) : GREEN.darker());
                    g.setFont(getFont().deriveFont(Font.BOLD, 12f));
                    g.drawString("Lire l’article  ↗", w - 139, bottom);
                }
                g.setClip(null); g.setColor(isFocusOwner() ? GREEN : dark ? new Color(57, 64, 59) : new Color(221, 229, 219));
                g.setStroke(new BasicStroke(isFocusOwner() ? 2 : 1)); g.draw(round);
            } finally { g.dispose(); }
        }
        private void paintCover(Graphics2D g, int width, int height) {
            Shape old = g.getClip(); g.clipRect(0, 0, width, height);
            if (image != null) {
                double scale = Math.max(width / (double) image.getWidth(), height / (double) image.getHeight());
                int iw = (int) Math.ceil(image.getWidth() * scale), ih = (int) Math.ceil(image.getHeight() * scale);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(image, (width - iw) / 2, (height - ih) / 2, iw, ih, null);
            } else {
                g.setPaint(new GradientPaint(0, 0, new Color(21, 54, 36), width, height, new Color(51, 91, 43)));
                g.fillRect(0, 0, width, height);
                g.setColor(new Color(151, 208, 119, 35));
                g.setStroke(new BasicStroke(1.3f));
                for (int offset = -height; offset < width + height; offset += 42) g.drawLine(offset, 0, offset + height, height);
                g.setColor(new Color(225, 244, 214));
                g.setFont(getFont().deriveFont(Font.BOLD, featured ? 23f : 16f));
                g.drawString("SNAKE", 24, height / 2 - 2); g.drawString("N3ST", 24, height / 2 + 25);
            }
            g.setClip(old);
        }
        private int lines(Graphics2D g, String value, int x, int y, int width, int limit) {
            if (value.isBlank() || width < 1) return y;
            FontMetrics metrics = g.getFontMetrics();
            String remaining = value;
            for (int line = 0; line < limit && !remaining.isBlank(); line++) {
                int end = remaining.length();
                while (end > 0 && metrics.stringWidth(remaining.substring(0, end)) > width) end--;
                if (end == 0) return y;
                if (end < remaining.length()) {
                    if (line == limit - 1) {
                        while (end > 0 && metrics.stringWidth(remaining.substring(0, end) + "…") > width) end--;
                        g.drawString(remaining.substring(0, end).stripTrailing() + "…", x, y); return y + metrics.getHeight();
                    }
                    int space = remaining.lastIndexOf(' ', end); if (space > 0) end = space;
                }
                g.drawString(remaining.substring(0, end), x, y);
                remaining = remaining.substring(end).stripLeading(); y += metrics.getHeight();
            }
            return y;
        }
    }
}
