package com.miniide;

import com.miniide.models.FileNode;
import com.miniide.models.SearchResult;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class FileService {
    private final Path workspaceRoot;

    public FileService(String workspacePath) {
        this.workspaceRoot = Paths.get(workspacePath).toAbsolutePath().normalize();
        initializeWorkspace();
    }

    public Path getWorkspaceRoot() {
        return workspaceRoot;
    }

    private void initializeWorkspace() {
        try {
            if (!Files.exists(workspaceRoot)) {
                Files.createDirectories(workspaceRoot);
                seedWorkspace();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize workspace", e);
        }
    }

    private void seedWorkspace() throws IOException {
        // Create sample directories
        Files.createDirectories(workspaceRoot.resolve("scenes"));
        Files.createDirectories(workspaceRoot.resolve("chars"));
        Files.createDirectories(workspaceRoot.resolve("notes"));

        // Create sample files
        writeFile("scenes/intro.md",
            "# Scene: Introduction\n\n" +
            "## Setting\n" +
            "A dimly lit coffee shop on a rainy evening. The smell of fresh espresso fills the air.\n\n" +
            "## Characters Present\n" +
            "- Mara (protagonist)\n" +
            "- The Stranger\n\n" +
            "## Description\n" +
            "The rain tapped against the window in a steady rhythm. Mara sat alone at her usual table,\n" +
            "nursing a lukewarm latte and staring at the empty seat across from her.\n\n" +
            "\"Is this seat taken?\"\n\n" +
            "She looked up to find a stranger with kind eyes and a worn leather jacket.\n\n" +
            "## Notes\n" +
            "- Establish mysterious atmosphere\n" +
            "- Hint at Mara's loneliness\n" +
            "- Introduce the Stranger's enigmatic nature\n");

        writeFile("scenes/confrontation.md",
            "# Scene: The Confrontation\n\n" +
            "## Setting\n" +
            "The old warehouse district, midnight.\n\n" +
            "## Characters Present\n" +
            "- Mara\n" +
            "- Viktor (antagonist)\n\n" +
            "## Description\n" +
            "The moonlight cast long shadows through the broken skylights.\n" +
            "Mara's footsteps echoed in the cavernous space.\n\n" +
            "\"I know you're here, Viktor.\"\n\n" +
            "Silence. Then a slow clap from the darkness.\n");

        writeFile("chars/mara.md",
            "# Character: Mara Chen\n\n" +
            "## Basic Info\n" +
            "- **Age:** 28\n" +
            "- **Occupation:** Private Investigator\n" +
            "- **Location:** Neo-Seattle\n\n" +
            "## Appearance\n" +
            "- Dark hair, often tied back\n" +
            "- Sharp, observant eyes\n" +
            "- Usually wears practical clothes\n" +
            "- Has a small scar above her left eyebrow\n\n" +
            "## Personality\n" +
            "- Determined and resourceful\n" +
            "- Struggles with trust issues\n" +
            "- Dry sense of humor\n" +
            "- Fiercely loyal to few close friends\n\n" +
            "## Background\n" +
            "Former police detective who left the force after uncovering corruption.\n" +
            "Now works independently, taking cases others won't touch.\n\n" +
            "## Motivations\n" +
            "- Seeking justice for the voiceless\n" +
            "- Finding the truth about her father's disappearance\n");

        writeFile("chars/viktor.md",
            "# Character: Viktor Kozlov\n\n" +
            "## Basic Info\n" +
            "- **Age:** 45\n" +
            "- **Occupation:** Crime Lord / Businessman\n" +
            "- **Location:** Neo-Seattle Underground\n\n" +
            "## Appearance\n" +
            "- Silver hair, slicked back\n" +
            "- Cold blue eyes\n" +
            "- Always impeccably dressed\n" +
            "- Missing the tip of his left pinky finger\n\n" +
            "## Personality\n" +
            "- Calculating and patient\n" +
            "- Believes in twisted sense of order\n" +
            "- Never raises his voice\n" +
            "- Values loyalty above all\n");

        writeFile("notes/todo.txt",
            "Project Todo List\n" +
            "=================\n\n" +
            "[x] Create initial outline\n" +
            "[x] Develop main character Mara\n" +
            "[ ] Write introduction scene\n" +
            "[ ] Develop antagonist Viktor's backstory\n" +
            "[ ] Plan the confrontation scene\n" +
            "[ ] Research Neo-Seattle setting details\n" +
            "[ ] Create supporting cast\n" +
            "[ ] Outline story arc\n\n" +
            "Ideas\n" +
            "-----\n" +
            "- Add a hacker sidekick?\n" +
            "- Explore noir themes\n" +
            "- Consider adding flashback sequences\n");

        writeFile("notes/worldbuilding.md",
            "# World Building: Neo-Seattle\n\n" +
            "## Overview\n" +
            "Set in 2087, Neo-Seattle is a sprawling megacity built on the ruins of old Seattle.\n\n" +
            "## Technology\n" +
            "- Neural interfaces common but regulated\n" +
            "- AI assistants ubiquitous\n" +
            "- Holographic displays replace most screens\n" +
            "- Flying vehicles restricted to commercial use\n\n" +
            "## Social Structure\n" +
            "- Corporate zones (wealthy, controlled)\n" +
            "- Citizen districts (middle class, monitored)\n" +
            "- The Undergrowth (poor, lawless, vibrant)\n\n" +
            "## Weather\n" +
            "Still rains constantly. Some things never change.\n");

        writeFile("README.md",
            "# My Writing Project\n\n" +
            "Welcome to my creative writing workspace!\n\n" +
            "## Structure\n" +
            "- `/scenes` - Story scenes and chapters\n" +
            "- `/chars` - Character profiles\n" +
            "- `/notes` - Planning and worldbuilding notes\n\n" +
            "## Getting Started\n" +
            "Open any file to start editing. Use Ctrl+S to save.\n");
    }

    public Path resolvePath(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            return workspaceRoot;
        }
        Path resolved = workspaceRoot.resolve(relativePath).normalize();
        if (!resolved.startsWith(workspaceRoot)) {
            throw new SecurityException("Path escapes workspace root");
        }
        return resolved;
    }

    public FileNode getTree() throws IOException {
        return buildTree(workspaceRoot, "");
    }

    private FileNode buildTree(Path dir, String relativePath) throws IOException {
        String name = relativePath.isEmpty() ? "workspace" : dir.getFileName().toString();
        FileNode node = new FileNode(name, relativePath, "folder");

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            List<FileNode> folders = new ArrayList<>();
            List<FileNode> files = new ArrayList<>();

            for (Path entry : stream) {
                String childRelPath = relativePath.isEmpty()
                    ? entry.getFileName().toString()
                    : relativePath + "/" + entry.getFileName().toString();

                if (Files.isDirectory(entry)) {
                    folders.add(buildTree(entry, childRelPath));
                } else {
                    files.add(new FileNode(entry.getFileName().toString(), childRelPath, "file"));
                }
            }

            folders.sort(Comparator.comparing(FileNode::getName, String.CASE_INSENSITIVE_ORDER));
            files.sort(Comparator.comparing(FileNode::getName, String.CASE_INSENSITIVE_ORDER));

            folders.forEach(node::addChild);
            files.forEach(node::addChild);
        }

        return node;
    }

    public String readFile(String relativePath) throws IOException {
        Path path = resolvePath(relativePath);
        if (!Files.exists(path)) {
            throw new FileNotFoundException("File not found: " + relativePath);
        }
        if (Files.isDirectory(path)) {
            throw new IOException("Cannot read directory as file");
        }
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    public void writeFile(String relativePath, String content) throws IOException {
        Path path = resolvePath(relativePath);
        Files.createDirectories(path.getParent());
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
    }

    public void createFileOrFolder(String relativePath, String type, String initialContent) throws IOException {
        Path path = resolvePath(relativePath);
        if (Files.exists(path)) {
            throw new IOException("Path already exists: " + relativePath);
        }
        if ("folder".equals(type)) {
            Files.createDirectories(path);
        } else {
            Files.createDirectories(path.getParent());
            String content = initialContent != null ? initialContent : "";
            Files.write(path, content.getBytes(StandardCharsets.UTF_8));
        }
    }

    public void deleteFileOrFolder(String relativePath) throws IOException {
        Path path = resolvePath(relativePath);
        if (!Files.exists(path)) {
            throw new FileNotFoundException("Path not found: " + relativePath);
        }
        if (Files.isDirectory(path)) {
            try (Stream<Path> walk = Files.walk(path)) {
                walk.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
            }
        } else {
            Files.delete(path);
        }
    }

    public void rename(String fromPath, String toPath) throws IOException {
        Path from = resolvePath(fromPath);
        Path to = resolvePath(toPath);
        if (!Files.exists(from)) {
            throw new FileNotFoundException("Source not found: " + fromPath);
        }
        if (Files.exists(to)) {
            throw new IOException("Target already exists: " + toPath);
        }
        Files.createDirectories(to.getParent());
        Files.move(from, to);
    }

    public List<SearchResult> search(String query) throws IOException {
        List<SearchResult> results = new ArrayList<>();
        if (query == null || query.trim().isEmpty()) {
            return results;
        }

        String lowerQuery = query.toLowerCase();

        try (Stream<Path> walk = Files.walk(workspaceRoot)) {
            walk.filter(Files::isRegularFile)
                .forEach(file -> {
                    try {
                        String relativePath = workspaceRoot.relativize(file).toString().replace('\\', '/');
                        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                        for (int i = 0; i < lines.size(); i++) {
                            String line = lines.get(i);
                            if (line.toLowerCase().contains(lowerQuery)) {
                                String preview = line.trim();
                                if (preview.length() > 100) {
                                    preview = preview.substring(0, 100) + "...";
                                }
                                results.add(new SearchResult(relativePath, i + 1, preview));
                            }
                        }
                    } catch (IOException e) {
                        // Skip unreadable files
                    }
                });
        }

        return results;
    }
}
