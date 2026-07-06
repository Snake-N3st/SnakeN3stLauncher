package mc.snakenest.launcher.ui.news;

import mc.snakenest.launcher.news.Post;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/** Scrollable list of news posts - click one to read it in full. */
public final class NewsListPage extends JPanel {

    public NewsListPage(NewsListViewModel viewModel) {
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(16, 16, 16, 16));

        if (viewModel.posts().isEmpty()) {
            add(new JLabel("Aucune actualite pour le moment.", SwingConstants.CENTER), BorderLayout.CENTER);
            return;
        }

        JPanel list = new JPanel();
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));

        for (Post post : viewModel.posts()) {
            JPanel card = postCard(post, () -> viewModel.select(post));
            card.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 96));
            list.add(card);
            list.add(Box.createVerticalStrut(12));
        }

        JScrollPane scrollPane = new JScrollPane(list);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);
    }

    private JPanel postCard(Post post, Runnable onOpen) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBorder(new CompoundBorder(new EmptyBorder(4, 4, 4, 4),
                BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor"), 1, true)));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.setBorder(new EmptyBorder(12, 16, 12, 16));

        JLabel title = new JLabel(post.title());
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        text.add(title);

        if (post.description() != null && !post.description().isBlank()) {
            JLabel description = new JLabel(post.description());
            description.setForeground(UIManager.getColor("Label.disabledForeground"));
            text.add(description);
        }

        card.add(text, BorderLayout.CENTER);

        MouseAdapter openOnClick = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                onOpen.run();
            }
        };
        card.addMouseListener(openOnClick);
        title.addMouseListener(openOnClick);

        return card;
    }
}
