package com.starhunt;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import net.runelite.api.ObjectID;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Data
public class StarData {
    // Same tier IDs as in the original plugin
    private static final int[] TIER_IDS = new int[]{
            ObjectID.CRASHED_STAR_41229,
            ObjectID.CRASHED_STAR_41228,
            ObjectID.CRASHED_STAR_41227,
            ObjectID.CRASHED_STAR_41226,
            ObjectID.CRASHED_STAR_41225,
            ObjectID.CRASHED_STAR_41224,
            ObjectID.CRASHED_STAR_41223,
            ObjectID.CRASHED_STAR_41021,
            ObjectID.CRASHED_STAR,
    };

    public static final String UNKNOWN_MINERS = "?";
    private static final int MINING_CACHE_TIME = 13;
    private static final Map<String, Integer> playerLastMined = new HashMap<>();

    // This data will be serialized and sent over websocket
    @Getter private final WorldPoint worldPoint;
    @Getter private final int world;
    @Getter @Setter private String location;
    @Getter @Setter private int tier = -1;
    @Getter @Setter private int health = -1;
    @Getter @Setter private String miners = UNKNOWN_MINERS;
    @Getter @Setter private int[] tierTicksEstimate;
    @Getter @Setter private boolean active = true;
    @Getter @Setter private Instant lastUpdate = Instant.now();
    @Getter @Setter private String discoveredBy;

    // These fields won't be serialized
    @Getter @Setter private transient NPC npc;
    @Getter @Setter private transient GameObject object;

    public StarData(NPC npc, int world) {
        this.npc = npc;
        this.worldPoint = npc.getWorldLocation();
        this.world = world;
        this.location = getLocationName(worldPoint);
        this.active = true; // Ensure new stars are active by default
        this.lastUpdate = Instant.now();
    }

    public StarData(GameObject gameObject, int world) {
        this.object = gameObject;
        this.worldPoint = gameObject.getWorldLocation();
        this.world = world;
        this.tier = getTier(gameObject.getId());
        this.location = getLocationName(worldPoint);
        this.active = true; // Ensure new stars are active by default
        this.lastUpdate = Instant.now();
    }

    // Deserialization constructor
    public StarData(WorldPoint worldPoint, int world, String location, int tier, int health,
                    String miners, int[] tierTicksEstimate, boolean active, Instant lastUpdate,
                    String discoveredBy) {
        this.worldPoint = worldPoint;
        this.world = world;
        this.location = location;
        this.tier = tier;
        this.health = health;
        this.miners = miners;
        this.tierTicksEstimate = tierTicksEstimate;
        // If active isn't specified, default to true
        this.active = active;
        this.lastUpdate = lastUpdate != null ? lastUpdate : Instant.now();
        this.discoveredBy = discoveredBy;
    }

    public static int getTier(int id) {
        for (int i = 0; i < TIER_IDS.length; i++) {
            if (id == TIER_IDS[i]) {
                return i + 1;
            }
        }
        return -1;
    }

    public int getTier() {
        if (object == null) {
            return tier;
        }
        return getTier(object.getId());
    }

    public int getHealth() {
        if (npc == null) {
            return health;
        }
        if (npc.getHealthRatio() >= 0) {
            health = 100 * npc.getHealthRatio() / npc.getHealthScale();
        } else if (npc.isDead()) {
            health = -1;
        }
        return health;
    }

    public void resetHealth() {
        health = 100;
    }

    public boolean isNearby(WorldPoint playerLocation, int maxDistance) {
        return worldPoint.distanceTo(playerLocation) <= maxDistance;
    }

    /**
     * Update star information based on client data
     * @param client The RuneLite client
     * @return true if the star data changed
     */
    public boolean update(Client client) {
        boolean changed = false;

        // Update tier
        int newTier = getTier();
        if (tier != newTier && newTier > 0) {
            tier = newTier;
            changed = true;
        }

        // Update health
        int newHealth = getHealth();
        if (health != newHealth && newHealth >= 0) {
            health = newHealth;
            changed = true;
        }

        // Update miners count
        String newMiners = countMiners(client);
        if (!miners.equals(newMiners)) {
            miners = newMiners;
            changed = true;
        }

        if (changed) {
            lastUpdate = Instant.now();
        }

        return changed;
    }

    /**
     * Updates this star data with information from another star data object
     * @param other The other star data
     */
    public void update(StarData other) {
        // If the other star has a newer tier, make sure we keep the active status
        if (other.getTier() > 0 && other.getTier() != this.tier) {
            this.tier = other.getTier();
            // If we're getting a new tier, the star should be active
            this.active = true;
        } else if (other.getTier() > 0) {
            this.tier = other.getTier();
        }

        if (other.getHealth() >= 0) {
            this.health = other.getHealth();
        }

        if (!other.getMiners().equals(UNKNOWN_MINERS)) {
            this.miners = other.getMiners();
        }

        if (other.getTierTicksEstimate() != null) {
            this.tierTicksEstimate = Arrays.copyOf(other.getTierTicksEstimate(), other.getTierTicksEstimate().length);
        }

        // Only use the other star's active status if we're not dealing with a tier change
        // or if we're explicitly activating the star
        if (other.isActive()) {
            this.active = true;
        }

        this.lastUpdate = other.getLastUpdate();

        if (other.getDiscoveredBy() != null) {
            this.discoveredBy = other.getDiscoveredBy();
        }
    }

