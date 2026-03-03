package com.mkpro;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public class LogHttpServer {

    private final int port;
    private final int wsPort;
    private HttpServer server;
    private final Gson gson = new Gson();

    public LogHttpServer(int port, int wsPort) {
        this.port = port;
        this.wsPort = wsPort;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/logs", this::handleLogsRequest);
        server.start();
        ActionLogger.logAction("LogHttpServer started on port " + port + " (WS port: " + wsPort + ")");
    }

    public void stop() {
        if (server != null) {
            server.stop(1);
            ActionLogger.logAction("LogHttpServer stopped");
        }
    }

    private void handleLogsRequest(HttpExchange exchange) {
        try {
            // CORS Headers
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            // Fetch logs from ActionLogger (static)
            List<String> logs = ActionLogger.getAllLogs();
            
            // Construct response
            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put("wsPort", wsPort);
            responseMap.put("logs", logs);

            String json = gson.toJson(responseMap);
            byte[] responseBytes = json.getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        } catch (Exception e) {
            try {
                byte[] errorBytes = "{\"error\":\"Internal Server Error\"}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(500, errorBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(errorBytes);
                }
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }
    }
}
