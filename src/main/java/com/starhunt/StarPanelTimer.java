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
}