    private String countMiners(Client client) {
        if (worldPoint == null || !active) {
            return UNKNOWN_MINERS;
        }

        if (client.getWorld() != world) {
            // We can't count miners on other worlds
            return miners;
        }

        // Only count miners if we're close enough
        if (client.getLocalPlayer().getWorldLocation().distanceTo(worldPoint) > 15) {
            return miners;
        }

        WorldArea areaH = new WorldArea(worldPoint.dx(-1), 4, 2);
        WorldArea areaV = new WorldArea(worldPoint.dy(-1), 2, 4);
        int count = 0;
        int tickCount = client.getTickCount();

        for (Player p : client.getPlayers()) {
            if (!p.getWorldLocation().isInArea2D(areaH, areaV)) {
                // Skip players not next to the star
                continue;
            }

            if (isMiningAnimation(p.getAnimation())) {
                count++;
                playerLastMined.put(p.getName(), tickCount);
                continue;
            }

            if (p.getHealthRatio() < 0 || !playerLastMined.containsKey(p.getName())) {
                continue;
            }

            int ticksSinceMinedLast = tickCount - playerLastMined.get(p.getName());
            if (ticksSinceMinedLast < MINING_CACHE_TIME) {
                count++;
            }
        }

        return Integer.toString(count);
    }

    private boolean isMiningAnimation(int animId) {
        // This is a simplified version - you may want to check against the full list from StarInfoPlugin
        return animId >= 624 && animId <= 8329; // Wide range that includes common mining animations
    }

    /**
     * Gets a more descriptive location name using the StarLocation enum
     *
     * @return User-friendly location name
     */
    public String getFormattedLocation() {
        // Try to find a matching location from StarLocation enum
        WorldPoint point = getWorldPoint();
        if (point != null) {
            StarLocation closestLocation = StarLocation.getClosestLocation(point);
            if (closestLocation != null) {
                return closestLocation.getName();
            }
        }

        // Fall back to the basic location string
        return location;
    }

// Also modify the existing getLocationName method to use the StarLocation data:

    private String getLocationName(WorldPoint worldPoint) {
        StarLocation closestLocation = StarLocation.getClosestLocation(worldPoint);
        if (closestLocation != null) {
            return closestLocation.getName();
        }

        // Fall back to coordinates if no matching location
        return worldPoint.getX() + "," + worldPoint.getY();
    }

    public String getMessage() {
        return "Star: W" + world + " T" + getTier() + " " + location +
                (active ? "" : " (inactive)");
    }

    /**
     * Gets a more accurate health tracking by accounting for previous tiers
     * This is useful for displaying overall progress of the star
     *
     * @return Overall percentage completion (0-100)
     */
    public int getOverallHealth() {
        if (tier <= 0 || health < 0) {
            return -1;
        }

        // Calculate how many tiers are "complete"
        int completedTiers = 9 - tier;

        // Calculate the percentage of the current tier
        double currentTierPercentage = health / 100.0;

        // Total percentage, where each tier is worth 1/9 of the total
        double totalPercentage = (completedTiers + currentTierPercentage) / 9.0;

        // Convert to percentage (0-100)
        return 100 - (int) Math.round(totalPercentage * 100);
    }

    /**
     * Estimate the time remaining until this tier depletes
     * based on health and number of miners
     *
     * @return Estimated seconds remaining, -1 if unknown
     */
    public int getEstimatedTimeRemaining() {
        if (tier <= 0 || health < 0) {
            return -1;
        }

        // Count the number of miners, default to 1 if unknown
        int numMiners = 1;
        if (!miners.equals(UNKNOWN_MINERS)) {
            try {
                numMiners = Integer.parseInt(miners);
                if (numMiners <= 0) {
                    numMiners = 1;
                }
            } catch (NumberFormatException e) {
                // Ignore, use default
            }
        }

        // Rough estimate: tier size * health percentage / miners
        // This is a very simplified model and would need tuning
        // Values below are estimates for how long a tier takes with 1 miner
        int[] tierTimesInSeconds = {
                300,   // Tier 1 - approximately 5 minutes
                450,   // Tier 2 - approximately 7.5 minutes
                600,   // Tier 3 - approximately 10 minutes
                750,   // Tier 4 - approximately 12.5 minutes
                900,   // Tier 5 - approximately 15 minutes
                1200,  // Tier 6 - approximately 20 minutes
                1500,  // Tier 7 - approximately 25 minutes
                1800,  // Tier 8 - approximately 30 minutes
                2100   // Tier 9 - approximately 35 minutes
        };

        // Calculate time remaining for this tier
        int tierIndex = Math.min(tier - 1, tierTimesInSeconds.length - 1);
        int timeForFullTier = tierTimesInSeconds[tierIndex];

        // Reduce time based on miners (diminishing returns)
        // For example: 2 miners -> 60% of solo time, 5 miners -> 30% of solo time
        double minerMultiplier = Math.max(0.2, 1.0 / Math.sqrt(numMiners));

        int adjustedTime = (int) (timeForFullTier * minerMultiplier);

        // Apply health percentage
        return (int) (adjustedTime * (health / 100.0));
    }

    /**
     * Format time remaining as a readable string
     *
     * @return Formatted time string
     */
    public String getFormattedTimeRemaining() {
        int seconds = getEstimatedTimeRemaining();

        if (seconds < 0) {
            return "Unknown";
        }

        int minutes = seconds / 60;
        int hours = minutes / 60;
        minutes = minutes % 60;

        if (hours > 0) {
            return String.format("%dh %dm", hours, minutes);
        } else {
            return String.format("%dm", minutes);
        }
    }


}