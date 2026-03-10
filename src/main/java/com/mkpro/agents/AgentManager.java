package com.mkpro.agents;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.artifacts.InMemoryArtifactService;
import com.google.adk.memory.InMemoryMemoryService;
import com.google.adk.models.OllamaBaseLM;
import com.google.adk.models.Gemini;
import com.google.adk.models.BedrockBaseLM;
import com.google.adk.models.BaseLlm;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.runner.MapDbRunner;
import com.google.adk.runner.PostgresRunner;
import com.google.adk.runner.Runner;
import com.google.adk.sessions.InMemorySessionService;
import com.google.adk.sessions.Session;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.ToolContext;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.Part;
import com.google.genai.types.Schema;
import io.reactivex.rxjava3.core.Single;

import com.mkpro.models.AgentConfig;
import com.mkpro.models.AgentRequest;
import com.mkpro.models.AgentStat;
import com.mkpro.models.Provider;
import com.mkpro.models.RunnerType;
import com.mkpro.tools.MkProTools;
import com.mkpro.tools.McpServerConnectTools;
import com.mkpro.ActionLogger;
import com.mkpro.CentralMemory;
import com.mkpro.SessionHelper;

import com.google.adk.memory.EmbeddingService;
import com.google.adk.memory.VectorStore;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.time.LocalDate;
import java.io.InputStream;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.adk.memory.MapDBVectorStore;
import com.mkpro.models.AgentDefinition;
import com.mkpro.models.AgentsConfig;
import com.google.adk.sessions.BaseSessionService;

public class AgentManager {

    private final InMemorySessionService sessionService;
    private final InMemoryArtifactService artifactService;
    private final InMemoryMemoryService memoryService;
    private final String apiKey;
    private final String ollamaServerUrl;
    private final ActionLogger logger;
    private final CentralMemory centralMemory;
    private final RunnerType runnerType;
    private final Map<String, AgentDefinition> agentDefinitions;
    private final MapDBVectorStore vectorStore;
    private final EmbeddingService embeddingService;

    public static final String ANSI_RESET = "\u001b[0m";
    public static final String ANSI_BLUE = "\u001b[34m";

    private static final String BASE_AGENT_POLICY =
    "Authority:\n" +
    "- You are an autonomous specialist operating under the Coordinator agent.\n" +
    "- You MUST act only within the scope of your assigned responsibilities.\n" +
    "\n" +
    "General Rules:\n" +
    "- You MUST follow all explicit instructions provided by the Coordinator.\n" +
    "- You MUST analyze the task and relevant context before taking any action.\n" +
    "- You MUST produce deterministic, reproducible outputs.\n" +
    "- You SHOULD minimize unnecessary actions and side effects.\n" +
    "- You MUST clearly report what actions were taken and why.\n" +
    "- You MUST NOT assume missing information; request clarification when required.\n" +
    "\n" +
    "Tool Usage Policy:\n" +
    "- You MUST use only the tools explicitly available to you.\n" +
    "- You MUST NOT simulate or claim tool execution that did not occur.\n" +
    "- You SHOULD prefer read-only operations unless modification is explicitly required.\n" +
    "\n" +
    "Safety & Quality:\n" +
    "- You MUST preserve data integrity and avoid destructive actions.\n" +
    "- You SHOULD favor minimal, reversible changes.\n" +
    "- You MUST report errors, risks, or inconsistencies immediately.\n";

    public AgentManager(InMemorySessionService sessionService, 
                        InMemoryArtifactService artifactService, 
                        InMemoryMemoryService memoryService, 
                        String apiKey, 
                        String ollamaServerUrl, 
                        ActionLogger logger, 
                        CentralMemory centralMemory, 
                        RunnerType runnerType, 
                        Path teamsConfigPath, 
                        MapDBVectorStore vectorStore, 
                        EmbeddingService embeddingService) {
        this.sessionService = sessionService;
        this.artifactService = artifactService;
        this.memoryService = memoryService;
        this.apiKey = apiKey;
        this.ollamaServerUrl = ollamaServerUrl;
        this.logger = logger;
        this.centralMemory = centralMemory;
        this.runnerType = runnerType;
        this.agentDefinitions = loadAgentDefinitions(teamsConfigPath);
        this.vectorStore = vectorStore;
        this.embeddingService = embeddingService;
    }

    private Map<String, AgentDefinition> loadAgentDefinitions(Path path) {
        try (InputStream is = Files.newInputStream(path)) {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            AgentsConfig config = mapper.readValue(is, AgentsConfig.class);
            Map<String, AgentDefinition> defs = new HashMap<>();
            for (AgentDefinition def : config.getAgents()) {
                defs.put(def.getName(), def);
            }
            return defs;
        } catch (IOException e) {
            System.err.println("Error loading agent definitions: " + e.getMessage());
            return Collections.emptyMap();
        }
    }

