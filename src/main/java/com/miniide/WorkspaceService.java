package com.miniide;

import com.miniide.models.FileNode;
import com.miniide.models.SearchResult;
import com.miniide.models.TextEdit;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Centralized service for all workspace filesystem operations.
 * All paths are workspace-relative; absolute paths are resolved internally.
 */
public class WorkspaceService {

    private final Path workspaceRoot;

    public WorkspaceService(Path workspaceRoot) {
        this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
        log("WorkspaceService initialized with root: " + this.workspaceRoot);
    }

    // -------------------------------------------------------------------------
    // Path Resolution
    // -------------------------------------------------------------------------

    /**
     * Returns the absolute path to the workspace root.
     */
    public Path getWorkspaceRoot() {
        return workspaceRoot;
    }

    /**
     * Resolves a workspace-relative path to an absolute path.
     * Validates that the resolved path stays within the workspace root.
     *
     * @param relativePath workspace-relative path (empty or null means root)
     * @return absolute path within workspace
     * @throws SecurityException if path escapes workspace root
     */
    public Path resolvePath(String relativePath) {
        if (relativePath == null || relativePath.isBlank() || ".".equals(relativePath)) {
            return workspaceRoot;
        }

        // Normalize separators
        String normalized = relativePath.replace('\\', '/');

        // Strip leading slash - treat "/chars" as workspace-relative "chars"
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }

        // Handle empty after stripping
        if (normalized.isEmpty()) {
            return workspaceRoot;
        }

