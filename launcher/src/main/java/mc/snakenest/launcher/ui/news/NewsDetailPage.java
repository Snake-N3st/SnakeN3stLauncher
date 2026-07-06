package mc.snakenest.launcher.ui.news;

import mc.snakenest.launcher.news.Post;

import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Font;

/** Full content of one news post, matching the mockup's "one prominent article + link to the site" layout. */
public final class NewsDetailPage extends JPanel {

    public NewsDetailPage(Post post, Runnable onOpenOnSite) {
        setLayout(new BorderLayout(0, 12));
        setBorder(new EmptyBorder(24, 24, 24, 24));

        JLabel title = new JLabel(post.title());
        title.setFont(title.getFont().deriveFont(Font.BOLD, 28f));
        add(title, BorderLayout.NORTH);

        JEditorPane content = new JEditorPane("text/html", post.content() == null ? "" : post.content());
        content.setEditable(false);
        content.setOpaque(false);
        content.setBorder(null);
        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(null);
        add(scrollPane, BorderLayout.CENTER);

        JButton openOnSite = new JButton("Voir sur le site");
        openOnSite.addActionListener(e -> onOpenOnSite.run());
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.add(openOnSite, BorderLayout.EAST);
        add(footer, BorderLayout.SOUTH);
    }
}
