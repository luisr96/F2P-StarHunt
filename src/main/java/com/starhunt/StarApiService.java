package com.starhunt;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
public class StarApiService {
    private static final String API_URL = "https://your-api-endpoint.com/api/stars"; // Replace with actual endpoint
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final Gson gson;

    public StarApiService() {
        // Configure HTTP client with reasonable timeouts
        client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        // Configure Gson for JSON serialization
        gson = new GsonBuilder()
                .registerTypeAdapter(Instant.class, new InstantTypeAdapter())
                .create();
    }

    /**
     * Send star data to the server
     */
    public void sendStarData(Collection<StarData> stars) {
        if (stars.isEmpty()) {
            return;
        }

        try {
            String json = gson.toJson(stars);
            // Fixed version for newer OkHttp
            RequestBody body = RequestBody.create(JSON, json);

            Request request = new Request.Builder()
                    .url(API_URL)
                    .post(body)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.warn("Failed to send star data: {}", response.code());
                }
            }
        } catch (IOException e) {
            log.error("Error sending star data", e);
        }
    }

    /**
     * Report a depleted star
     */
    public void reportDepletedStar(String starKey) {
        try {
            String json = "{\"key\":\"" + starKey + "\"}";
            // Fixed version for newer OkHttp
            RequestBody body = RequestBody.create(JSON, json);

            Request request = new Request.Builder()
                    .url(API_URL + "/depleted")
                    .post(body)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.warn("Failed to report depleted star: {}", response.code());
                }
            }
        } catch (IOException e) {
            log.error("Error reporting depleted star", e);
        }
    }

    /**
     * Get all active stars from the server
     */
    public Map<String, StarData> getActiveStars() {
        try {
            Request request = new Request.Builder()
                    .url(API_URL)
                    .get()
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.warn("Failed to get star data: {}", response.code());
                    return Collections.emptyMap();
                }

                String responseBody = response.body().string();
                Type listType = new TypeToken<List<StarData>>(){}.getType();
                List<StarData> stars = gson.fromJson(responseBody, listType);

                // Convert to map for easier lookup
                Map<String, StarData> starMap = new HashMap<>();
                for (StarData star : stars) {
                    starMap.put(star.getUniqueKey(), star);
                }

                return starMap;
            }
        } catch (IOException e) {
            log.error("Error getting star data", e);
            return Collections.emptyMap();
        }
    }

    // Helper class for proper serialization of Instant objects
    private static class InstantTypeAdapter implements com.google.gson.JsonSerializer<Instant>,
            com.google.gson.JsonDeserializer<Instant> {
        @Override
        public com.google.gson.JsonElement serialize(Instant src, java.lang.reflect.Type typeOfSrc,
                                                     com.google.gson.JsonSerializationContext context) {
            return new com.google.gson.JsonPrimitive(src.toEpochMilli());
        }

        @Override
        public Instant deserialize(com.google.gson.JsonElement json, java.lang.reflect.Type typeOfT,
                                   com.google.gson.JsonDeserializationContext context) {
            return Instant.ofEpochMilli(json.getAsLong());
        }
    }
}