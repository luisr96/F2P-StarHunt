package com.starhunt;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.PluginPanel;
import javax.swing.*;
import java.awt.*;
import lombok.extern.slf4j.Slf4j;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class StarhuntPanel extends PluginPanel
{
    private final JPanel starListPanel;
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final Map<String, JPanel> starPanels = new HashMap<>();

    public StarhuntPanel()
    {
        super(false);

        setLayout(new BorderLayout());

        // Title at the top
        JPanel titlePanel = new JPanel(new BorderLayout());
        JLabel title = new JLabel("Active Stars");
        title.setHorizontalAlignment(SwingConstants.CENTER);
        titlePanel.add(title, BorderLayout.CENTER);

        // Refresh button
        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> {
            // TODO: Implement refresh from server
            log.debug("Refresh requested");
        });
        titlePanel.add(refreshBtn, BorderLayout.EAST);

        add(titlePanel, BorderLayout.NORTH);

        // Scrollable list of stars
        starListPanel = new JPanel();
        starListPanel.setLayout(new BoxLayout(starListPanel, BoxLayout.Y_AXIS));

        JScrollPane scrollPane = new JScrollPane(starListPanel);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        add(scrollPane, BorderLayout.CENTER);
    }

    public void addStar(StarData star)
    {
        SwingUtilities.invokeLater(() -> {
            JPanel starEntry = createStarPanel(star);
            starPanels.put(star.getUniqueKey(), starEntry);
            starListPanel.add(starEntry, 0); // Add to top of list
            starListPanel.revalidate();
            starListPanel.repaint();
        });
    }

    public void updateStar(StarData star)
    {
        SwingUtilities.invokeLater(() -> {
            JPanel existingPanel = starPanels.get(star.getUniqueKey());
            if (existingPanel != null)
            {
                // Remove the old panel
                starListPanel.remove(existingPanel);
                // Create and add the updated panel
                JPanel updatedPanel = createStarPanel(star);
                starPanels.put(star.getUniqueKey(), updatedPanel);
                starListPanel.add(updatedPanel, 0);
                starListPanel.revalidate();
                starListPanel.repaint();
            }
            else
            {
                // If we don't have this star, just add it
                addStar(star);
            }
        });
    }

    public void removeStar(String key)
    {
        SwingUtilities.invokeLater(() -> {
            JPanel starPanel = starPanels.remove(key);
            if (starPanel != null)
            {
                starListPanel.remove(starPanel);
                starListPanel.revalidate();
                starListPanel.repaint();
            }
        });
    }

    private JPanel createStarPanel(StarData star)
    {
        JPanel starEntry = new JPanel();
        starEntry.setLayout(new GridLayout(4, 1));
        starEntry.setBorder(BorderFactory.createEtchedBorder());

        JLabel worldLabel = new JLabel("World: " + star.getWorld());

        String locationName = star.getLocationName() != null
                ? star.getLocationName().getName()
                : String.format("Unknown (%d, %d)", star.getLocation().getX(), star.getLocation().getY());

        JLabel locationLabel = new JLabel("Location: " + locationName);
        JLabel tierLabel = new JLabel("Tier: " + star.getTier().getName());

        LocalTime foundTime = LocalTime.ofInstant(star.getFoundTime(), ZoneId.systemDefault());
        JLabel timeLabel = new JLabel("Found: " + foundTime.format(timeFormatter));

        starEntry.add(worldLabel);
        starEntry.add(locationLabel);
        starEntry.add(tierLabel);
        starEntry.add(timeLabel);

        return starEntry;
    }
}