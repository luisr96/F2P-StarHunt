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
    }

    public StarData(GameObject gameObject, int world) {
        this.object = gameObject;
        this.worldPoint = gameObject.getWorldLocation();
        this.world = world;
        this.tier = getTier(gameObject.getId());
        this.location = getLocationName(worldPoint);
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
        this.active = active;
        this.lastUpdate = lastUpdate;
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
        if (other.getTier() > 0) {
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

        this.active = other.isActive();
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

    private String getLocationName(WorldPoint worldPoint) {
        // For simplicity, just return coordinates - in a full implementation,
        // you'd want to use the Location class from StarInfoPlugin
        return worldPoint.getX() + "," + worldPoint.getY();
    }

    public String getMessage() {
        return "Star: W" + world + " T" + getTier() + " " + location +
                (active ? "" : " (inactive)");
    }
}