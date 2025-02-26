package com.starhunt;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.PluginErrorPanel;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.ui.FontManager;

import javax.inject.Inject;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
@Slf4j
public class StarhuntPanel extends PluginPanel {

    private static final ImageIcon STAR_ICON;
    private static final ImageIcon STAR_MINERS_ICON;
    private static final ImageIcon STAR_HEALTH_ICON;

    static {
        final BufferedImage starIcon = ImageUtil.loadImageResource(StarhuntPanel.class, "/star_icon.png");
        STAR_ICON = new ImageIcon(starIcon);

        // Use placeholders for now, replace with actual icons later
        final BufferedImage minersIcon = ImageUtil.loadImageResource(StarhuntPanel.class, "/miners_icon.png");
        STAR_MINERS_ICON = minersIcon != null ? new ImageIcon(minersIcon) : STAR_ICON;

        final BufferedImage healthIcon = ImageUtil.loadImageResource(StarhuntPanel.class, "/health_icon.png");
        STAR_HEALTH_ICON = healthIcon != null ? new ImageIcon(healthIcon) : STAR_ICON;
    }

    private final StarhuntPlugin plugin;
    private final StarhuntConfig config;

    private final JPanel starsContainer = new JPanel();
    private final PluginErrorPanel noStarsPanel = new PluginErrorPanel();

    private final List<StarPanel> starPanels = new ArrayList<>();

    @Inject
    public StarhuntPanel(StarhuntPlugin plugin, StarhuntConfig config) {
        this.plugin = plugin;
        this.config = config;

        setBorder(new EmptyBorder(10, 10, 10, 10));
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setLayout(new BorderLayout());

        // Set up the main container
        starsContainer.setLayout(new BoxLayout(starsContainer, BoxLayout.Y_AXIS));
        starsContainer.setBorder(new EmptyBorder(0, 0, 10, 0));

        // Set up the "no stars" panel
        noStarsPanel.setContent("No stars found", "No shooting stars have been discovered yet.");

        // Add a header
        JPanel header = new JPanel();
        header.setLayout(new BorderLayout());
        header.setBorder(new EmptyBorder(0, 0, 10, 0));

        JLabel title = new JLabel("Star Hunt");
        title.setForeground(Color.WHITE);
        title.setFont(FontManager.getRunescapeBoldFont());
        header.add(title, BorderLayout.WEST);

        add(header, BorderLayout.NORTH);
        add(starsContainer, BorderLayout.CENTER);

        showNoStarsMessage();
    }

    private void showNoStarsMessage() {
        starsContainer.removeAll();
        starsContainer.add(noStarsPanel);
        starsContainer.revalidate();
        starsContainer.repaint();
    }

    public void updateStars(List<StarData> stars) {
        // Ensure we add the Slf4j annotation to the class
        // @Slf4j should be at the top of the StarhuntPanel class

        log.debug("Updating panel with {} stars", stars.size());

        starsContainer.removeAll();
        starPanels.clear();

        if (stars.isEmpty()) {
            log.debug("No stars to display, showing empty message");
            showNoStarsMessage();
            return;
        }

        // Check if there are any active stars
        long activeCount = stars.stream().filter(StarData::isActive).count();
        log.debug("Found {} active stars out of {} total", activeCount, stars.size());

        if (activeCount == 0) {
            log.debug("No active stars, showing empty message");
            showNoStarsMessage();
            return;
        }

        // Sort by last update time (newest first)
        List<StarData> sortedStars = new ArrayList<>(stars);
        sortedStars.sort(Comparator.comparing(StarData::getLastUpdate).reversed());

        int displayedCount = 0;
        for (StarData star : sortedStars) {
            if (star.isActive()) {
                log.debug("Adding star panel for: W{} T{} at {}",
                        star.getWorld(), star.getTier(), star.getLocation());
                StarPanel panel = new StarPanel(star);
                starPanels.add(panel);
                starsContainer.add(panel);
                displayedCount++;
            }
        }

        log.debug("Added {} star panels to the container", displayedCount);

        starsContainer.revalidate();
        starsContainer.repaint();
    }

