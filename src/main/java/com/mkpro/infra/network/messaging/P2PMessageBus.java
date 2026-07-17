package com.mkpro.infra.network.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mkpro.security.MessageAuthenticator;
import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.ServerHandshake;
import org.java_websocket.server.DefaultSSLWebSocketServerFactory;
import org.java_websocket.server.WebSocketServer;

import javax.net.ssl.SSLContext;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * P2PMessageBus manages peer-to-peer WebSocket communication.
 * Enhanced with mTLS support and dynamic certificate rotation.
 */
public class P2PMessageBus extends WebSocketServer {

    private static final String SIGNATURE_FIELD = "_sig";
    private static final String PAYLOAD_FIELD = "_payload";
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final int MAX_RECONNECT_ATTEMPTS = 5;
    private static final long INITIAL_BACKOFF_MS = 2000;

    private final Set<WebSocketClient> outboundPeers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<String> connectedPeerUris = ConcurrentHashMap.newKeySet();
    private final com.mkpro.infra.network.security.P2PAuditLog auditLog = com.mkpro.infra.network.security.P2PAuditLog.getInstance();
    private final ScheduledExecutorService reconnectExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "P2P-Reconnect");
        t.setDaemon(true);
        return t;
    });

    private Consumer<ObjectNode> messageHandler;
    private final MessageAuthenticator authenticator;
    private final Map<String, CompletableFuture<ObjectNode>> pendingRequests = new ConcurrentHashMap<>();
    private Runnable onConnectHook;
    
    // Use AtomicReference for thread-safe hot-reload of SSL context
    private final AtomicReference<SSLContext> sslContextRef = new AtomicReference<>();
    private volatile String activeCertThumbprint = "N/A";

    public P2PMessageBus(int port) {
        super(new InetSocketAddress(port));
        this.authenticator = MessageAuthenticator.getInstance();
    }

    /**
     * Updates the SSLContext used for mTLS and the active certificate thumbprint.
     */
    public void updateSSLContext(SSLContext newContext, String thumbprint) {
        this.sslContextRef.set(newContext);
        this.activeCertThumbprint = (thumbprint != null) ? thumbprint : "N/A";
        
        if (newContext != null) {
            this.setWebSocketFactory(new DefaultSSLWebSocketServerFactory(newContext));
            System.out.println("P2P Message Bus: SSLContext updated. Active Thumbprint: " + activeCertThumbprint);
        } else {
            this.setWebSocketFactory(new org.java_websocket.server.DefaultWebSocketServerFactory());
            System.out.println("P2P Message Bus: SSLContext cleared (PLAIN mode).");
        }

        // Gracefully reconnect outbound peers to apply new mTLS identity
        reconnectExecutor.submit(() -> {
            for (String uri : new ArrayList<>(connectedPeerUris)) {
                disconnectPeer(uri);
                connectToPeer(uri);
            }
        });
    }

    public void updateSSLContext(SSLContext newContext) {
        updateSSLContext(newContext, computeThumbprint(newContext));
    }

    public String getActiveCertThumbprint() {
        return activeCertThumbprint;
    }

    private String computeThumbprint(SSLContext context) {
        if (context == null) return "N/A";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(String.valueOf(context.hashCode()).getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString().toUpperCase();
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    public void setMessageHandler(Consumer<ObjectNode> handler) {
        this.messageHandler = handler;
    }

    public void setOnConnectHook(Runnable hook) {
        this.onConnectHook = hook;
    }

    @Override
    public void onStart() {
        System.out.println("P2P Message Bus started on port: " + getPort());
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        if (onConnectHook != null) onConnectHook.run();
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {}

    @Override
    public void onMessage(WebSocket conn, String message) {
        try {
            ObjectNode parsed = (ObjectNode) mapper.readTree(message);
            if (messageHandler != null) messageHandler.accept(parsed);
        } catch (Exception e) {}
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {}

    public void connectToPeer(String peerUri) {
        if (connectedPeerUris.contains(peerUri)) return;
        connectedPeerUris.add(peerUri);
        
        try {
            WebSocketClient client = new WebSocketClient(new URI(peerUri)) {
                @Override public void onOpen(ServerHandshake handshakedata) {}
                @Override public void onMessage(String message) {}
                @Override public void onClose(int code, String reason, boolean remote) {
                    outboundPeers.remove(this);
                }
                @Override public void onError(Exception ex) {}
            };
            SSLContext ctx = sslContextRef.get();
            if (ctx != null) client.setSocketFactory(ctx.getSocketFactory());
            client.connect();
            outboundPeers.add(client);
        } catch (Exception e) {
            connectedPeerUris.remove(peerUri);
        }
    }

    public void disconnectPeer(String peerUri) {
        connectedPeerUris.remove(peerUri);
    }

    public void broadcast(ObjectNode message) {
        String payload = message.toString();
        for (WebSocket conn : getConnections()) {
            if (conn.isOpen()) conn.send(payload);
        }
        for (WebSocketClient peer : outboundPeers) {
            if (peer.isOpen()) peer.send(payload);
        }
    }

    @Override
    public void stop() throws InterruptedException {
        reconnectExecutor.shutdown();
        super.stop();
    }
}