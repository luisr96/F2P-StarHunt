package com.starhunt;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("starhunt")
public interface StarhuntConfig extends Config
{
	@ConfigSection(
			name = "Connection",
			description = "Settings for connecting to the Starhunt server",
			position = 0
	)
	String connectionSection = "connectionSection";

	@ConfigItem(
			keyName = "websocketUrl",
			name = "Server URL",
			description = "The URL of the Starhunt websocket server",
			section = connectionSection,
			position = 1
	)
	default String websocketUrl()
	{
		return "ws://localhost:8080";
	}

	@ConfigItem(
			keyName = "shareStarData",
			name = "Share Star Data",
			description = "Whether to share star data with the server",
			position = 2
	)
	default boolean shareStarData()
	{
		return true;
	}

	@ConfigItem(
			keyName = "shareUsername",
			name = "Share Username",
			description = "Whether to share your username when reporting stars",
			position = 3
	)
	default boolean shareUsername()
	{
		return false;
	}

	@ConfigItem(
			keyName = "showNotifications",
			name = "Show Notifications",
			description = "Whether to show notifications for new stars",
			position = 4
	)
	default boolean showNotifications()
	{
		return true;
	}

	@Range(
			min = 10,
			max = 100
	)
	@ConfigItem(
			keyName = "maxUpdateDistance",
			name = "Max Update Distance",
			description = "The maximum distance (in tiles) at which to send updates about a star",
			position = 5
	)
	default int maxUpdateDistance()
	{
		return 32;
	}

	@Range(
			min = 5,
			max = 60
	)
	@ConfigItem(
			keyName = "updateFrequency",
			name = "Update Frequency",
			description = "How often (in seconds) to send updates about a star",
			position = 6
	)
	default int updateFrequency()
	{
		return 10;
	}
}