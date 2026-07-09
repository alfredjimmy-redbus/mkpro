package com.mkpro.commands.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mkpro.ActionLogger;
import com.mkpro.commands.Command;
import com.mkpro.core.MkProContext;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.mkpro.MkPro.*;

/**
 * Exports actual chat session data from ActionLogger into JSONL training format.
 * Output is written to datajsonl/ folder in the project root.
 * 
 * Format per line:
 * {"messages": [{"role": "system", "content": "..."}, {"role": "user", "content": "..."}, {"role": "assistant", "content": "..."}]}
 * 
 * Usage: /export [coordinator|goaltracker|all]
 */
public class ExportTrainingDataCommand implements Command {

    private static final ObjectMapper mapper = new ObjectMapper();

    // Matches log entries: [2026-07-07T15:30:00.123] ROLE: content
    private static final Pattern LOG_PATTERN = Pattern.compile(
        "^\\[([^\\]]+)\\]\\s+(\\w+):\\s+(.+)$", Pattern.DOTALL
    );

    private static final String COORDINATOR_SYSTEM = 
        "You are the Coordinator agent for mkpro. You orchestrate a team of specialized AI agents. " +
        "Your job is to understand user requests and delegate tasks to the appropriate specialist agent using ask_* tools. " +
        "You do not write code or run commands yourself. Available agents: GoalTracker, Coder, CodeEditor, SysAdmin, " +
        "GitAgent, Tester, DocWriter, SecurityAuditor, Architect, DatabaseAdmin, DevOps, DataAnalyst, AndroidDev, IosDev.";

    private static final String GOALTRACKER_SYSTEM = 
        "You are the GoalTracker agent for mkpro. Your role is to manage project goals and TODO items. " +
        "You create goals, decompose them into sub-goals, track progress, update statuses " +
        "(PENDING, IN_PROGRESS, COMPLETED, FAILED), and report on project progress. " +
        "You maintain a hierarchical goal tree.";

    @Override
    public void execute(String[] args, MkProContext context) throws Exception {
        String target = args.length > 0 ? args[0].toLowerCase() : "all";

        Path outputDir = Paths.get("").toAbsolutePath().resolve("datajsonl");
        Files.createDirectories(outputDir);

        List<String> logs = ActionLogger.getLogs();
        if (logs.isEmpty()) {
            System.out.println(ANSI_YELLOW + "No session logs found to export." + ANSI_RESET);
            return;
        }

        System.out.println(ANSI_BLUE + "Parsing " + logs.size() + " log entries..." + ANSI_RESET);

        // Parse logs into structured entries
        List<LogEntry> entries = parseLogs(logs);

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        int exported = 0;

        if ("coordinator".equals(target) || "all".equals(target)) {
            int count = exportCoordinatorData(entries, outputDir, timestamp);
            exported += count;
            System.out.println(ANSI_GREEN + "  Coordinator: " + count + " training examples" + ANSI_RESET);
        }

        if ("goaltracker".equals(target) || "all".equals(target)) {
            int count = exportGoalTrackerData(entries, outputDir, timestamp);
            exported += count;
            System.out.println(ANSI_GREEN + "  GoalTracker: " + count + " training examples" + ANSI_RESET);
        }

        if (exported == 0) {
            System.out.println(ANSI_YELLOW + "No conversation pairs found in logs. Chat more and try again." + ANSI_RESET);
        } else {
            System.out.println(ANSI_GREEN + "Exported " + exported + " total training examples to datajsonl/" + ANSI_RESET);
        }
    }

    private int exportCoordinatorData(List<LogEntry> entries, Path outputDir, String timestamp) throws IOException {
        Path file = outputDir.resolve("coordinator_session_" + timestamp + ".jsonl");
        List<ObjectNode> trainingLines = new ArrayList<>();

        // Strategy 1: Find USER→Coordinator response pairs (new logging format)
        for (int i = 0; i < entries.size() - 1; i++) {
            LogEntry current = entries.get(i);

            if (isUserEntry(current)) {
                String userContent = current.content;

                // Find the next Coordinator response
                for (int j = i + 1; j < entries.size(); j++) {
                    LogEntry next = entries.get(j);
                    if ("Coordinator".equalsIgnoreCase(next.role)) {
                        trainingLines.add(createTrainingLine(COORDINATOR_SYSTEM, userContent, next.content));
                        break;
                    }
                    if (isUserEntry(next)) break;
                }
            }
        }

        // Strategy 2: Extract delegation patterns from SYSTEM logs (existing format)
        // Pattern: SYSTEM "Delegating task to X" followed by agent response = shows routing behavior
        for (int i = 0; i < entries.size() - 1; i++) {
            LogEntry entry = entries.get(i);
            if ("SYSTEM".equalsIgnoreCase(entry.role) && entry.content.contains("Delegating task to")) {
                // Find the agent response that follows
                for (int j = i + 1; j < entries.size(); j++) {
                    LogEntry next = entries.get(j);
                    if (!next.role.equalsIgnoreCase("SYSTEM") && !next.role.equalsIgnoreCase("INFO")) {
                        // We have a delegation→response pair
                        // Synthesize the user intent from the delegation message
                        String agentName = next.role;
                        String delegationNote = "I'll delegate this to the " + agentName + ".";
                        String fullResponse = delegationNote + "\n\n" + next.content;
                        
                        // Find preceding user input if available
                        String userContent = findPrecedingUserInput(entries, i);
                        if (userContent != null) {
                            trainingLines.add(createTrainingLine(COORDINATOR_SYSTEM, userContent, fullResponse));
                        }
                        break;
                    }
                }
            }
        }

        writeJsonl(file, trainingLines);
        return trainingLines.size();
    }

