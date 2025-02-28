package com.starhunt;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

@Slf4j
@Singleton
public class StarhuntSocketManager {

    private final Gson gson;
    private WebSocketClient client;
    private final List<Object> listeners = new ArrayList<>();
    private boolean isConnecting = false;

    @Inject
    private ScheduledExecutorService executor;

    @Inject
    private StarhuntConfig config;

    @Inject
    public StarhuntSocketManager() {
        this.gson = createGsonInstance();
    }

    private Gson createGsonInstance() {
        return new GsonBuilder()
                .registerTypeAdapter(WorldPoint.class, new WorldPointAdapter())
                .registerTypeAdapter(Instant.class, new InstantAdapter())
                .create();
    }

    /**
     * Connect to WebSocket server
     * @param serverUri URI of the WebSocket server
     * @return boolean indicating whether connection was initiated (not necessarily successful)
     */
    public boolean connect(URI serverUri) {
        // Check if we're already connected or connecting
        if (client != null && client.isOpen()) {
            log.info("Already connected to a websocket server");
            return true;
        }

        if (isConnecting) {
            log.info("Already attempting to connect to a websocket server");
            return false;
        }

        isConnecting = true;
        log.info("Connecting to WebSocket server: {}", serverUri);

        try {
            client = new WebSocketClient(serverUri) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    log.info("Connected to Starhunt server");
                    isConnecting = false;
                    notifyListeners("onWebsocketConnected");
                }

                @Override
                public void onMessage(String message) {
                    log.debug("Received WebSocket message: {}", message);
                    handleMessage(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    log.info("Disconnected from Starhunt server: {} (code: {})", reason, code);
                    isConnecting = false;
                    notifyListeners("onWebsocketDisconnected");
                }

                @Override
                public void onError(Exception ex) {
                    log.error("Websocket error", ex);
                    isConnecting = false;
                }
            };

            // Set up keep-alive ping
            executor.scheduleAtFixedRate(() -> {
                if (client != null && client.isOpen()) {
                    client.sendPing();
                }
            }, 30, 30, TimeUnit.SECONDS);

            // Connect asynchronously to prevent blocking the main thread
            client.connectBlocking(2, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            log.error("Failed to initialize WebSocket connection", e);
            isConnecting = false;
            return false;
        }
    }

    public boolean isConnected() {
        return client != null && client.isOpen();
    }

    public void disconnect() {
        if (client != null && client.isOpen()) {
            client.close();
        }
        isConnecting = false;
    }

    public void sendStarData(StarData star) {
        if (client == null || !client.isOpen()) {
            log.debug("Cannot send star data: WebSocket not connected");
            return;
        }

        try {
            // Create a message payload
            MessagePayload payload = new MessagePayload();
            payload.setType(MessageType.STAR_UPDATE);
            payload.setData(star);

            String message = gson.toJson(payload);
            log.debug("Sending star data: {}", message);
            client.send(message);
        } catch (Exception e) {
            log.error("Failed to send star data", e);
        }
    }

    private void handleMessage(String message) {
        try {
            MessagePayload payload = gson.fromJson(message, MessagePayload.class);

            if (payload.getType() == MessageType.STAR_UPDATE) {
                log.debug("Received STAR_UPDATE message");
                StarData star = gson.fromJson(gson.toJson(payload.getData()), StarData.class);
                log.debug("Parsed star data: W{} T{} at {}", star.getWorld(), star.getTier(), star.getLocation());
                notifyListenersWithStar(star);
            } else {
                log.debug("Received message with type: {}", payload.getType());
            }
        } catch (Exception e) {
            log.error("Failed to parse message: {}", message, e);
        }
    }

    public void registerListener(Object listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
            log.debug("Registered listener: {}", listener.getClass().getSimpleName());
        }
    }

    public void unregisterListener(Object listener) {
        listeners.remove(listener);
        log.debug("Unregistered listener: {}", listener.getClass().getSimpleName());
    }

    private void notifyListeners(String methodName) {
        log.debug("Notifying {} listeners with method: {}", listeners.size(), methodName);
        for (Object listener : listeners) {
            try {
                listener.getClass().getMethod(methodName).invoke(listener);
            } catch (Exception e) {
                log.error("Failed to notify listener {} with method {}",
                        listener.getClass().getSimpleName(), methodName, e);
            }
        }
    }

    private void notifyListenersWithStar(StarData star) {
        log.debug("Notifying {} listeners with star data: W{} T{} at {}",
                listeners.size(), star.getWorld(), star.getTier(), star.getLocation());

        for (Object listener : listeners) {
            try {
                listener.getClass().getMethod("onStarDataReceived", StarData.class).invoke(listener, star);
                log.debug("Successfully notified listener: {}", listener.getClass().getSimpleName());
            } catch (Exception e) {
                log.error("Failed to notify listener {} with star data",
                        listener.getClass().getSimpleName(), e);
            }
        }
    }

    private enum MessageType {
        STAR_UPDATE,
        PLAYER_JOIN,
        PLAYER_LEAVE
    }

    private static class MessagePayload {
        private MessageType type;
        private Object data;

        public MessageType getType() {
            return type;
        }

        public void setType(MessageType type) {
            this.type = type;
        }

        public Object getData() {
            return data;
        }

        public void setData(Object data) {
            this.data = data;
        }
    }
}