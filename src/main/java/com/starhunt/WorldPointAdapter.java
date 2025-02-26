package com.starhunt;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import net.runelite.api.coords.WorldPoint;

import java.io.IOException;

public class WorldPointAdapter extends TypeAdapter<WorldPoint> {
    @Override
    public void write(JsonWriter out, WorldPoint worldPoint) throws IOException {
        out.beginObject();
        out.name("x").value(worldPoint.getX());
        out.name("y").value(worldPoint.getY());
        out.name("plane").value(worldPoint.getPlane());
        out.endObject();
    }

    @Override
    public WorldPoint read(JsonReader in) throws IOException {
        int x = 0;
        int y = 0;
        int plane = 0;

        in.beginObject();
        while (in.hasNext()) {
            String name = in.nextName();
            switch (name) {
                case "x":
                    x = in.nextInt();
                    break;
                case "y":
                    y = in.nextInt();
                    break;
                case "plane":
                    plane = in.nextInt();
                    break;
                default:
                    in.skipValue();
                    break;
            }
        }
        in.endObject();

        return new WorldPoint(x, y, plane);
    }
}