package com.starhunt;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import java.awt.image.BufferedImage;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.Getter;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
		name = "Starhunt",
		description = "Shares shooting star locations with other players",
		tags = {"shooting", "star", "mining", "share"}
)
public class StarhuntPlugin extends Plugin
{
	private static final int NPC_ID = NullNpcID.NULL_10629;
	private static final int MAX_RECONNECT_ATTEMPTS = 5;
	private static final int RECONNECT_DELAY_MS = 5000;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private StarhuntConfig config;

	@Inject
	private StarhuntSocketManager socketManager;

	@Inject
	private StarhuntOverlay overlay;

	@Inject
	private OverlayManager overlayManager;

	// Add these fields to StarhuntPlugin class
	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private StarPanelTimer starPanelTimer;

	private NavigationButton navButton;
	private StarhuntPanel starhuntPanel;

	// Stars that we've discovered locally
	private final List<StarData> stars = new ArrayList<>();

	// Stars received from the network
	@Getter
	private final List<StarData> networkStars = new ArrayList<>();

	private int reconnectAttempts = 0;
	private boolean connected = false;

	@Provides
	StarhuntConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(StarhuntConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		log.info("Starhunt plugin starting up");

		// Connect to server first
		socketManager.registerListener(this);
		connectToServer();

		// Add overlay
		overlayManager.add(overlay);
		log.debug("Added overlay to manager");

		// Create panel
		starhuntPanel = new StarhuntPanel(this, config);
		log.debug("Created StarhuntPanel instance");

		// Load icon for navigation button
		final BufferedImage icon = ImageUtil.loadImageResource(StarhuntPlugin.class, "/star_icon.png");
		if (icon == null) {
			log.warn("Could not load star icon resource");
		}

		// Create navigation button
		navButton = NavigationButton.builder()
				.tooltip("Star Hunt")
				.icon(icon != null ? icon : ImageUtil.getResourceStreamFromClass(StarhuntPlugin.class, "star_icon.png"))
				.priority(5)
				.panel(starhuntPanel)
				.build();
		log.debug("Created navigation button");

		// Add to toolbar
		clientToolbar.addNavigation(navButton);
		log.debug("Added navigation button to toolbar");

		// Set the panel in the timer
		if (starPanelTimer != null) {
			starPanelTimer.setPanel(starhuntPanel);
			log.debug("Set panel in timer");
		} else {
			log.warn("starPanelTimer is null, cannot set panel");
		}

		// Initial update with any existing stars
		if (!networkStars.isEmpty()) {
			log.debug("Performing initial panel update with {} existing stars", networkStars.size());
			starhuntPanel.updateStars(networkStars);
		} else {
			log.debug("No existing stars for initial panel update");
		}

		log.info("Starhunt plugin started successfully");
	}

	@Override
	protected void shutDown() throws Exception
	{
		socketManager.unregisterListener(this);
		socketManager.disconnect();
		overlayManager.remove(overlay);

		// Remove navigation button
		clientToolbar.removeNavigation(navButton);

		stars.clear();
		networkStars.clear();
		connected = false;
		reconnectAttempts = 0;
	}

	private void connectToServer()
	{
		if (config.websocketUrl().isEmpty())
		{
			log.warn("Websocket URL is not configured, star data will not be shared");
			return;
		}

		try
		{
			socketManager.connect(new URI(config.websocketUrl()));
			connected = true;
			reconnectAttempts = 0;
		}
		catch (Exception e)
		{
			log.error("Failed to connect to websocket server", e);
			if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS)
			{
				reconnectAttempts++;
				log.info("Attempting to reconnect ({}/{})", reconnectAttempts, MAX_RECONNECT_ATTEMPTS);
				new Thread(() -> {
					try
					{
						Thread.sleep(RECONNECT_DELAY_MS);
						connectToServer();
					}
					catch (InterruptedException ie)
					{
						Thread.currentThread().interrupt();
					}
				}).start();
			}
		}
	}

	public void onWebsocketConnected()
	{
		log.info("Connected to Starhunt server");
		connected = true;
		// Send any existing stars we have
		for (StarData star : stars) {
			sendStarData(star);
		}
	}

	public void onWebsocketDisconnected()
	{
		log.info("Disconnected from Starhunt server");
		connected = false;
		connectToServer();
	}

	public void onStarDataReceived(StarData starData) {
		log.debug("Received star data: W{} T{} at {}, active: {}",
				starData.getWorld(), starData.getTier(), starData.getLocation(), starData.isActive());

		// Handle incoming star data
		clientThread.invokeLater(() -> {
			if (client.getGameState() != GameState.LOGGED_IN) {
				log.debug("Ignoring star data - client not logged in");
				return;
			}

			// Check if we already know about this star in our network list
			boolean found = false;
			for (StarData existingStar : networkStars) {
				if (existingStar.getWorld() == starData.getWorld() &&
						existingStar.getWorldPoint().equals(starData.getWorldPoint())) {
					// Update our existing star with new information
					existingStar.update(starData);
					log.debug("Updated existing star: W{} T{} at {}",
							existingStar.getWorld(), existingStar.getTier(), existingStar.getLocation());
					found = true;
					break;
				}
			}

			// Add new star if we're not tracking it yet
			if (!found) {
				networkStars.add(starData);
				log.debug("Added new star to network stars list: W{} T{} at {}",
						starData.getWorld(), starData.getTier(), starData.getLocation());

				// Sort stars by last update time (newest first)
				networkStars.sort(Comparator.comparing(StarData::getLastUpdate).reversed());

				// Show notification if enabled
				if (config.showNotifications() && starData.isActive() && starData.getTier() > 0) {
					client.addChatMessage(
							ChatMessageType.GAMEMESSAGE,
							"",
							"[Starhunt] New star found: W" + starData.getWorld() +
									" T" + starData.getTier() + " " + starData.getLocation(),
							""
					);
				}
			}

			// If this is a star in our world, check if we need to update our local list
			if (starData.getWorld() == client.getWorld()) {
				boolean localFound = false;
				for (StarData localStar : stars) {
					if (localStar.getWorldPoint().equals(starData.getWorldPoint())) {
						// We're already tracking this star locally
						localFound = true;
						break;
					}
				}

				if (!localFound && starData.isActive()) {
					// This is a star in our world that we're not tracking locally
					stars.add(starData);
					log.debug("Added star to local tracking list: W{} T{} at {}",
							starData.getWorld(), starData.getTier(), starData.getLocation());
				}
			}

			log.debug("Network stars list now contains {} stars", networkStars.size());

			// Always update the panel when we receive any star data
			if (starhuntPanel != null) {
				log.debug("Updating panel with network stars");
				starhuntPanel.updateStars(networkStars);
			} else {
				log.warn("Cannot update panel - starhuntPanel is null");
			}
		});
	}

	private void sendStarData(StarData star)
	{
		if (connected && config.shareStarData()) {
			// Set discoverer if configured
			if (config.shareUsername()) {
				star.setDiscoveredBy(client.getLocalPlayer().getName());
			}

			// Set latest update time
			star.setLastUpdate(Instant.now());

			socketManager.sendStarData(star);
		}
	}

	/**
	 * Finds a star in the network stars list by world and location
	 *
	 * @param world The world to search for
	 * @param worldPoint The world point to search for
	 * @return The star if found, null otherwise
	 */
	public StarData findStar(int world, WorldPoint worldPoint) {
		for (StarData star : networkStars) {
			if (star.getWorld() == world && star.getWorldPoint().equals(worldPoint)) {
				return star;
			}
		}
		return null;
	}

