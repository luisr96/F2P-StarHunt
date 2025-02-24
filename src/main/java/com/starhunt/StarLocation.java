package com.starhunt;

import lombok.Getter;
import net.runelite.api.coords.WorldPoint;

import java.util.Arrays;

public enum StarLocation {
    CRAFTING_GUILD("Crafting Guild", new WorldPoint(2940, 3280, 0), true),
    RIMMINGTON_MINE("Rimmington Mine", new WorldPoint(2974, 3240, 0), true),
    LUMBRIDGE_SWAMP("Lumbridge Swamp", new WorldPoint(3228, 3186, 0), true),
    DRAYNOR_VILLAGE_BANK("Draynor Village Bank", new WorldPoint(3092, 3243, 0), true),
    VARROCK_EAST_MINE("Varrock East Mine", new WorldPoint(3290, 3369, 0), true),
    BARBARIAN_VILLAGE("Barbarian Village", new WorldPoint(3082, 3420, 0), true),
    EDGEVILLE_MONASTERY("Edgeville Monastery", new WorldPoint(3052, 3497, 0), true),
    COOKS_GUILD("Cooks' Guild", new WorldPoint(3145, 3442, 0), true),
    GRAND_EXCHANGE("Grand Exchange", new WorldPoint(3164, 3489, 0), true),
    FALADOR_PARK("Falador Park", new WorldPoint(2999, 3376, 0), true),
    DWARVEN_MINE("Dwarven Mine", new WorldPoint(3019, 3450, 0), true),
    WILDERNESS_RUNITE_ROCKS("Wilderness Runite Rocks", new WorldPoint(3061, 3884, 0), true),
    SOUTHERN_WILDERNESS("Southern Wilderness", new WorldPoint(3024, 3595, 0), true),
    PORT_KHAZARD("Port Khazard", new WorldPoint(2650, 3166, 0), false),
    YANILLE_BANK("Yanille Bank", new WorldPoint(2602, 3093, 0), false),
    AL_KHARID_MINE("Al Kharid Mine", new WorldPoint(3295, 3300, 0), true),
    CORSAIR_COVE("Corsair Cove", new WorldPoint(2483, 2890, 0), true);

    @Getter
    private final String name;

    @Getter
    private final WorldPoint location;

    @Getter
    private final boolean f2p;

    StarLocation(String name, WorldPoint location, boolean f2p) {
        this.name = name;
        this.location = location;
        this.f2p = f2p;
    }

    public static StarLocation getClosestLocation(WorldPoint point) {
        return Arrays.stream(values())
                .min((l1, l2) -> {
                    int dist1 = l1.getLocation().distanceTo2D(point);
                    int dist2 = l2.getLocation().distanceTo2D(point);
                    return Integer.compare(dist1, dist2);
                })
                .orElse(null);
    }
}