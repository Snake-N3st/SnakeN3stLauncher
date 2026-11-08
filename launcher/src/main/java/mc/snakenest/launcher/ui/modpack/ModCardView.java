package mc.snakenest.launcher.ui.modpack;

import mc.snakenest.launcher.modrinth.ModrinthSearchHit;
import mc.snakenest.launcher.ui.common.AvatarPanel;
import mc.snakenest.launcher.ui.common.Borders;
import mc.snakenest.launcher.ui.common.Buttons;
import mc.snakenest.launcher.ui.common.Colors;
import mc.snakenest.launcher.ui.common.Icons;
import mc.snakenest.launcher.ui.common.ProgressRing;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

/**
 * One row of {@link ModManagerDialog}'s search results - same card shape as
 * {@link ModpackCardView} (logo/name/subtitle + a quick-action icon, clicking the row itself
 * opens the mod's detail page). Incompatible mods aren't hidden, just read visually muted -
 * a player might still want to force-install one, see {@code ModrinthSearchHit#isCompatible}.
 *
 * <p>The quick-action slot is a small {@link CardLayout} between three states: nothing (already
 * installed and up to date - the whole point of a quick-install button, "get this mod", no longer
 * applies), the download icon (not installed, or {@link #setUpdateAvailable} says a newer
 * compatible version exists), and a {@link ProgressRing} while {@link #setInstalling} is in
 * flight - never the download icon at the same time, they're separate cards of the same
 * {@code CardLayout}, not overlaid. The ring itself swaps to a cancel button on hover (see
 * {@link ProgressRing}'s own Javadoc). Which of the first two applies for an already-installed
 * mod isn't known at construction time - that would mean fetching every visible row's version
 * list up front - so it starts hidden (assumed up to date) and {@code ModManagerDialog} flips it
 * on asynchronously once its own per-row check resolves.
 */
final class ModCardView extends JPanel {

    private static final int ACTION_ICON_SIZE = 24;
    private static final int ACTION_BUTTON_SIZE = 40;

    private static final String CARD_EMPTY = "empty";
    private static final String CARD_BUTTON = "button";
    private static final String CARD_PROGRESS = "progress";

    private final AvatarPanel avatar;
    private final CardLayout actionLayout = new CardLayout();
    private final JPanel actionArea = new JPanel(actionLayout);
    private final JButton actionButton;
    private final ProgressRing progressRing;
    private final boolean compatible;
    private boolean installed;
    private boolean updateAvailable;
    private boolean installing;