        Path resolved = workspaceRoot.resolve(normalized).normalize();
        if (!resolved.startsWith(workspaceRoot)) {
            throw new SecurityException("Path escapes workspace root: " + relativePath);
        }
        return resolved;
    }

    /**
     * Converts an absolute path to a workspace-relative path.
     */
    public String toRelativePath(Path absolutePath) {
        return workspaceRoot.relativize(absolutePath).toString().replace('\\', '/');
    }

    // -------------------------------------------------------------------------
    // Directory Listing
    // -------------------------------------------------------------------------

    /**
     * Lists entries in a directory.
     *
     * @param relativePath workspace-relative path to directory (empty for root)
     * @return FileNode representing the directory with its children
     * @throws IOException if the path doesn't exist or isn't a directory
     */
    public FileNode listEntries(String relativePath) throws IOException {
        Path dir = resolvePath(relativePath);
        if (!Files.exists(dir)) {
            throw new FileNotFoundException("Directory not found: " + relativePath);
        }
        if (!Files.isDirectory(dir)) {
            throw new IOException("Path is not a directory: " + relativePath);
        }

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
                    // For listing, we don't recurse - just mark as folder
                    folders.add(new FileNode(entry.getFileName().toString(), childRelPath, "folder"));
                } else {
                    files.add(new FileNode(entry.getFileName().toString(), childRelPath, "file"));
                }
            }

            // Sort alphabetically (case-insensitive), folders first
            folders.sort(Comparator.comparing(FileNode::getName, String.CASE_INSENSITIVE_ORDER));
            files.sort(Comparator.comparing(FileNode::getName, String.CASE_INSENSITIVE_ORDER));

            folders.forEach(node::addChild);
            files.forEach(node::addChild);
        }

        return node;
    }

    /**
     * Builds a complete tree structure starting from a directory.
     * Recursively includes all subdirectories and files.
     *
     * @param relativePath workspace-relative path (empty for full workspace tree)
     * @return FileNode tree structure
     * @throws IOException if reading fails
     */
    public FileNode getTree(String relativePath) throws IOException {
        Path dir = resolvePath(relativePath);
        if (!Files.exists(dir)) {
            throw new FileNotFoundException("Directory not found: " + relativePath);
        }
        return buildTree(dir, relativePath);
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

    // -------------------------------------------------------------------------
    // File Read/Write
    // -------------------------------------------------------------------------

    /**
     * Reads file content as a UTF-8 string.
     *
     * @param relativePath workspace-relative path to file
     * @return file contents
     * @throws IOException if file doesn't exist or can't be read
     */
    public String readFile(String relativePath) throws IOException {
        Path path = resolvePath(relativePath);
        if (!Files.exists(path)) {
            throw new FileNotFoundException("File not found: " + relativePath);
        }
        if (Files.isDirectory(path)) {
            throw new IOException("Cannot read directory as file: " + relativePath);
        }
        log("Reading file: " + relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    /**
     * Writes content to a file, creating parent directories if needed.
     * Overwrites existing file content.
     *
     * @param relativePath workspace-relative path to file
     * @param content file content to write
     * @throws IOException if write fails
     */
    public void writeFile(String relativePath, String content) throws IOException {
        Path path = resolvePath(relativePath);
        Files.createDirectories(path.getParent());
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
        log("Wrote file: " + relativePath);
    }

    // -------------------------------------------------------------------------
    // Create Operations
    // -------------------------------------------------------------------------

    /**
     * Creates a new file with optional initial content.
     *
     * @param relativePath workspace-relative path for new file
     * @param initialContent initial content (null or empty for blank file)
     * @throws IOException if file already exists or creation fails
     */
    public void createFile(String relativePath, String initialContent) throws IOException {
        Path path = resolvePath(relativePath);
        if (Files.exists(path)) {
            throw new IOException("File already exists: " + relativePath);
        }
        Files.createDirectories(path.getParent());
        String content = initialContent != null ? initialContent : "";
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
        log("Created file: " + relativePath);
    }

    /**
     * Creates a new folder, including any necessary parent directories.
     *
     * @param relativePath workspace-relative path for new folder
     * @throws IOException if folder already exists or creation fails
     */
    public void createFolder(String relativePath) throws IOException {
        Path path = resolvePath(relativePath);
        if (Files.exists(path)) {
            throw new IOException("Folder already exists: " + relativePath);
        }
        Files.createDirectories(path);
        log("Created folder: " + relativePath);
    }

    // -------------------------------------------------------------------------
    // Rename/Move Operations
    // -------------------------------------------------------------------------

    /**
     * Renames or moves a file or folder.
     *
     * @param oldPath current workspace-relative path
     * @param newPath target workspace-relative path
     * @throws IOException if source doesn't exist, target exists, or move fails
     */
    public void renameEntry(String oldPath, String newPath) throws IOException {
        Path from = resolvePath(oldPath);
        Path to = resolvePath(newPath);

        if (!Files.exists(from)) {
            throw new FileNotFoundException("Source not found: " + oldPath);
        }
        if (Files.exists(to)) {
            throw new IOException("Target already exists: " + newPath);
        }

        Files.createDirectories(to.getParent());
        Files.move(from, to);
        log("Renamed: " + oldPath + " -> " + newPath);
    }

    // -------------------------------------------------------------------------
    // Delete Operations
    // -------------------------------------------------------------------------

    /**
     * Deletes a file or folder (recursively for folders).
     *
     * @param relativePath workspace-relative path to delete
     * @throws IOException if path doesn't exist or deletion fails
     */
    public void deleteEntry(String relativePath) throws IOException {
        Path path = resolvePath(relativePath);
        if (!Files.exists(path)) {
            throw new FileNotFoundException("Path not found: " + relativePath);
        }

        if (Files.isDirectory(path)) {
            // Delete directory recursively
            try (Stream<Path> walk = Files.walk(path)) {
                walk.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to delete: " + p, e);
                        }
                    });
            }
            log("Deleted folder: " + relativePath);
        } else {
            Files.delete(path);
            log("Deleted file: " + relativePath);
        }
    }

    // -------------------------------------------------------------------------
    // Duplicate Operations
    // -------------------------------------------------------------------------

    /**
     * Duplicates a file or folder, creating a copy with " (copy)" suffix.
     *
     * @param relativePath workspace-relative path to duplicate
     * @return the relative path of the created duplicate
     * @throws IOException if source doesn't exist or copy fails
     */
    public String duplicateEntry(String relativePath) throws IOException {
        Path source = resolvePath(relativePath);
        if (!Files.exists(source)) {
            throw new FileNotFoundException("Path not found: " + relativePath);
        }

        // Generate unique name with " (copy)" suffix
        String targetPath = generateCopyName(relativePath);
        Path target = resolvePath(targetPath);

        if (Files.isDirectory(source)) {
            copyDirectory(source, target);
            log("Duplicated folder: " + relativePath + " -> " + targetPath);
        } else {
            Files.copy(source, target);
            log("Duplicated file: " + relativePath + " -> " + targetPath);
        }

        return targetPath;
    }

    private String generateCopyName(String relativePath) {
        Path path = Paths.get(relativePath);
        String fileName = path.getFileName().toString();
        String parent = path.getParent() != null ? path.getParent().toString().replace('\\', '/') : "";

        String baseName;
        String extension = "";

        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0 && !Files.isDirectory(resolvePath(relativePath))) {
            baseName = fileName.substring(0, dotIndex);
            extension = fileName.substring(dotIndex);
        } else {
            baseName = fileName;
        }

        // Try " (copy)", then " (copy 2)", etc.
        String newName = baseName + " (copy)" + extension;
        String newPath = parent.isEmpty() ? newName : parent + "/" + newName;

        int counter = 2;
        while (Files.exists(resolvePath(newPath))) {
            newName = baseName + " (copy " + counter + ")" + extension;
            newPath = parent.isEmpty() ? newName : parent + "/" + newName;
            counter++;
        }

        return newPath;
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path targetDir = target.resolve(source.relativize(dir));
                Files.createDirectories(targetDir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path targetFile = target.resolve(source.relativize(file));
                Files.copy(file, targetFile);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    // -------------------------------------------------------------------------
    // Search Operations
    // -------------------------------------------------------------------------

    /**
     * Searches for text within workspace files.
     *
     * @param query text to search for (case-insensitive)
     * @param globPattern optional glob pattern to filter files (e.g., "*.md", "scenes/*.txt")
     * @return list of search results with file, line number, and preview
     * @throws IOException if search fails
     */
    public List<SearchResult> search(String query, String globPattern) throws IOException {
        List<SearchResult> results = new ArrayList<>();

        if (query == null || query.trim().isEmpty()) {
            return results;
        }

        String lowerQuery = query.toLowerCase();
        PathMatcher matcher = null;

        if (globPattern != null && !globPattern.isEmpty()) {
            // Prepend ** if pattern doesn't start with it for recursive matching
            String pattern = globPattern.startsWith("**/") ? globPattern : "**/" + globPattern;
            matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
        }

        final PathMatcher finalMatcher = matcher;

        try (Stream<Path> walk = Files.walk(workspaceRoot)) {
            walk.filter(Files::isRegularFile)
                .filter(file -> {
                    if (finalMatcher == null) return true;
                    Path relativePath = workspaceRoot.relativize(file);
                    return finalMatcher.matches(relativePath);
                })
                .forEach(file -> {
                    try {
                        String relativePath = toRelativePath(file);
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

        log("Search for '" + query + "'" + (globPattern != null ? " (pattern: " + globPattern + ")" : "")
            + " found " + results.size() + " results");
        return results;
    }

    // -------------------------------------------------------------------------
    // Patch Operations (Stub for future AI use)
    // -------------------------------------------------------------------------

    /**
     * Applies a series of text edits to a file.
     * Stub implementation for future AI-assisted editing.
     *
     * @param relativePath workspace-relative path to file
     * @param edits list of text edits to apply
     * @throws IOException if file doesn't exist or edits fail
     * @throws UnsupportedOperationException currently not implemented
     */
    public void applyPatch(String relativePath, List<TextEdit> edits) throws IOException {
        Path path = resolvePath(relativePath);
        if (!Files.exists(path)) {
            throw new FileNotFoundException("File not found: " + relativePath);
        }
        if (Files.isDirectory(path)) {
            throw new IOException("Cannot apply patch to directory: " + relativePath);
        }

        // TODO: Implement patch application for AI-assisted editing
        // This will need to:
        // 1. Read file lines
        // 2. Validate edit ranges
        // 3. Apply edits in reverse order (to preserve line numbers)
        // 4. Write result back
        throw new UnsupportedOperationException("applyPatch not yet implemented");
    }

    // -------------------------------------------------------------------------
    // Utility Methods
    // -------------------------------------------------------------------------

    /**
     * Checks if a path exists in the workspace.
     */
    public boolean exists(String relativePath) {
        try {
            return Files.exists(resolvePath(relativePath));
        } catch (SecurityException e) {
            return false;
        }
    }

    /**
     * Checks if a path is a directory.
     */
    public boolean isDirectory(String relativePath) {
        try {
            return Files.isDirectory(resolvePath(relativePath));
        } catch (SecurityException e) {
            return false;
        }
    }

    /**
     * Checks if a path is a regular file.
     */
    public boolean isFile(String relativePath) {
        try {
            return Files.isRegularFile(resolvePath(relativePath));
        } catch (SecurityException e) {
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Logging
    // -------------------------------------------------------------------------

    private void log(String message) {
        AppLogger logger = AppLogger.get();
        if (logger != null) {
            logger.info("[WorkspaceService] " + message);
        }
    }
}
