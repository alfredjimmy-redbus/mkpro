package com.mkpro;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;

public class InstanceRegistry {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Path REGISTRY_PATH = Paths.get(System.getProperty("user.home"), ".mkpro", "instances.json");

    public static int findAvailablePort(int startPort) {
        for (int port = startPort; port < startPort + 100; port++) {
            try (ServerSocket ignored = new ServerSocket(port)) {
                return port;
            } catch (IOException ignored) {}
        }
        throw new RuntimeException("No available ports found starting from " + startPort);
    }

    public static void registerInstance(String name, int httpPort, int wsPort) {
        try {
            cleanupStaleEntries();
            File registryFile = REGISTRY_PATH.toFile();
            ArrayNode root;
            if (registryFile.exists()) {
                root = (ArrayNode) mapper.readTree(registryFile);
            } else {
                Files.createDirectories(REGISTRY_PATH.getParent());
                root = mapper.createArrayNode();
            }

            ObjectNode instance = mapper.createObjectNode();
            instance.put("name", name);
            instance.put("pid", ProcessHandle.current().pid());
            instance.put("httpPort", httpPort);
            instance.put("wsPort", wsPort);
            instance.put("workingDir", System.getProperty("user.dir"));
            instance.put("startTime", System.currentTimeMillis());

            root.add(instance);
            mapper.writerWithDefaultPrettyPrinter().writeValue(registryFile, root);
        } catch (IOException e) {
            System.err.println("Failed to register instance: " + e.getMessage());
        }
    }

    public static void unregisterInstance(String name) {
        try {
            File registryFile = REGISTRY_PATH.toFile();
            if (!registryFile.exists()) return;

            ArrayNode root = (ArrayNode) mapper.readTree(registryFile);
            long currentPid = ProcessHandle.current().pid();
            
            Iterator<com.fasterxml.jackson.databind.JsonNode> it = root.elements();
            while (it.hasNext()) {
                ObjectNode node = (ObjectNode) it.next();
                if (node.get("pid").asLong() == currentPid) {
                    it.remove();
                }
            }
            mapper.writerWithDefaultPrettyPrinter().writeValue(registryFile, root);
        } catch (IOException e) {
            System.err.println("Failed to unregister instance: " + e.getMessage());
        }
    }

    public static void cleanupStaleEntries() {
        try {
            File registryFile = REGISTRY_PATH.toFile();
            if (!registryFile.exists()) return;

            ArrayNode root = (ArrayNode) mapper.readTree(registryFile);
            ArrayNode newRoot = mapper.createArrayNode();

            for (com.fasterxml.jackson.databind.JsonNode node : root) {
                long pid = node.get("pid").asLong();
                if (ProcessHandle.of(pid).isPresent()) {
                    newRoot.add(node);
                }
            }

            if (root.size() != newRoot.size()) {
                mapper.writerWithDefaultPrettyPrinter().writeValue(registryFile, newRoot);
            }
        } catch (IOException ignored) {}
    }
}