    ModCardView(ModrinthSearchHit hit, boolean compatible, boolean installed,
                Runnable onOpen, Runnable onQuickInstall) {
        this.installed = installed;
        this.compatible = compatible;
        setLayout(new BorderLayout(16, 0));
        setOpaque(false);
        setBorder(Borders.card());
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        avatar = new AvatarPanel(hit.title(), 48);
        JPanel avatarWrapper = new JPanel(new BorderLayout());
        avatarWrapper.setOpaque(false);
        avatarWrapper.setBorder(new EmptyBorder(0, 10, 0, 0));
        avatarWrapper.add(avatar, BorderLayout.CENTER);
        add(avatarWrapper, BorderLayout.WEST);

        Color mutedColor = UIManager.getColor("Label.disabledForeground");

        JPanel textPanel = new JPanel();
        textPanel.setOpaque(false);
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setBorder(new EmptyBorder(8, 0, 8, 0));

        JPanel titleRow = new JPanel();
        titleRow.setOpaque(false);
        titleRow.setLayout(new BoxLayout(titleRow, BoxLayout.X_AXIS));
        JLabel nameLabel = new JLabel(hit.title());
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 15f));
        titleRow.add(nameLabel);
        if (installed) {
            titleRow.add(Box.createHorizontalStrut(8));
            JLabel installedBadge = new JLabel("Installé");
            installedBadge.setFont(installedBadge.getFont().deriveFont(Font.BOLD, 11f));
            installedBadge.setForeground(UIManager.getColor("Component.accentColor"));
            titleRow.add(installedBadge);
        }
        if (!compatible) {
            titleRow.add(Box.createHorizontalStrut(8));
            JLabel incompatibleBadge = new JLabel("Incompatible");
            incompatibleBadge.setFont(incompatibleBadge.getFont().deriveFont(Font.BOLD, 11f));
            incompatibleBadge.setForeground(Colors.danger());
            titleRow.add(incompatibleBadge);
        }

        JLabel authorLabel = new JLabel("par " + hit.author());
        authorLabel.setForeground(mutedColor);

        JLabel descLabel = new JLabel(truncate(hit.description(), 90));
        descLabel.setForeground(mutedColor);

        // BoxLayout.Y_AXIS centers each child by default (alignmentX 0.5) unless told
        // otherwise - left unset, every row of a different text length centers at a
        // different X, producing a ragged left edge instead of a shared one.
        titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        authorLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        descLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        textPanel.add(Box.createVerticalGlue());
        textPanel.add(titleRow);
        textPanel.add(authorLabel);
        textPanel.add(descLabel);
        textPanel.add(Box.createVerticalGlue());
        add(textPanel, BorderLayout.CENTER);

        if (!compatible) {
            nameLabel.setForeground(mutedColor);
        }

        actionButton = Buttons.roundActionButton(Icons.download(ACTION_ICON_SIZE), "Installation rapide",
                ACTION_BUTTON_SIZE, onQuickInstall);
        progressRing = new ProgressRing(ACTION_BUTTON_SIZE);

        java.awt.Dimension actionSize = new java.awt.Dimension(ACTION_BUTTON_SIZE, ACTION_BUTTON_SIZE);
        JPanel emptyCard = new JPanel();
        emptyCard.setOpaque(false);
        emptyCard.setPreferredSize(actionSize);

        actionArea.setOpaque(false);
        actionArea.add(emptyCard, CARD_EMPTY);
        actionArea.add(actionButton, CARD_BUTTON);
        actionArea.add(progressRing, CARD_PROGRESS);
        applyActionCard();

        JPanel actionWrapper = new JPanel(new GridBagLayout());
        actionWrapper.setOpaque(false);
        actionWrapper.setBorder(new EmptyBorder(0, 0, 0, 12));
        actionWrapper.add(actionArea);
        add(actionWrapper, BorderLayout.EAST);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                onOpen.run();
            }
        });
    }

    /** Set once the icon has loaded (best-effort, async - see {@code ui.common.RemoteImages}). Safe from any thread. */
    void setIcon(BufferedImage icon) {
        SwingUtilities.invokeLater(() -> avatar.setImage(icon));
    }

    /**
     * Flips the button from "not installed" straight to hidden once a quick-install just
     * completed successfully - without this, the button would only disappear the next time the
     * search results are re-rendered (e.g. a fresh search), even though the mod really is now
     * installed. Safe from any thread.
     */
    void setInstalled(boolean installed) {
        SwingUtilities.invokeLater(() -> {
            this.installed = installed;
            this.updateAvailable = false;
            applyActionCard();
        });
    }

    /**
     * Only meaningful when this card was built with {@code installed = true} - reveals the
     * button (relabeled "Mettre à jour") once {@code ModManagerDialog}'s own async per-row check
     * confirms a newer compatible version actually exists. Safe from any thread.
     */
    void setUpdateAvailable(boolean updateAvailable) {
        SwingUtilities.invokeLater(() -> {
            this.updateAvailable = updateAvailable;
            actionButton.setToolTipText(updateAvailable ? "Mettre à jour" : "Installation rapide");
            applyActionCard();
        });
    }

    /** Swaps the action slot to a progress ring while a download for this exact mod is in flight. Safe from any thread. */
    void setInstalling(boolean installing) {
        SwingUtilities.invokeLater(() -> {
            this.installing = installing;
            progressRing.setActive(installing);
            applyActionCard();
        });
    }

    /** 0.0-1.0, or negative while the total download size isn't known yet. Only meaningful while {@link #setInstalling} is active. Safe from any thread. */
    void setDownloadProgress(double progress) {
        progressRing.setProgress(progress);
    }

    /** Wired once at construction - {@code ModManagerDialog} replaces the callback per install via this, not a new {@code ProgressRing}. */
    void setOnCancel(Runnable onCancel) {
        progressRing.setOnCancel(onCancel);
    }

    private void applyActionCard() {
        String card;
        if (installing) {
            card = CARD_PROGRESS;
        } else if (!compatible) {
            // Never offer to install/update an incompatible mod through the quick-action button -
            // see the "Incompatible" badge next to the name instead, which is the only feedback
            // an incompatible mod gets here.
            card = CARD_EMPTY;
        } else if (!installed || updateAvailable) {
            card = CARD_BUTTON;
        } else {
            card = CARD_EMPTY;
        }
        actionLayout.show(actionArea, card);
    }

    private static String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String singleLine = text.replace('\n', ' ').trim();
        return singleLine.length() <= maxLength ? singleLine : singleLine.substring(0, maxLength - 1) + "…";
    }
}
