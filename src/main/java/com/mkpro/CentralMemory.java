package com.mkpro;

import com.mkpro.models.AgentConfig;
import com.mkpro.models.AgentStat;
import com.mkpro.models.Goal;
import com.mkpro.models.McpServer;
import com.mkpro.models.Provider;
import com.mkpro.utils.PathUtils;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CentralMemory is the source of truth for the application's persistent state.
 */
public class CentralMemory {

    public interface MemoryListener {
        void onUpdate(String key, Object value);
    }

    private final Path sharedDbPath;
    private final DB localDb;
    private final List<MemoryListener> listeners = new ArrayList<>();
    private static CentralMemory instance;

    private final ConcurrentHashMap<String, AgentConfig> configCache = new ConcurrentHashMap<>();
    private volatile List<McpServer> mcpServerCache = null;
    private volatile List<String> ollamaServerCache = null;
    private volatile String selectedOllamaServerCache = null;
    private volatile boolean configCacheLoaded = false;

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 150;

    public static synchronized CentralMemory getInstance() {
        if (instance == null) {
            instance = new CentralMemory();
        }
        return instance;
    }

    public CentralMemory() {
        this(PathUtils.getBaseDocumentsPath().resolve("central_memory.db"),
             resolveLocalDbPath());
    }

    public CentralMemory(Path sharedDbPath, Path localDbPath) {
        this.sharedDbPath = sharedDbPath;
        try {
            PathUtils.ensureDirectoriesExist(sharedDbPath);
            PathUtils.ensureDirectoriesExist(localDbPath);
        } catch (IOException e) {
            System.err.println("[CentralMemory] Warning: could not create directories: " + e.getMessage());
        }

        this.localDb = DBMaker.fileDB(localDbPath.toString())
                .fileMmapEnableIfSupported()
                .transactionEnable()
                .make();

        loadCacheFromShared();
        instance = this;
    }

    private static Path resolveLocalDbPath() {
        Path mkproDir = PathUtils.getProjectPath().resolve(".mkpro");
        String dbName = System.getProperty("mkpro.db.name", "mkpro_data");
        return mkproDir.resolve(dbName + "_local_stats.db");
    }

    public void close() {
        try {
            if (localDb != null && !localDb.isClosed()) {
                localDb.close();
            }
        } catch (Exception e) {
            System.err.println("[CentralMemory] Error closing local DB: " + e.getMessage());
        }
    }

    private DB openSharedDB() {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return DBMaker.fileDB(sharedDbPath.toString())
                        .transactionEnable()
                        .make();
            } catch (Exception e) {
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        return DBMaker.memoryDB().transactionEnable().make();
    }

    private <T> T withSharedDb(java.util.function.Function<DB, T> action) {
        try (DB db = openSharedDB()) {
            return action.apply(db);
        }
    }

    private void withSharedDbVoid(java.util.function.Consumer<DB> action) {
        try (DB db = openSharedDB()) {
            action.accept(db);
        }
    }

    private void loadCacheFromShared() {
        try {
            withSharedDbVoid(db -> {
                Map<String, AgentConfig> configs = db.hashMap("agent_configs", Serializer.STRING, Serializer.JAVA).createOrOpen();
                configCache.putAll(configs);
                configCacheLoaded = true;
            });
        } catch (Exception e) {
            configCacheLoaded = true;
        }
    }

    public void addListener(MemoryListener l) {
        listeners.add(l);
    }

    private void notifyListeners(String key, Object value) {
        for (MemoryListener l : listeners) {
            try {
                l.onUpdate(key, value);
            } catch (Exception e) {}
        }
    }

    public String getMemory(String path) {
        return withSharedDb(db -> {
            Map<String, String> memories = db.hashMap("memories", Serializer.STRING, Serializer.STRING).createOrOpen();
            return memories.getOrDefault(path, "");
        });
    }

    public void saveMemory(String path, String content) {
        withSharedDbVoid(db -> {
            Map<String, String> memories = db.hashMap("memories", Serializer.STRING, Serializer.STRING).createOrOpen();
            memories.put(path, content);
            db.commit();
        });
        notifyListeners("memory:" + path, content);
    }

    /**
     * Alias for saveMemory to improve API usability.
     */
    public void putMemory(String key, String value) {
        saveMemory(key, value);
    }

    public void updateFromRemote(String key, Object value) {
        if (value instanceof String) {
            saveMemory(key, (String) value);
        }
    }

    public void saveAgentStat(AgentStat stat) {
        synchronized (localDb) {
            List<AgentStat> agent_stats = (List<AgentStat>) localDb.indexTreeList("agent_stats", Serializer.JAVA).createOrOpen();
            agent_stats.add(stat);
            localDb.commit();
        }
        notifyListeners("agent_stats", stat);
    }

    public List<McpServer> getMcpServers() {
        return withSharedDb(db -> {
            Map<String, List<McpServer>> mcp_servers = db.hashMap("mcp_servers", Serializer.STRING, Serializer.JAVA).createOrOpen();
            List<McpServer> servers = mcp_servers.get("all");
            return servers != null ? new ArrayList<>(servers) : new ArrayList<>();
        });
    }

    public void saveMcpServers(List<McpServer> servers) {
        withSharedDbVoid(db -> {
            Map<String, List<McpServer>> mcp_servers = db.hashMap("mcp_servers", Serializer.STRING, Serializer.JAVA).createOrOpen();
            mcp_servers.put("all", servers);
            db.commit();
        });
        mcpServerCache = new ArrayList<>(servers);
        notifyListeners("mcp_servers", servers);
    }
}