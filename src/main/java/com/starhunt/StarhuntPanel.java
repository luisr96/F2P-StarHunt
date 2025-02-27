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

    // Create simple programmatically-generated icons instead of loading resources
    private static final ImageIcon STAR_ICON = createStarIcon();
    private static final ImageIcon MINERS_ICON = createMinersIcon();
    private static final ImageIcon HEALTH_ICON = createHealthIcon();

    // Create a simple star icon programmatically
    private static ImageIcon createStarIcon() {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw a simple star shape
        g2d.setColor(Color.YELLOW);
        int[] xPoints = {8, 10, 14, 11, 12, 8, 4, 5, 2, 6};
        int[] yPoints = {1, 5, 5, 8, 12, 10, 12, 8, 5, 5};
        g2d.fillPolygon(xPoints, yPoints, 10);

        g2d.dispose();
        return new ImageIcon(image);
    }

    // Create a simple miners (person) icon
    private static ImageIcon createMinersIcon() {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw a simple person shape
        g2d.setColor(Color.WHITE);
        // Head
        g2d.fillOval(6, 2, 5, 5);
        // Body
        g2d.fillRect(7, 7, 3, 5);
        // Arms
        g2d.drawLine(7, 9, 4, 7);
        g2d.drawLine(10, 9, 13, 7);
        // Legs
        g2d.drawLine(7, 12, 5, 15);
        g2d.drawLine(10, 12, 12, 15);

        g2d.dispose();
        return new ImageIcon(image);
    }

    // Create a simple health (heart) icon
    private static ImageIcon createHealthIcon() {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw a simple heart shape
        g2d.setColor(Color.RED);
        g2d.fillArc(3, 3, 5, 5, 0, 180);
        g2d.fillArc(8, 3, 5, 5, 0, 180);
        g2d.fillPolygon(
                new int[]{3, 8, 13, 8},
                new int[]{5, 12, 5, 5},
                4
        );

        g2d.dispose();
        return new ImageIcon(image);
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

            // Add star icon to location
            locationLabel.setIcon(STAR_ICON);
            locationLabel.setText(star.getLocation());
            locationLabel.setForeground(Color.WHITE);
            locationLabel.setFont(FontManager.getRunescapeSmallFont());

            minersLabel.setIcon(MINERS_ICON);
            minersLabel.setText("Miners: " + star.getMiners());
            minersLabel.setForeground(Color.WHITE);
            minersLabel.setFont(FontManager.getRunescapeSmallFont());

            healthLabel.setIcon(HEALTH_ICON);
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