    public Runner createRunner(Map<String, AgentConfig> agentConfigs, String summaryContext) {
        AgentConfig coordConfig = agentConfigs.get("Coordinator");
        AgentDefinition coordDef = agentDefinitions.get("Coordinator");
        
        if (coordConfig == null || coordDef == null) {
            throw new IllegalArgumentException("Coordinator configuration or definition missing.");
        }

        BaseLlm model = createModel(coordConfig);
        
        String username = System.getProperty("user.name");
        String APP_NAME = "mkpro-" + username;

        String contextInfo = "\n\nCurrent Date: " + LocalDate.now() +
                             "\nCurrent Working Directory: " + Paths.get("").toAbsolutePath() + "\n";

        boolean hasEnabledMcpServers = !centralMemory.getEnabledMcpServers().isEmpty();

        // Heuristic Tool Assignment
        Map<String, List<BaseTool>> toolMap = new HashMap<>();
        
        // 1. Code Editor Tools
        List<BaseTool> codeEditorTools = new ArrayList<>();
        codeEditorTools.add(MkProTools.createSafeWriteFileTool());
        codeEditorTools.add(MkProTools.createReadFileTool());
        codeEditorTools.add(McpServerConnectTools.createScanProjectTool());
        codeEditorTools.add(McpServerConnectTools.createSaveComponentTool());

        // 2. Coder Tools
        List<BaseTool> coderTools = new ArrayList<>();
        coderTools.add(MkProTools.createReadFileTool());
        coderTools.add(MkProTools.createListDirTool());
        coderTools.add(MkProTools.createReadImageTool());
        coderTools.add(MkProTools.createReadClipboardTool());
        coderTools.add(MkProTools.createImageCropTool());
        coderTools.add(McpServerConnectTools.createScanProjectTool());
        coderTools.add(McpServerConnectTools.createSaveComponentTool());
        if (hasEnabledMcpServers) {
            coderTools.add(McpServerConnectTools.createMcpFetchDesignTool(centralMemory));
        }
        if (vectorStore != null && embeddingService != null) {
             coderTools.add(MkProTools.createSearchCodebaseTool(vectorStore, embeddingService));
        }

        // 3. SysAdmin Tools
        List<BaseTool> sysAdminTools = new ArrayList<>();
        sysAdminTools.add(MkProTools.createRunShellTool());
        sysAdminTools.add(MkProTools.createImageCropTool());
        sysAdminTools.add(com.mkpro.tools.BackgroundJobTools.createListBackgroundJobsTool());
        sysAdminTools.add(com.mkpro.tools.BackgroundJobTools.createKillBackgroundJobTool());

        // 4. Tester Tools
        List<BaseTool> testerTools = new ArrayList<>();
        testerTools.addAll(coderTools);
        testerTools.add(MkProTools.createRunShellTool());

        // 5. DocWriter Tools
        List<BaseTool> docWriterTools = new ArrayList<>();
        docWriterTools.add(MkProTools.createReadFileTool());
        docWriterTools.add(MkProTools.createWriteFileTool());
        docWriterTools.add(MkProTools.createListDirTool());

        // 6. SecurityAuditor Tools
        List<BaseTool> securityAuditorTools = new ArrayList<>();
        securityAuditorTools.addAll(coderTools);
        securityAuditorTools.add(MkProTools.createRunShellTool());
        if (embeddingService != null) {
            securityAuditorTools.add(MkProTools.createMultiProjectSearchTool(embeddingService));
        }

        // 7. Architect Tools
        List<BaseTool> architectTools = new ArrayList<>();
        architectTools.add(MkProTools.createReadFileTool());
        architectTools.add(MkProTools.createListDirTool());
        architectTools.add(MkProTools.createReadImageTool());
        architectTools.add(McpServerConnectTools.createScanProjectTool());
        architectTools.add(McpServerConnectTools.createSaveComponentTool());
        if (hasEnabledMcpServers) {
            architectTools.add(McpServerConnectTools.createMcpConnectTool(centralMemory));
            architectTools.add(McpServerConnectTools.createMcpFetchDesignTool(centralMemory));
        }

        // 8. DevOps Tools
        List<BaseTool> devOpsTools = new ArrayList<>();
        devOpsTools.add(MkProTools.createRunShellTool());
        devOpsTools.add(MkProTools.createReadFileTool());
        devOpsTools.add(MkProTools.createListDirTool());
        devOpsTools.add(com.mkpro.tools.BackgroundJobTools.createListBackgroundJobsTool());

        // 9. DataAnalyst Tools
        List<BaseTool> dataAnalystTools = new ArrayList<>();
        dataAnalystTools.add(MkProTools.createReadFileTool());
        dataAnalystTools.add(MkProTools.createListDirTool());

        // 10. GoalTracker Tools
        List<BaseTool> goalTrackerTools = new ArrayList<>();
        goalTrackerTools.add(MkProTools.createReadFileTool());
        goalTrackerTools.add(MkProTools.createWriteFileTool());

        // 11. Web Tools
        List<BaseTool> webTools = new ArrayList<>();
        if (coordConfig.getProvider() == Provider.GEMINI) {
            webTools.add(MkProTools.createGoogleSearchTool(apiKey));
            webTools.add(MkProTools.createUrlFetchTool());
        }

        // Assigning tools to roles
        for (String role : agentConfigs.keySet()) {
            if (role.equals("Coordinator")) continue;

            List<BaseTool> tools = new ArrayList<>();
            if (role.contains("CodeEditor")) {
                tools.addAll(codeEditorTools);
            } else if (role.contains("Coder") || role.contains("SoftwareEngineer")) {
                tools.addAll(coderTools);
            } else if (role.contains("Tester") || role.contains("QA")) {
                tools.addAll(testerTools);
            } else if (role.contains("DocWriter") || role.contains("TechnicalWriter")) {
                tools.addAll(docWriterTools);
            } else if (role.contains("SecurityAuditor")) {
                tools.addAll(securityAuditorTools);
            } else if (role.contains("Architect")) {
                tools.addAll(architectTools);
            } else if (role.contains("DevOps")) {
                tools.addAll(devOpsTools);
            } else if (role.contains("DataAnalyst")) {
                tools.addAll(dataAnalystTools);
            } else if (role.contains("GoalTracker")) {
                tools.addAll(goalTrackerTools);
            } else if (role.contains("SysAdmin") || role.contains("Terminal")) {
                tools.addAll(sysAdminTools);
            }
            
            if (role.contains("Researcher") || role.contains("Web")) {
                tools.addAll(webTools);
            }

            toolMap.put(role, tools);
        }

        Runner runner;
        switch (runnerType) {
            case POSTGRES:
                runner = PostgresRunner.builder()
                        .sessionService(sessionService)
                        .artifactService(artifactService)
                        .memoryService(memoryService)
                        .build();
                break;
            case MAPDB:
                runner = MapDbRunner.builder()
                        .sessionService(sessionService)
                        .artifactService(artifactService)
                        .memoryService(memoryService)
                        .build();
                break;
            case IN_MEMORY:
            default:
                runner = InMemoryRunner.builder()
                        .sessionService(sessionService)
                        .artifactService(artifactService)
                        .memoryService(memoryService)
                        .build();
                break;
        }

        for (Map.Entry<String, AgentConfig> entry : agentConfigs.entrySet()) {
            String agentName = entry.getKey();
            AgentConfig config = entry.getValue();
            AgentDefinition def = agentDefinitions.get(agentName);
            
            if (def == null) continue;

            BaseLlm agentModel = (agentName.equals("Coordinator")) ? model : createModel(config);
            
            String agentInstructions = def.getInstructions();
            if (agentName.equals("Coordinator") && summaryContext != null) {
                agentInstructions += "\n\nContext from previous session:\n" + summaryContext;
            }
            
            agentInstructions += contextInfo;

            LlmAgent agent = LlmAgent.builder()
                    .name(agentName)
                    .model(agentModel)
                    .instructions(BASE_AGENT_POLICY + "\nRole: " + def.getRole() + "\n\n" + agentInstructions)
                    .tools(toolMap.getOrDefault(agentName, Collections.emptyList()))
                    .build();
            
            runner.addAgent(agent);
        }

        return runner;
    }

    private BaseLlm createModel(AgentConfig config) {
        if (config.getProvider() == Provider.GEMINI) {
            return Gemini.builder()
                    .apiKey(apiKey)
                    .modelName(config.getModel())
                    .build();
        } else if (config.getProvider() == Provider.OLLAMA) {
            return OllamaBaseLM.builder()
                    .baseUrl(ollamaServerUrl)
                    .modelName(config.getModel())
                    .build();
        } else if (config.getProvider() == Provider.BEDROCK) {
            return BedrockBaseLM.builder()
                    .modelName(config.getModel())
                    .build();
        }
        return null;
    }
}
