package com.starhunt;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("starhunt")
public interface StarhuntConfig extends Config
{
	@ConfigItem(
			keyName = "showPanel",
			name = "Show Star Panel",
			description = "Toggle the star tracking panel"
	)
	default boolean showPanel()
	{
		return true;
	}

	@ConfigItem(
			keyName = "autoTrack",
			name = "Auto Track Stars",
			description = "Automatically track stars when found"
	)
	default boolean autoTrack()
	{
		return true;
	}

	@ConfigItem(
			keyName = "notifications",
			name = "Star Notifications",
			description = "Show notifications when stars are found"
	)
	default boolean notifications()
	{
		return true;
	}
}