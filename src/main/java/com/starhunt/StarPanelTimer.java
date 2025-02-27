package com.starhunt;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.task.Schedule;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Handles periodic updates to the star panel
 */
@Slf4j
@Singleton
public class StarPanelTimer {

    private final StarhuntPlugin plugin;
    private final ClientThread clientThread;
    private StarhuntPanel panel;

    @Inject
    public StarPanelTimer(StarhuntPlugin plugin, ClientThread clientThread) {
        this.plugin = plugin;
        this.clientThread = clientThread;
    }

    /**
     * Set the panel instance to use for updates
     */
    public void setPanel(StarhuntPanel panel) {
        this.panel = panel;
    }

    /**
     * Update the panel every 5 seconds to refresh time displays and status
     */
    @Schedule(
            period = 5,
            unit = ChronoUnit.SECONDS
    )
    public void updatePanel() {
        if (panel != null) {
            clientThread.invokeLater(() -> {
                List<StarData> stars = plugin.getNetworkStars();
                log.debug("Updating panel with {} stars", stars.size());
                panel.updateStars(stars);
            });
        } else {
            log.debug("Panel is null, cannot update");
        }
    }

    /**
     * Update just the health displays more frequently
     */
    @Schedule(
            period = 1,
            unit = ChronoUnit.SECONDS
    )
    public void updateHealth() {
        if (panel != null && plugin.getClient().getGameState().getState() >= net.runelite.api.GameState.LOADING.getState()) {
            clientThread.invokeLater(() -> {
                // Check if we need to update any local stars
                for (StarData star : plugin.getStars()) {
                    // Force a health update for active stars we're close to
                    if (star.isActive() && star.isNearby(plugin.getClient().getLocalPlayer().getWorldLocation(), 32)) {
                        int newHealth = star.getHealth();
                        if (newHealth >= 0) {
                            // Find matching network star and update health
                            for (StarData networkStar : plugin.getNetworkStars()) {
                                if (networkStar.getWorld() == star.getWorld() &&
                                        networkStar.getWorldPoint().equals(star.getWorldPoint())) {
                                    if (networkStar.getHealth() != newHealth) {
                                        networkStar.setHealth(newHealth);
                                        // Update panel immediately
                                        panel.updateStars(plugin.getNetworkStars());
                                    }
                                    break;
                                }
                            }
                        }
                    }
                }
            });
        }
    }
}