package com.starhunt;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

public class StarhuntOverlay extends OverlayPanel
{
    private final StarhuntPlugin plugin;
    private final StarhuntConfig config;
    private final Client client;

    @Inject
    public StarhuntOverlay(StarhuntPlugin plugin, StarhuntConfig config, Client client)
    {
        super(plugin);
        this.plugin = plugin;
        this.config = config;
        this.client = client;

        setPriority(OverlayPriority.LOW);
        setPosition(OverlayPosition.TOP_LEFT);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        List<StarData> stars = plugin.getNetworkStars();
        if (stars.isEmpty())
        {
            return null;
        }

        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Starhunt Stars")
                .color(Color.GREEN)
                .build());

        // Show the most recent stars first (up to 5)
        int count = 0;
        for (StarData star : stars)
        {
            if (count >= 5)
            {
                break;
            }

            // Skip stars in the current world as they're likely shown by the other plugin
            if (star.getWorld() == client.getWorld())
            {
                continue;
            }

            // Skip stars older than 1 hour
            Duration age = Duration.between(star.getLastUpdate(), Instant.now());
            if (age.toHours() > 1)
            {
                continue;
            }

            String timeAgo = formatTimeAgo(age);
            String starInfo = String.format("W%d T%d %s",
                    star.getWorld(),
                    star.getTier(),
                    star.getLocation());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left(starInfo)
                    .right(timeAgo)
                    .rightColor(getAgeColor(age))
                    .build());

            if (star.getMiners() != null && !star.getMiners().equals(StarData.UNKNOWN_MINERS))
            {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("    Miners:")
                        .right(star.getMiners())
                        .build());
            }

            count++;
        }

        return super.render(graphics);
    }

    private String formatTimeAgo(Duration duration)
    {
        long totalMinutes = duration.toMinutes();

        if (totalMinutes < 1)
        {
            return "Just now";
        }
        else if (totalMinutes < 60)
        {
            return totalMinutes + "m ago";
        }
        else
        {
            long hours = duration.toHours();
            long minutes = totalMinutes % 60;
            return String.format("%dh %dm ago", hours, minutes);
        }
    }

    private Color getAgeColor(Duration age)
    {
        long minutes = age.toMinutes();

        if (minutes < 5)
        {
            return Color.GREEN;
        }
        else if (minutes < 15)
        {
            return Color.YELLOW;
        }
        else if (minutes < 30)
        {
            return Color.ORANGE;
        }
        else
        {
            return Color.RED;
        }
    }
}