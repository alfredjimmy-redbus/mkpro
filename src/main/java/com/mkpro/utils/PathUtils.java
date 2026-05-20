package com.mkpro.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PathUtils {

    private static final String APP_DIR = "mkpro";

    public static Path getBaseDocumentsPath() {
        String userHome = System.getProperty("user.home");
        Path documents = Paths.get(userHome, "Documents");
        
        // On Windows, check for OneDrive/Documents first
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            Path oneDriveDocs = Paths.get(userHome, "OneDrive", "Documents");
            if (Files.exists(oneDriveDocs)) {
                documents = oneDriveDocs;
            }
        }
        
        return documents.resolve(APP_DIR);
    }

    public static Path getProjectPath() {
        String projectName = Paths.get("").toAbsolutePath().getFileName().toString();
        return getBaseDocumentsPath().resolve(projectName);
    }

    public static void ensureDirectoriesExist(Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
    }
}
