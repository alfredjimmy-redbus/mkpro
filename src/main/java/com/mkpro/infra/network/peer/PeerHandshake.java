package com.mkpro.infra.network.peer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mkpro.CentralMemory;
import com.mkpro.infra.network.discovery.NetworkPeerRegistry;
import com.mkpro.infra.network.messaging.P2PMessageBus;
import com.mkpro.tools.McpProjectScanner;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * PeerHandshake manages the PEER_HELLO protocol.
 * Enhanced to exchange mTLS certificate thumbprints for rotation verification.
 */
public class PeerHandshake {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String ANSI_CYAN = "\u001b[36m";
    private static final String ANSI_RESET = "\u001b[0m";

    private final P2PMessageBus messageBus;
    private final String instanceId;
    private final String projectName;
    private final String projectType;
    private final String projectDescription;
    private final List<String> availableAgents;
    private final String primaryModel;
    private final CentralMemory centralMemory;

    public PeerHandshake(P2PMessageBus messageBus, String instanceId,
                          List<String> availableAgents, String primaryModel,
                          CentralMemory centralMemory) {
        this.messageBus = messageBus;
        this.instanceId = instanceId;
        this.availableAgents = availableAgents;
        this.primaryModel = primaryModel;
        this.centralMemory = centralMemory;

        // Auto-detect project
        McpProjectScanner.ProjectInfo project = McpProjectScanner.detectProject(Paths.get("").toAbsolutePath());
        this.projectName = project.root.getFileName().toString();
        this.projectType = project.type;

        String projectPath = System.getProperty("user.dir");
        String memory = centralMemory != null ? (String) centralMemory.getMemory(projectPath) : "";
        if (memory != null && !memory.isEmpty()) {
            this.projectDescription = memory.length() > 200 ? memory.substring(0, 200) + "..." : memory;
        } else {
            this.projectDescription = "No description available.";
        }
    }

    public void sendHello() {
        messageBus.broadcast(buildHelloMessage());
    }

    public static void handleHello(ObjectNode message, CentralMemory centralMemory) {
        String peerId = message.has("instance_id") ? message.get("instance_id").asText() : "unknown";
        String projectName = message.has("project_name") ? message.get("project_name").asText() : "unknown";
        String thumbprint = message.has("cert_thumbprint") ? message.get("cert_thumbprint").asText() : "N/A";

        // Store peer thumbprint in CentralMemory for SyncEngine to check "100% Mesh"
        if (centralMemory != null) {
            centralMemory.putMemory("peer." + peerId + ".thumbprint", thumbprint);
        }

        List<String> agents = new ArrayList<>();
        if (message.has("agents") && message.get("agents").isArray()) {
            for (var node : message.get("agents")) {
                agents.add(node.asText());
            }
        }

        NetworkPeerRegistry registry = NetworkPeerRegistry.getInstance();
        registry.updatePeerInfo(peerId, projectName, 
            message.has("project_type") ? message.get("project_type").asText() : "unknown", 
            agents, 
            message.has("model") ? message.get("model").asText() : "unknown");

        System.out.println(ANSI_CYAN + "  ✓ Peer hello: " + peerId + " [Thumbprint: " + thumbprint + "]" + ANSI_RESET);
    }

    public ObjectNode buildHelloMessage() {
        ObjectNode hello = mapper.createObjectNode();
        hello.put("type", "PEER_HELLO");
        hello.put("instance_id", instanceId);
        hello.put("project_name", projectName);
        hello.put("cert_thumbprint", messageBus.getActiveCertThumbprint());
        hello.put("timestamp", System.currentTimeMillis());

        ArrayNode agentsArray = mapper.createArrayNode();
        for (String agent : availableAgents) {
            agentsArray.add(agent);
        }
        hello.set("agents", agentsArray);
        return hello;
    }
}