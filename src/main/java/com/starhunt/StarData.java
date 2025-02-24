package com.starhunt;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.runelite.api.coords.WorldPoint;

import java.time.Instant;

@Data
@AllArgsConstructor
public class StarData {
    private int world;
    private WorldPoint location;
    private StarLocation locationName;
    private StarTier tier;
    private Instant foundTime;
    private String foundBy;

    // This will be used to uniquely identify a star
    public String getUniqueKey() {
        return world + ":" + location.getX() + ":" + location.getY() + ":" + location.getPlane();
    }
}