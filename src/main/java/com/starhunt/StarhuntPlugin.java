package com.starhunt;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@PluginDescriptor(
		name = "F2P StarHunt"
)
public class StarhuntPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private StarhuntConfig config;

	@Inject
	private ClientToolbar clientToolbar;

	private NavigationButton navButton;
	private StarhuntPanel panel;

	// Store found stars to prevent duplicates
	private final Map<String, StarData> foundStars = new HashMap<>();

	// Track stars currently in view
	private final Set<String> starsInView = new HashSet<>();

	// Track when we last checked for stars to limit API calls
	private Instant lastDataSync;
	private static final int SYNC_INTERVAL_SECONDS = 60; // Sync every minute

	@Override
	protected void startUp() throws Exception
	{
		log.info("F2P StarHunt started!");

		// Initialize the panel
		panel = new StarhuntPanel();

		try {
			// Load the icon
			final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/star_icon.png");

			navButton = NavigationButton.builder()
					.tooltip("F2P StarHunt")
					.icon(icon)
					.priority(5)
					.panel(panel)
					.build();
		} catch (Exception e) {
			// Fallback if icon can't be loaded
			log.warn("Couldn't load icon", e);

			navButton = NavigationButton.builder()
					.tooltip("F2P StarHunt")
					.priority(5)
					.panel(panel)
					.build();
		}

		clientToolbar.addNavigation(navButton);
		lastDataSync = Instant.now().minusSeconds(SYNC_INTERVAL_SECONDS); // Force initial sync
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("F2P StarHunt stopped!");
		clientToolbar.removeNavigation(navButton);
		foundStars.clear();
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!config.autoTrack())
		{
			return;
		}

		// Clear the set of stars in view
		starsInView.clear();

		// Check for stars
		scanForStars();

		// Remove any stars that are no longer in view
		checkForDepletedStars();

		// Check if we should sync with server
		if (Instant.now().isAfter(lastDataSync.plusSeconds(SYNC_INTERVAL_SECONDS)))
		{
			syncWithServer();
			lastDataSync = Instant.now();
		}
	}

	private void scanForStars()
	{
		// Get all objects in the scene
		List<GameObject> objects = Arrays.stream(client.getScene().getTiles())
				.flatMap(Arrays::stream)
				.flatMap(Arrays::stream)
				.filter(tile -> tile != null)
				.flatMap(tile -> Arrays.stream(tile.getGameObjects()))
				.filter(obj -> obj != null)
				.collect(Collectors.toList());

		// Check each object to see if it's a star
		for (GameObject object : objects)
		{
			StarTier tier = StarTier.fromObjectId(object.getId());
			if (tier != null)
			{
				// Star found! Process it
				processFoundStar(object, tier);

				// Add to our view set
				String key = getStarKey(object.getWorldLocation(), client.getWorld());
				starsInView.add(key);
			}
		}
	}

	private void checkForDepletedStars()
	{
		// Create a copy of the keys to avoid ConcurrentModificationException
		List<String> currentStars = new ArrayList<>(foundStars.keySet());

		for (String key : currentStars)
		{
			// If a star was in foundStars but not in our current view, it might be depleted
			if (!starsInView.contains(key))
			{
				StarData star = foundStars.get(key);

				// Only remove if the star is in the current world
				// (we don't want to remove stars from other worlds)
				if (star.getWorld() == client.getWorld())
				{
					// Check if it's in our render distance
					WorldPoint playerLocation = client.getLocalPlayer().getWorldLocation();
					int distance = star.getLocation().distanceTo2D(playerLocation);

					// Only remove if we're close enough that we should be able to see it
					// (render distance is approximately 32 tiles)
					if (distance <= 32)
					{
						log.debug("Star depleted at {}",
								star.getLocationName() != null ? star.getLocationName().getName() : "Unknown");

						// Remove from our tracking
						foundStars.remove(key);

						// Update the panel
						panel.removeStar(key);

						// TODO: Update server that star is depleted
					}
				}
			}
		}
	}

	// Helper method to generate a consistent unique key
	private String getStarKey(WorldPoint location, int world)
	{
		return world + ":" + location.getX() + ":" + location.getY() + ":" + location.getPlane();
	}

	private void processFoundStar(GameObject object, StarTier tier)
	{
		WorldPoint location = object.getWorldLocation();
		StarLocation starLocation = StarLocation.getClosestLocation(location);

		// Create unique key
		String key = getStarKey(location, client.getWorld());

		// Create star data
		StarData starData = new StarData(
				client.getWorld(),
				location,
				starLocation,
				tier,
				Instant.now(),
				client.getLocalPlayer().getName()
		);

		// Only add if we haven't seen this star before
		if (!foundStars.containsKey(key))
		{
			foundStars.put(key, starData);
			panel.addStar(starData);
			log.debug("Found new star: {} at {} ({})",
					tier.getName(),
					starLocation != null ? starLocation.getName() : "Unknown",
					location);

			// TODO: Send to server/database
		}
		else
		{
			// Update the tier if it changed
			StarData existingStar = foundStars.get(key);
			if (existingStar.getTier() != tier)
			{
				existingStar.setTier(tier);
				panel.updateStar(existingStar);
				log.debug("Updated star tier: {} at {}",
						tier.getName(),
						starLocation != null ? starLocation.getName() : "Unknown");

				// TODO: Update server/database
			}
		}
	}

	private void syncWithServer()
	{
		// TODO: Implement server sync
		log.debug("Syncing with server...");

		// This would fetch the latest stars from the server
		// For now, just keep our local copy
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		if (gameStateChanged.getGameState() == GameState.LOGGED_IN)
		{
			// Sync with server on login
			syncWithServer();
		}
	}

	@Provides
	StarhuntConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(StarhuntConfig.class);
	}
}