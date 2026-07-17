package com.mkpro.core;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mkpro.CentralMemory;
import com.mkpro.infra.network.discovery.DiscoveryService;
import com.mkpro.infra.network.discovery.NetworkPeerRegistry;
import com.mkpro.infra.network.messaging.P2PMessageBus;
import com.mkpro.infra.network.peer.PeerAgentRequestHandler;
import com.mkpro.infra.network.peer.PeerHandshake;
import com.mkpro.infra.network.sync.SyncEngine;
import com.mkpro.graph.MapDbGraphRepository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * BootstrapService initializes core application components.
 */
public class BootstrapService {

    private final MkProContext context;

    public BootstrapService(MkProContext context) {
        this.context = context;
    }

    public void bootstrap() {
        System.out.println("Bootstrapping mkpro components...");
        
        // Initialize P2P Message Bus
        int p2pPort = Integer.parseInt(System.getProperty("mkpro.p2p.port", "9000"));
        P2PMessageBus bus = new P2PMessageBus(p2pPort);
        context.setP2pMessageBus(bus);

        // Initialize SyncEngine
        SyncEngine syncEngine = new SyncEngine(bus, context.getCentralMemory(), context.getRepository());
        
        // Setup message routing
        bus.setMessageHandler(message -> {
            String type = message.has("type") ? message.get("type").asText() : "";
            
            if ("PEER_HELLO".equals(type)) {
                // Fixed call to handleHello with CentralMemory
                PeerHandshake.handleHello(message, context.getCentralMemory());
            } else if (type.endsWith("_SYNC") || "CA_UPDATE".equals(type) || "TRUST_CONTRACTION".equals(type)) {
                syncEngine.processIncomingMessage(message);
            } else if (type.startsWith("AGENT_")) {
                PeerAgentRequestHandler.handle(message, context);
            }
        });

        // Start Discovery
        try {
            DiscoveryService discovery = new DiscoveryService();
            discovery.setMessageBus(bus);
            discovery.start(p2pPort, context.getInstanceId());
            context.setDiscoveryService(discovery);
        } catch (IOException e) {
            System.err.println("Failed to start discovery: " + e.getMessage());
        }

        // Start the P2P Bus
        bus.start();
        
        // Send initial handshake
        List<String> agents = new ArrayList<>(context.getAgentManager().getAvailableAgents());
        PeerHandshake handshake = new PeerHandshake(bus, context.getInstanceId(), agents, "gpt-4o", context.getCentralMemory());
        handshake.sendHello();
    }
}