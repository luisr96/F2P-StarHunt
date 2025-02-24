package com.starhunt;

import lombok.Getter;

public enum StarTier {
    TIER_1(41229, "Size 1"),
    TIER_2(41228, "Size 2"),
    TIER_3(41227, "Size 3"),
    TIER_4(41226, "Size 4"),
    TIER_5(41225, "Size 5"),
    TIER_6(41224, "Size 6"),
    TIER_7(41223, "Size 7"),
    TIER_8(41021, "Size 8"),
    TIER_9(41020, "Size 9");

    @Getter
    private final int objectId;

    @Getter
    private final String name;

    StarTier(int objectId, String name) {
        this.objectId = objectId;
        this.name = name;
    }

    public static StarTier fromObjectId(int objectId) {
        for (StarTier tier : values()) {
            if (tier.getObjectId() == objectId) {
                return tier;
            }
        }
        return null;
    }
}