    private class StarPanel extends JPanel {
        private final JLabel worldLabel = new JLabel();
        private final JLabel tierLabel = new JLabel();
        private final JLabel locationLabel = new JLabel();
        private final JLabel minersLabel = new JLabel();
        private final JLabel healthLabel = new JLabel();
        private final JLabel timeLabel = new JLabel();
        private final JProgressBar healthBar = new JProgressBar(0, 100);
        private final StarData star;

        StarPanel(StarData star) {
            this.star = star;

            setLayout(new BorderLayout());
            setBorder(new EmptyBorder(5, 0, 5, 0));

            JPanel headerPanel = new JPanel();
            headerPanel.setLayout(new BorderLayout());

            // World and tier
            JPanel worldTierPanel = new JPanel();
            worldTierPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 0));
            worldTierPanel.setOpaque(false);

            worldLabel.setText("W" + star.getWorld());
            worldLabel.setForeground(Color.WHITE);
            worldLabel.setFont(FontManager.getRunescapeBoldFont());

            tierLabel.setText("T" + star.getTier());
            tierLabel.setForeground(getTierColor(star.getTier()));
            tierLabel.setFont(FontManager.getRunescapeBoldFont());

            worldTierPanel.add(worldLabel);
            worldTierPanel.add(tierLabel);

            // Last update time
            timeLabel.setText(formatTimeAgo(Duration.between(star.getLastUpdate(), Instant.now())));
            timeLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            timeLabel.setFont(FontManager.getRunescapeSmallFont());

            headerPanel.add(worldTierPanel, BorderLayout.WEST);
            headerPanel.add(timeLabel, BorderLayout.EAST);

            // Location, miners, health
            JPanel detailsPanel = new JPanel();
            detailsPanel.setLayout(new DynamicGridLayout(3, 1, 0, 2));
            detailsPanel.setBorder(new EmptyBorder(5, 0, 5, 0));
            detailsPanel.setOpaque(false);

            locationLabel.setText(star.getLocation());
            locationLabel.setForeground(Color.WHITE);
            locationLabel.setFont(FontManager.getRunescapeSmallFont());

            minersLabel.setIcon(STAR_MINERS_ICON);
            minersLabel.setText("Miners: " + star.getMiners());
            minersLabel.setForeground(Color.WHITE);
            minersLabel.setFont(FontManager.getRunescapeSmallFont());

            healthLabel.setIcon(STAR_HEALTH_ICON);
            int health = star.getHealth() >= 0 ? star.getHealth() : 0;
            healthLabel.setText("Health: " + health + "%");
            healthLabel.setForeground(getHealthColor(health));
            healthLabel.setFont(FontManager.getRunescapeSmallFont());

            detailsPanel.add(locationLabel);
            detailsPanel.add(minersLabel);
            detailsPanel.add(healthLabel);

            // Health bar
            healthBar.setValue(health);
            healthBar.setStringPainted(false);
            healthBar.setForeground(getHealthColor(health));
            healthBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            healthBar.setBorder(new EmptyBorder(0, 0, 5, 0));

            add(headerPanel, BorderLayout.NORTH);
            add(detailsPanel, BorderLayout.CENTER);
            add(healthBar, BorderLayout.SOUTH);

            // Set background color
            setBackground(ColorScheme.DARKER_GRAY_COLOR);
        }

        private Color getTierColor(int tier) {
            switch (tier) {
                case 1:
                    return Color.RED;
                case 2:
                    return new Color(255, 127, 0); // Orange
                case 3:
                    return Color.YELLOW;
                case 4:
                    return new Color(127, 255, 0); // Light green
                case 5:
                case 6:
                case 7:
                case 8:
                case 9:
                    return Color.GREEN;
                default:
                    return Color.WHITE;
            }
        }

        private Color getHealthColor(int health) {
            if (health <= 25) {
                return Color.RED;
            } else if (health <= 50) {
                return Color.YELLOW;
            } else {
                return Color.GREEN;
            }
        }
    }

    private String formatTimeAgo(Duration duration) {
        long totalMinutes = duration.toMinutes();

        if (totalMinutes < 1) {
            return "Just now";
        } else if (totalMinutes < 60) {
            return totalMinutes + "m ago";
        } else {
            long hours = duration.toHours();
            long minutes = totalMinutes % 60;
            return String.format("%dh %dm ago", hours, minutes);
        }
    }
}