// Also add a method to get all active stars

	/**
	 * Gets all active stars
	 *
	 * @return List of active stars
	 */
	public List<StarData> getActiveStars() {
		List<StarData> activeStars = new ArrayList<>();
		for (StarData star : networkStars) {
			if (star.isActive()) {
				activeStars.add(star);
			}
		}
		return activeStars;
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		if (event.getNpc().getId() != NPC_ID) {
			return;
		}

		NPC npc = event.getNpc();
		WorldPoint worldPoint = npc.getWorldLocation();

		// Check if we already have this star
		for (StarData star : stars) {
			if (star.getWorldPoint().equals(worldPoint)) {
				star.setNpc(npc);
				sendStarData(star);
				return;
			}
		}

		// Create new star
		StarData star = new StarData(npc, client.getWorld());
		stars.add(star);
		sendStarData(star);
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		if (event.getNpc().getId() != NPC_ID) {
			return;
		}

		WorldPoint worldPoint = event.getNpc().getWorldLocation();

		// Find the star and update it
		for (StarData star : stars) {
			if (star.getWorldPoint().equals(worldPoint)) {
				star.setNpc(null);
				sendStarData(star);
				return;
			}
		}
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		int tier = StarData.getTier(event.getGameObject().getId());
		if (tier < 0) {
			return;
		}

		GameObject obj = event.getGameObject();
		WorldPoint worldPoint = obj.getWorldLocation();

		// Check if we already have this star
		for (StarData star : stars) {
			if (star.getWorldPoint().equals(worldPoint)) {
				star.setObject(obj);
				star.resetHealth();
				sendStarData(star);
				return;
			}
		}

		// Create new star
		StarData star = new StarData(obj, client.getWorld());
		stars.add(star);
		sendStarData(star);
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		int tier = StarData.getTier(event.getGameObject().getId());
		if (tier < 0) {
			return;
		}

		WorldPoint worldPoint = event.getGameObject().getWorldLocation();

		// Find the star and update/remove it
		for (StarData star : stars) {
			if (star.getWorldPoint().equals(worldPoint)) {
				star.setObject(null);
				// We'll keep it in our list in case it respawns but mark it as inactive
				star.setActive(false);
				sendStarData(star);
				return;
			}
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (!connected || !config.shareStarData() || stars.isEmpty()) {
			return;
		}

		// Update our nearby stars and send updates
		for (StarData star : stars) {
			if (star.isNearby(client.getLocalPlayer().getWorldLocation(), config.maxUpdateDistance())) {
				// Only update stars that are nearby to avoid unnecessary updates
				boolean updated = star.update(client);
				if (updated) {
					sendStarData(star);

					// Also update the UI if this star is in our network stars list
					for (StarData networkStar : networkStars) {
						if (networkStar.getWorld() == star.getWorld() &&
								networkStar.getWorldPoint().equals(star.getWorldPoint())) {
							// This is the same star, update its data
							networkStar.update(star);
							// Update the panel
							if (starhuntPanel != null) {
								starhuntPanel.updateStars(networkStars);
							}
							break;
						}
					}
				}
			}
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.HOPPING || event.getGameState() == GameState.LOGIN_SCREEN) {
			stars.clear();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals("starhunt")) {
			return;
		}

		if (event.getKey().equals("websocketUrl")) {
			socketManager.disconnect();
			connected = false;
			reconnectAttempts = 0;
			connectToServer();
		}
	}
}