    private int exportGoalTrackerData(List<LogEntry> entries, Path outputDir, String timestamp) throws IOException {
        Path file = outputDir.resolve("goaltracker_session_" + timestamp + ".jsonl");
        List<ObjectNode> trainingLines = new ArrayList<>();

        // Find GoalTracker interactions
        for (int i = 0; i < entries.size(); i++) {
            LogEntry entry = entries.get(i);
            if ("GoalTracker".equalsIgnoreCase(entry.role)) {
                // Find the preceding user/coordinator input that triggered this
                String userContent = findPrecedingUserInput(entries, i);
                if (userContent == null) {
                    // Look for Coordinator instruction to GoalTracker
                    for (int j = i - 1; j >= Math.max(0, i - 5); j--) {
                        LogEntry prev = entries.get(j);
                        if ("SYSTEM".equalsIgnoreCase(prev.role) && prev.content.toLowerCase().contains("goal")) {
                            userContent = prev.content;
                            break;
                        }
                    }
                }
                if (userContent != null && !entry.content.trim().isEmpty()) {
                    trainingLines.add(createTrainingLine(GOALTRACKER_SYSTEM, userContent, entry.content));
                }
            }
        }

        writeJsonl(file, trainingLines);
        return trainingLines.size();
    }

    private List<LogEntry> parseLogs(List<String> rawLogs) {
        List<LogEntry> entries = new ArrayList<>();
        StringBuilder currentContent = new StringBuilder();
        String currentRole = null;
        String currentTimestamp = null;

        for (String line : rawLogs) {
            Matcher m = LOG_PATTERN.matcher(line);
            if (m.matches()) {
                // Save previous entry
                if (currentRole != null) {
                    entries.add(new LogEntry(currentTimestamp, currentRole, currentContent.toString().trim()));
                }
                currentTimestamp = m.group(1);
                currentRole = m.group(2);
                currentContent = new StringBuilder(m.group(3));
            } else {
                // Continuation line
                if (currentContent != null) {
                    currentContent.append("\n").append(line);
                }
            }
        }
        // Save last entry
        if (currentRole != null) {
            entries.add(new LogEntry(currentTimestamp, currentRole, currentContent.toString().trim()));
        }

        return entries;
    }

    private boolean isUserEntry(LogEntry entry) {
        String role = entry.role.toLowerCase();
        return "user".equals(role);
    }

    private String findPrecedingUserInput(List<LogEntry> entries, int beforeIndex) {
        for (int j = beforeIndex - 1; j >= Math.max(0, beforeIndex - 10); j--) {
            if (isUserEntry(entries.get(j))) {
                return entries.get(j).content;
            }
        }
        return null;
    }

    private ObjectNode createTrainingLine(String systemPrompt, String userContent, String assistantContent) {
        ObjectNode root = mapper.createObjectNode();
        ArrayNode messages = mapper.createArrayNode();

        ObjectNode system = mapper.createObjectNode();
        system.put("role", "system");
        system.put("content", systemPrompt);
        messages.add(system);

        ObjectNode user = mapper.createObjectNode();
        user.put("role", "user");
        user.put("content", userContent);
        messages.add(user);

        ObjectNode assistant = mapper.createObjectNode();
        assistant.put("role", "assistant");
        assistant.put("content", assistantContent);
        messages.add(assistant);

        root.set("messages", messages);
        return root;
    }

    private void writeJsonl(Path file, List<ObjectNode> lines) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(file)) {
            for (ObjectNode line : lines) {
                writer.write(mapper.writeValueAsString(line));
                writer.newLine();
            }
        }
    }

    @Override
    public String getName() {
        return "export";
    }

    @Override
    public String getDescription() {
        return "Export chat session data as JSONL training data to datajsonl/ folder. Usage: /export [coordinator|goaltracker|all]";
    }

    private static class LogEntry {
        final String timestamp;
        final String role;
        final String content;

        LogEntry(String timestamp, String role, String content) {
            this.timestamp = timestamp;
            this.role = role;
            this.content = content;
        }
    }
}
