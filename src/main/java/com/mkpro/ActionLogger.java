package com.mkpro;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;
import com.mkpro.SimpleWebSocketServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.IOException;
import java.time.LocalDateTime;

public class ActionLogger {
    private static DB db;
    private static List<String> logs;
    private static SimpleWebSocketServer wsServer;
    private static final ObjectMapper mapper = new ObjectMapper();
    
    private static final List<String> memoryBuffer = Collections.synchronizedList(new ArrayList<>());
    private static final int MAX_BUFFER_SIZE = 500;
    private static final String ACTION_LOG_FILE = "action_log.txt";

    public ActionLogger(String dbPath) {
        init(dbPath);
    }

    public void log(String role, String content) {
        logAction(role, content);
    }

    public static synchronized void init(String dbPath) {
        if (db == null || db.isClosed()) {
            db = DBMaker.fileDB(dbPath).make();
            logs = db.indexTreeList("logs", Serializer.STRING).createOrOpen();
        }
    }

    public static synchronized void setWebSocketServer(SimpleWebSocketServer server) {
        wsServer = server;
    }

    public static synchronized void logAction(String role, String content) {
        if (logs == null) return;
        String entry = String.format("[%s] %s: %s", LocalDateTime.now(), role, content);
        logs.add(entry);
        db.commit();
        broadcastLog(entry);
    }

    public static synchronized void logAction(String action) {
        String entry = String.format("[%s] ACTION: %s", LocalDateTime.now(), action);
        
        try (FileWriter fw = new FileWriter(ACTION_LOG_FILE, true);
             PrintWriter pw = new PrintWriter(fw)) {
            pw.println(entry);
        } catch (IOException e) {
            e.printStackTrace();
        }

        memoryBuffer.add(entry);
        synchronized (memoryBuffer) {
            while (memoryBuffer.size() > MAX_BUFFER_SIZE) {
                memoryBuffer.remove(0);
            }
        }

        broadcastLog(entry);
    }

    public static synchronized List<String> getLogs() {
        if (logs == null) return new ArrayList<>();
        return new ArrayList<>(logs);
    }

    public static synchronized List<String> getAllLogs() {
        List<String> combined = getLogs();
        combined.addAll(memoryBuffer);
        return combined;
    }

    public static List<String> getMemoryBuffer() {
        return new ArrayList<>(memoryBuffer);
    }

    public static void clearMemoryLogs() {
        memoryBuffer.clear();
    }
    
    public static synchronized List<String> getRecentLogs(int limit) {
        if (logs == null) return new ArrayList<>();
        int size = logs.size();
        int start = Math.max(0, size - limit);
        List<String> recent = new ArrayList<>();
        for (int i = start; i < size; i++) {
            recent.add(logs.get(i));
        }
        return recent;
    }

    public static synchronized void importLog(String role, String content, String timestamp) {
        if (logs == null) return;
        String entry = String.format("[%s] %s: %s", timestamp, role, content);
        logs.add(entry);
        db.commit();
        broadcastLog(entry);
    }

    private static synchronized void broadcastLog(String entry) {
        if (wsServer != null) {
            try {
                Map<String, String> message = new HashMap<>();
                message.put("type", "log");
                message.put("content", entry);
                String json = mapper.writeValueAsString(message);
                wsServer.broadcast(json);
            } catch (Exception e) {}
        }
    }

    public static synchronized void close() {
        if (db != null && !db.isClosed()) {
            db.close();
        }
    }
}
