package com.starhunt;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
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
import net.runelite.client.task.Schedule;

import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

import java.awt.*;
import java.awt.image.BufferedImage;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
	@Getter
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

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private StarPanelTimer starPanelTimer;

	@Inject
	private ScheduledExecutorService executor;

	private NavigationButton navButton;
	private StarhuntPanel starhuntPanel;

	// Stars that we've discovered locally
	@Getter
	private final List<StarData> stars = new ArrayList<>();

	// Stars received from the network
	@Getter
	private final List<StarData> networkStars = new ArrayList<>();

	// Track when we last sent updates for each star
	private final Map<String, Long> lastStarUpdateTimes = new HashMap<>();

	private int reconnectAttempts = 0;
	private boolean connected = false;

	@Provides
	StarhuntConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(StarhuntConfig.class);
	}

	private BufferedImage createStarIcon() {
		// Create a simple star icon
		BufferedImage image = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = image.createGraphics();
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		// Draw a star shape
		g2d.setColor(new Color(255, 215, 0)); // Gold color
		int[] xPoints = {12, 15, 21, 16, 18, 12, 6, 8, 3, 9};
		int[] yPoints = {2, 8, 8, 12, 18, 15, 18, 12, 8, 8};
		g2d.fillPolygon(xPoints, yPoints, 10);

		// Add a simple outline
		g2d.setColor(new Color(218, 165, 32)); // Darker gold
		g2d.drawPolygon(xPoints, yPoints, 10);

		g2d.dispose();
		return image;
	}

	@Override
	protected void startUp() throws Exception
	{
		log.info("Starhunt plugin starting up");

		// Add overlay
		overlayManager.add(overlay);
		log.debug("Added overlay to manager");

		// Create panel first so we can update it with connection status
		starhuntPanel = new StarhuntPanel(this, config);
		log.debug("Created StarhuntPanel instance");

		// Try to connect to server, but don't block startup
		socketManager.registerListener(this);
		safeConnectToServer();

		// Create a star-shaped icon programmatically
		BufferedImage icon;
		try {
			// First try to load the custom icon from resources
			icon = ImageUtil.loadImageResource(getClass(), "/star_icon.png");
			log.debug("Loaded custom star icon from resources");
		} catch (Exception e) {
			// If that fails, create one programmatically
			log.debug("Could not load star icon resource, creating one programmatically");
			icon = createStarIcon();
		}

		// Create navigation button
		navButton = NavigationButton.builder()
				.tooltip("Star Hunt")
				.icon(icon)
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

		// Update connection status in panel
		updateConnectionStatus();

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
		lastStarUpdateTimes.clear();
		connected = false;
		reconnectAttempts = 0;
	}

	/**
	 * Safely attempt to connect to server without blocking or crashing
	 */
	private void safeConnectToServer() {
		if (config.websocketUrl().isEmpty()) {
			log.warn("Websocket URL is not configured, star data will not be shared");
			connected = false;
			updateConnectionStatus();
			return;
		}

		// Use an executor to prevent blocking the main thread
		executor.submit(() -> {
			try {
				boolean result = socketManager.connect(new URI(config.websocketUrl()));
				if (result) {
					// Connection attempt started, but may not be successful yet
					// The actual connection status will be updated in onWebsocketConnected/Disconnected
					log.debug("WebSocket connection attempt started");
				} else {
					log.warn("Could not initiate WebSocket connection");
					connected = false;
					updateConnectionStatus();
				}
			} catch (URISyntaxException e) {
				log.error("Invalid WebSocket URL", e);
				connected = false;
				updateConnectionStatus();
			}
		});
	}

	/**
	 * Try to reconnect to the server
	 */
	public void reconnectToServer() {
		if (!connected) {
			reconnectAttempts = 0; // Reset attempts on manual reconnect
			safeConnectToServer();
		}
	}

	/**
	 * Update the connection status in the UI
	 */
	private void updateConnectionStatus() {
		if (starhuntPanel != null) {
			clientThread.invokeLater(() -> {
				starhuntPanel.updateConnectionStatus(connected);
			});
		}
	}

	public void onWebsocketConnected()
	{
		log.info("Connected to Starhunt server");
		connected = true;
		reconnectAttempts = 0;
		updateConnectionStatus();

		// Send any existing stars we have
		for (StarData star : stars) {
			sendStarData(star);
		}
	}

	public void onWebsocketDisconnected()
	{
		log.info("Disconnected from Starhunt server");
		connected = false;
		updateConnectionStatus();

		// Schedule a reconnection attempt with exponential backoff
		if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
			reconnectAttempts++;
			int delay = RECONNECT_DELAY_MS * reconnectAttempts;
			log.info("Scheduling reconnection attempt {}/{} in {} ms",
					reconnectAttempts, MAX_RECONNECT_ATTEMPTS, delay);

			executor.schedule(this::safeConnectToServer, delay, TimeUnit.MILLISECONDS);
		}
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
				star.setActive(true); // Ensure star is marked as active
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

		// We don't need to do anything special here as verifyLocalStars will handle the status
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
				star.setTier(tier);
				// Make sure to set the star as active since a new tier has spawned
				star.setActive(true);
				sendStarData(star);
				updateNetworkStar(star);
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

		// We don't need to do anything special here as verifyLocalStars will handle the status
	}

	/**
	 * Direct star verification method - called from onGameTick
	 * Checks if stars actually exist by looking for their object IDs
	 */
	/**
	 * Direct star verification method - called from onGameTick
	 * Checks if stars actually exist by looking for their object IDs
	 */
	private void verifyLocalStars() {
		// Only check when logged in
		if (client.getGameState() != GameState.LOGGED_IN) {
			return;
		}

		// Create a list of stars to remove
		List<StarData> starsToRemove = new ArrayList<>();
		boolean needsNetworkUpdate = false;

		// Check each star in our local tracking list
		for (StarData star : stars) {
			// Get world point for this star
			WorldPoint worldPoint = star.getWorldPoint();

			// Convert to local point to check if it's in the scene
			LocalPoint localPoint = LocalPoint.fromWorld(client, worldPoint);

			// Check if this point is within the currently loaded scene
			if (localPoint != null && localPoint.isInScene()) {
				// Check for star objects at this location
				Tile[][][] tiles = client.getScene().getTiles();
				int plane = worldPoint.getPlane();

				// Calculate scene coordinates
				int sceneX = localPoint.getSceneX();
				int sceneY = localPoint.getSceneY();

				// Verify coordinates are within bounds
				if (sceneX >= 0 && sceneY >= 0 && sceneX < Constants.SCENE_SIZE && sceneY < Constants.SCENE_SIZE) {
					Tile tile = tiles[plane][sceneX][sceneY];

					// Skip if tile is null
					if (tile == null) {
						continue;
					}

					// Check for star game objects on this tile
					boolean starFound = false;
					if (tile.getGameObjects() != null) {
						for (GameObject obj : tile.getGameObjects()) {
							// Skip if null
							if (obj == null) {
								continue;
							}

							// Check if this is a star object
							int tier = StarData.getTier(obj.getId());
							if (tier > 0) {
								// Star found!
								starFound = true;

								// Update the star data if needed
								if (!star.isActive() || star.getTier() != tier) {
									log.debug("Updating star at {} to tier {} (was {})",
											worldPoint, tier, star.getTier());
									star.setTier(tier);
									star.setObject(obj);
									star.setActive(true);
									star.resetHealth();
									sendStarData(star);
									updateNetworkStar(star);
								}
								break;
							}
						}
					}

					// If no star game object was found, check for the NPC
					if (!starFound) {
						List<NPC> npcsAtTile = client.getNpcs().stream()
								.filter(npc -> npc.getId() == NPC_ID && npc.getWorldLocation().equals(worldPoint))
								.collect(Collectors.toList());

						if (!npcsAtTile.isEmpty()) {
							// Star NPC found
							starFound = true;

							// Update the star data if needed
							if (!star.isActive() || star.getNpc() == null) {
								log.debug("Found star NPC at {}", worldPoint);
								star.setNpc(npcsAtTile.get(0));
								star.setActive(true);
								sendStarData(star);
								updateNetworkStar(star);
							}
						}
					}

					// If no star found at all but it's marked active, mark it inactive
					if (!starFound && star.isActive()) {
						log.debug("Star at {} no longer exists in game world - marking inactive", worldPoint);
						star.setActive(false);
						star.setObject(null);
						star.setNpc(null);
						sendStarData(star);
						updateNetworkStar(star);

						// Mark for removal from local tracking after some time
						if (star.getLastUpdate() != null) {
							long inactiveTime = Instant.now().toEpochMilli() - star.getLastUpdate().toEpochMilli();
							if (inactiveTime > 60000) { // 60 seconds
								starsToRemove.add(star);
							}
						}
					}
				}
			} else {
				// Star is outside loaded scene - we can't verify it directly
				// We'll just keep it as is unless it's been inactive for a while
				if (!star.isActive() && star.getLastUpdate() != null) {
					long inactiveTime = Instant.now().toEpochMilli() - star.getLastUpdate().toEpochMilli();
					if (inactiveTime > 60000) { // 60 seconds of inactivity
						starsToRemove.add(star);
					}
				}
			}
		}

		// Remove stars marked for removal from local tracking
		if (!starsToRemove.isEmpty()) {
			stars.removeAll(starsToRemove);
		}

		// Also verify network stars in the current world
		int currentWorld = client.getWorld();

		for (StarData networkStar : networkStars) {
			// Only check stars in our current world
			if (networkStar.getWorld() == currentWorld && networkStar.isActive()) {
				WorldPoint worldPoint = networkStar.getWorldPoint();
				LocalPoint localPoint = LocalPoint.fromWorld(client, worldPoint);

				// If point is in the scene, verify if star exists
				if (localPoint != null && localPoint.isInScene()) {
					boolean starExists = false;

					// Calculate scene coordinates
					int sceneX = localPoint.getSceneX();
					int sceneY = localPoint.getSceneY();
					int plane = worldPoint.getPlane();

					// Verify coordinates are within bounds
					if (sceneX >= 0 && sceneY >= 0 && sceneX < Constants.SCENE_SIZE && sceneY < Constants.SCENE_SIZE) {
						// Check the tile for star objects
						Tile tile = client.getScene().getTiles()[plane][sceneX][sceneY];
						if (tile != null && tile.getGameObjects() != null) {
							for (GameObject obj : tile.getGameObjects()) {
								if (obj != null && StarData.getTier(obj.getId()) > 0) {
									starExists = true;
									break;
								}
							}
						}

						// Check for NPCs
						if (!starExists) {
							starExists = client.getNpcs().stream()
									.anyMatch(npc -> npc.getId() == NPC_ID && npc.getWorldLocation().equals(worldPoint));
						}

						// Update if status doesn't match reality
						if (!starExists && networkStar.isActive()) {
							log.debug("Network star at {} doesn't exist in game world - marking inactive", worldPoint);
							networkStar.setActive(false);
							networkStar.setLastUpdate(Instant.now());
							needsNetworkUpdate = true;
						}
					}
				}
			}
		}

		// Update the panel if any network stars were modified
		if (needsNetworkUpdate && starhuntPanel != null) {
			clientThread.invokeLater(() -> {
				starhuntPanel.updateStars(networkStars);
			});
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		// Run the direct star verification method
		verifyLocalStars();

		// Skip update logic if not connected or no stars to update
		if (!connected || !config.shareStarData() || stars.isEmpty()) {
			return;
		}

		long currentTime = System.currentTimeMillis();
		int baseUpdateFrequencyMs = config.updateFrequency() * 1000;

		for (StarData star : stars) {
			if (star.isActive() && star.isNearby(client.getLocalPlayer().getWorldLocation(), config.maxUpdateDistance())) {
				String starId = star.getWorld() + "_" + star.getWorldPoint().getX() + "_" + star.getWorldPoint().getY();
				Long lastUpdate = lastStarUpdateTimes.getOrDefault(starId, 0L);

				// Determine if we should update based on time elapsed
				boolean shouldUpdate = false;

				// Add some jitter to prevent all clients updating at exactly the same time
				double jitterFactor = 0.8 + (Math.random() * 0.4); // 0.8 to 1.2
				int actualUpdateFrequency = (int)(baseUpdateFrequencyMs * jitterFactor);

				// Check if it's time for a regular update
				if (currentTime - lastUpdate >= actualUpdateFrequency) {
					shouldUpdate = true;
				}

				if (shouldUpdate) {
					boolean updated = star.update(client);
					if (updated) {
						sendStarData(star);
						lastStarUpdateTimes.put(starId, currentTime);
					}
				}
			}
		}
	}

	private void updateNetworkStar(StarData star) {
		boolean found = false;

		for (StarData networkStar : networkStars) {
			if (networkStar.getWorld() == star.getWorld() &&
					networkStar.getWorldPoint().equals(star.getWorldPoint())) {
				networkStar.update(star);
				found = true;
				break;
			}
		}

		// If not found in network stars but is valid, add it
		if (!found && star.getTier() > 0) {
			networkStars.add(star);
			// Sort stars by last update time (newest first)
			networkStars.sort(Comparator.comparing(StarData::getLastUpdate).reversed());
		}

		// Update the panel
		if (starhuntPanel != null) {
			clientThread.invokeLater(() -> {
				starhuntPanel.updateStars(networkStars);
			});
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
			safeConnectToServer();
		}
	}

	@Schedule(
			period = 5,
			unit = ChronoUnit.SECONDS
	)
	public void cleanupStars() {
		// Clean up depleted tier 1 stars that have been inactive for some time
		boolean needsUpdate = false;
		long currentTime = Instant.now().toEpochMilli();

		// Check if we need to clean up any network stars
		Iterator<StarData> iterator = networkStars.iterator();
		while (iterator.hasNext()) {
			StarData star = iterator.next();

			// If a star has been inactive for more than 60 seconds, remove it
			if (!star.isActive()) {
				long inactiveTime = currentTime - star.getLastUpdate().toEpochMilli();
				if (inactiveTime > 60000) { // 60 seconds
					log.debug("Removing inactive star from network stars: W{} T{} at {}",
							star.getWorld(), star.getTier(), star.getLocation());
					iterator.remove();
					needsUpdate = true;
				}
			}
		}

		// If we removed any stars, update the panel
		if (needsUpdate && starhuntPanel != null) {
			clientThread.invokeLater(() -> {
				starhuntPanel.updateStars(networkStars);
			});
		}
	}

	/**
	 * Periodically attempt to reconnect if not connected
	 */
	@Schedule(
			period = 60,
			unit = ChronoUnit.SECONDS
	)
	public void scheduledReconnect() {
		if (!connected && reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
			log.debug("Scheduled reconnection attempt");
			reconnectAttempts = 0; // Reset the counter for scheduled reconnects
			safeConnectToServer();
		}
	}
}