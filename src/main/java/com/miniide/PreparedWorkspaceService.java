package com.miniide;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniide.models.CanonCard;
import com.miniide.models.FileNode;
import com.miniide.models.HookMatch;
import com.miniide.models.SceneSegment;
import com.miniide.models.SearchResult;
import com.miniide.models.StoryRegistry;
import com.miniide.models.StoryScene;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class PreparedWorkspaceService {
    private final Path workspaceRoot;
    private final ObjectMapper mapper;
    private final AppLogger logger;

    public PreparedWorkspaceService(Path workspaceRoot, ObjectMapper mapper) {
        this.workspaceRoot = workspaceRoot;
        this.mapper = mapper;
        this.logger = AppLogger.get();
    }

    public FileNode getTree() throws IOException {
        FileNode root = new FileNode("workspace", "", "folder");
        FileNode story = new FileNode("Story", "Story", "folder");
        FileNode compendium = new FileNode("Compendium", "Compendium", "folder");

        story.addChild(buildScenesFolder());
        compendium.addChild(buildCompendiumFolder("Characters"));
        compendium.addChild(buildCompendiumFolder("Locations"));
        compendium.addChild(buildCompendiumFolder("Lore"));
        compendium.addChild(buildCompendiumFolder("Factions"));
        compendium.addChild(buildCompendiumFolder("Technology"));
        compendium.addChild(buildCompendiumFolder("Culture"));
        compendium.addChild(buildCompendiumFolder("Events"));
        compendium.addChild(buildCompendiumFolder("Themes"));
        compendium.addChild(buildCompendiumFolder("Glossary"));
        compendium.addChild(buildCompendiumFolder("Misc"));

        root.addChild(story);
        root.addChild(compendium);
        return root;
    }

    public String readFile(String relativePath) throws IOException {
        CanonPath path = CanonPath.parse(relativePath);
        if (path == null) {
            throw new FileNotFoundException("File not found: " + relativePath);
        }
        if (path.kind == CanonPath.Kind.SCENE) {
            StoryScene scene = findSceneByDisplayId(path.displayId);
            if (scene == null) {
                throw new FileNotFoundException("Scene not found: " + relativePath);
            }
            return scene.getContent() != null ? scene.getContent() : "";
        }
        CanonCard card = findCardByDisplayIdOrSlug(path.displayId);
        if (card == null) {
            throw new FileNotFoundException("Card not found: " + relativePath);
        }
        return card.getContent() != null ? card.getContent() : "";
    }

    public void writeFile(String relativePath, String content) throws IOException {
        CanonPath path = CanonPath.parse(relativePath);
        if (path == null) {
            throw new IOException("Invalid path: " + relativePath);
        }
        String now = nowIso();
        if (path.kind == CanonPath.Kind.SCENE) {
            StoryRegistry registry = loadStoryRegistry();
            StoryScene scene = findSceneByDisplayId(registry, path.displayId);
            if (scene == null) {
                throw new FileNotFoundException("Scene not found: " + relativePath);
            }
            scene.setContent(content);
            scene.setUpdatedAt(now);
            saveStoryRegistry(registry);
            return;
        }
        CanonCard card = findCardByDisplayIdOrSlug(path.displayId);
        if (card == null) {
            throw new FileNotFoundException("Card not found: " + relativePath);
        }
        card.setContent(content);
        card.setUpdatedAt(now);
        saveCard(card);
    }

    public void createFile(String relativePath, String content) throws IOException {
        CanonPath path = CanonPath.parse(relativePath);
        if (path == null) {
            throw new IOException("Invalid path: " + relativePath);
        }
        String now = nowIso();
        if (path.kind == CanonPath.Kind.SCENE) {
            StoryRegistry registry = loadStoryRegistry();
            if (findSceneByDisplayId(registry, path.displayId) != null) {
                throw new IOException("Scene already exists: " + relativePath);
            }
            StoryScene scene = new StoryScene();
            scene.setOrigin("native");
            scene.setStableId(generateStableId());
            scene.setDisplayId(path.displayId);
            scene.setTitle(path.titleFallback);
            scene.setOrder(registry.getScenes().size() + 1);
            scene.setContent(content != null ? content : "");
            scene.setCreatedAt(now);
            scene.setUpdatedAt(now);
            scene.setStatus("active");
            registry.getScenes().add(scene);
            saveStoryRegistry(registry);
            return;
        }

        CanonCard card = new CanonCard();
        card.setOrigin("native");
        card.setStableId(generateStableId());
        card.setDisplayId(path.displayId);
        card.setType(path.cardType);
        card.setTitle(path.titleFallback);
        card.setContent(content != null ? content : "");
        card.setCreatedAt(now);
        card.setUpdatedAt(now);
        card.setAnnotationStatus("complete");
        card.setStatus("active");
        saveCard(card);
    }

    public void deleteEntry(String relativePath) throws IOException {
        CanonPath path = CanonPath.parse(relativePath);
        if (path == null) {
            throw new IOException("Invalid path: " + relativePath);
        }
        if (path.kind == CanonPath.Kind.SCENE) {
            StoryRegistry registry = loadStoryRegistry();
            boolean removed = registry.getScenes().removeIf(scene -> path.displayId.equalsIgnoreCase(scene.getDisplayId()));
            if (!removed) {
                throw new FileNotFoundException("Scene not found: " + relativePath);
            }
            saveStoryRegistry(registry);
            return;
        }
        CanonCard card = findCardByDisplayIdOrSlug(path.displayId);
        if (card == null) {
            throw new FileNotFoundException("Card not found: " + relativePath);
        }
        Path cardPath = resolveCardPath(card);
        Files.deleteIfExists(cardPath);
    }

    public void renameEntry(String from, String to) throws IOException {
        CanonPath source = CanonPath.parse(from);
        CanonPath target = CanonPath.parse(to);
        if (source == null || target == null || source.kind != target.kind) {
            throw new IOException("Invalid rename target");
        }
        String now = nowIso();
        if (source.kind == CanonPath.Kind.SCENE) {
            StoryRegistry registry = loadStoryRegistry();
            StoryScene scene = findSceneByDisplayId(registry, source.displayId);
            if (scene == null) {
                throw new FileNotFoundException("Scene not found: " + from);
            }
            scene.setDisplayId(target.displayId);
            scene.setTitle(target.titleFallback);
            scene.setUpdatedAt(now);
            saveStoryRegistry(registry);
            return;
        }

        CanonCard card = findCardByDisplayIdOrSlug(source.displayId);
        if (card == null) {
            throw new FileNotFoundException("Card not found: " + from);
        }
        Path oldPath = resolveCardPath(card);
        card.setDisplayId(target.displayId);
        card.setTitle(target.titleFallback);
        card.setType(target.cardType);
        card.setUpdatedAt(now);
        Path newPath = resolveCardPath(card);
        if (!Files.exists(oldPath)) {
            saveCard(card);
            return;
        }
        Files.deleteIfExists(oldPath);
        saveCard(card);
        if (!oldPath.equals(newPath)) {
            logger.info("Renamed card: " + oldPath + " -> " + newPath);
        }
    }

    public String duplicateEntry(String relativePath) throws IOException {
        CanonPath path = CanonPath.parse(relativePath);
        if (path == null) {
            throw new IOException("Invalid path: " + relativePath);
        }
        String now = nowIso();
        if (path.kind == CanonPath.Kind.SCENE) {
            StoryRegistry registry = loadStoryRegistry();
            StoryScene source = findSceneByDisplayId(registry, path.displayId);
            if (source == null) {
                throw new FileNotFoundException("Scene not found: " + relativePath);
            }
            StoryScene copy = new StoryScene();
            copy.setOrigin("native");
            copy.setStableId(generateStableId());
            copy.setDisplayId(nextCopyDisplayId(path.displayId, registry.getScenes()));
            copy.setTitle(source.getTitle() + " (copy)");
            copy.setOrder(registry.getScenes().size() + 1);
            copy.setContent(source.getContent());
            copy.setCreatedAt(now);
            copy.setUpdatedAt(now);
            copy.setStatus("active");
            registry.getScenes().add(copy);
            saveStoryRegistry(registry);
            return CanonPath.scenePath(copy.getDisplayId());
        }

        CanonCard card = findCardByDisplayIdOrSlug(path.displayId);
        if (card == null) {
            throw new FileNotFoundException("Card not found: " + relativePath);
        }
        CanonCard copy = new CanonCard();
        copy.setOrigin("native");
        copy.setStableId(generateStableId());
        copy.setDisplayId(nextCopyCardDisplayId(card));
        copy.setType(card.getType());
        copy.setTitle(card.getTitle() + " (copy)");
        copy.setContent(card.getContent());
        copy.setCreatedAt(now);
        copy.setUpdatedAt(now);
        copy.setAnnotationStatus(card.getAnnotationStatus());
        copy.setStatus(card.getStatus());
        saveCard(copy);
        return CanonPath.cardPathFromDisplayId(copy.getDisplayId(), copy.getType());
    }

    public List<SearchResult> search(String query) throws IOException {
        List<SearchResult> results = new ArrayList<>();
        if (query == null || query.isBlank()) {
            return results;
        }
        String lower = query.toLowerCase(Locale.ROOT);
        for (StoryScene scene : loadStoryRegistry().getScenes()) {
            String content = scene.getContent();
            if (content != null && content.toLowerCase(Locale.ROOT).contains(lower)) {
                results.add(new SearchResult(CanonPath.scenePath(scene.getDisplayId()), 1, preview(content)));
            }
        }
        for (CanonCard card : loadAllCards()) {
            String content = card.getContent();
            if (content != null && content.toLowerCase(Locale.ROOT).contains(lower)) {
                results.add(new SearchResult(CanonPath.cardPathFromDisplayId(card.getDisplayId(), card.getType()), 1, preview(content)));
            }
        }
        return results;
    }

    public List<VirtualFile> listVirtualFiles() throws IOException {
        List<VirtualFile> files = new ArrayList<>();
        for (StoryScene scene : loadStoryRegistry().getScenes()) {
            String path = CanonPath.scenePath(scene.getDisplayId());
            files.add(new VirtualFile(path, scene.getContent() != null ? scene.getContent() : ""));
        }
        for (CanonCard card : loadAllCards()) {
            String path = CanonPath.cardPathFromDisplayId(card.getDisplayId(), card.getType());
            files.add(new VirtualFile(path, card.getContent() != null ? card.getContent() : ""));
        }
        return files;
    }

    public List<SceneSegment> getSceneSegments(String relativePath) throws IOException {
        String content = readFile(relativePath);
        List<SceneSegment> segments = new ArrayList<>();
        segments.add(new SceneSegment("seg-1", 0, content.length(), content));
        return segments;
    }

    public StoryScene reindexScene(String sceneDisplayId, String mode) throws IOException {
        String trimmedId = sceneDisplayId != null ? sceneDisplayId.trim() : "";
        if (trimmedId.isEmpty()) {
            throw new IllegalArgumentException("Scene displayId is required.");
        }
        String effectiveMode = mode != null ? mode.trim().toLowerCase(Locale.ROOT) : "index";
        if (!effectiveMode.equals("index") && !effectiveMode.equals("full") && !effectiveMode.equals("markers")) {
            throw new IllegalArgumentException("Unsupported mode: " + mode);
        }

        StoryRegistry registry = loadStoryRegistry();
        StoryScene scene = findSceneByDisplayId(registry, trimmedId);
        if (scene == null) {
            throw new FileNotFoundException("Scene not found: " + trimmedId);
        }

        String content = scene.getContent() != null ? scene.getContent() : "";
        Map<String, List<String>> hooksIndex = loadHooksIndex();
        if (hooksIndex.isEmpty() && !effectiveMode.equals("markers")) {
            scene.setLastIndexedHash(sha256Hex(content));
            scene.setIndexStatus("missing");
            scene.setLinkedCardStableIds(new ArrayList<>());
            scene.setLinkedHookIds(new ArrayList<>());
            scene.setHookMatches(new ArrayList<>());
            saveStoryRegistry(registry);
            return scene;
        }

        List<HookMatch> matches = effectiveMode.equals("markers")
            ? new ArrayList<>()
            : matchHooksByIndex(content, hooksIndex);
        applyIndexResults(scene, content, matches);
        saveStoryRegistry(registry);
        return scene;
    }

    private FileNode buildScenesFolder() throws IOException {
        FileNode scenesFolder = new FileNode("Scenes", "Story/Scenes", "folder");
        StoryRegistry registry = loadStoryRegistry();
        List<StoryScene> scenes = registry.getScenes() != null ? new ArrayList<>(registry.getScenes()) : new ArrayList<>();
        scenes.sort(Comparator.comparingInt(StoryScene::getOrder));
        for (StoryScene scene : scenes) {
            if (scene.getDisplayId() == null || scene.getDisplayId().isBlank()) {
                continue;
            }
            String filename = CanonPath.sceneFileName(scene.getDisplayId());
            scenesFolder.addChild(new FileNode(filename, "Story/Scenes/" + filename, "file"));
        }
        return scenesFolder;
    }

    private FileNode buildCompendiumFolder(String bucket) throws IOException {
        String path = "Compendium/" + bucket;
        FileNode folder = new FileNode(bucket, path, "folder");
        List<CanonCard> cards = loadAllCards();
        cards.stream()
            .filter(card -> bucketMatches(bucket, card))
            .sorted(Comparator.comparing(CanonCard::getTitle, String.CASE_INSENSITIVE_ORDER))
            .forEach(card -> {
                String filename = CanonPath.cardFileName(card.getDisplayId());
                folder.addChild(new FileNode(filename, path + "/" + filename, "file"));
            });
        return folder;
    }

    private StoryRegistry loadStoryRegistry() throws IOException {
        Path scenesPath = workspaceRoot.resolve(".control-room").resolve("story").resolve("scenes.json");
        if (!Files.exists(scenesPath)) {
            StoryRegistry registry = new StoryRegistry();
            registry.setSchemaVersion(1);
            registry.setScenes(new ArrayList<>());
            return registry;
        }
        try {
            StoryRegistry registry = mapper.readValue(scenesPath.toFile(), StoryRegistry.class);
            if (registry.getScenes() == null) {
                registry.setScenes(new ArrayList<>());
            }
            return registry;
        } catch (Exception e) {
            logger.error("Failed to load story registry, falling back to empty: " + e.getMessage());
            StoryRegistry registry = new StoryRegistry();
            registry.setSchemaVersion(1);
            registry.setScenes(new ArrayList<>());
            return registry;
        }
    }

    private void saveStoryRegistry(StoryRegistry registry) throws IOException {
        Path scenesPath = workspaceRoot.resolve(".control-room").resolve("story").resolve("scenes.json");
        Files.createDirectories(scenesPath.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(scenesPath.toFile(), registry);
    }

    private List<CanonCard> loadAllCards() throws IOException {
        List<CanonCard> cards = new ArrayList<>();
        Path cardsDir = workspaceRoot.resolve(".control-room").resolve("canon").resolve("cards");
        if (!Files.exists(cardsDir)) {
            return cards;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(cardsDir, "*.json")) {
            for (Path entry : stream) {
                try {
                    cards.add(mapper.readValue(entry.toFile(), CanonCard.class));
                } catch (Exception e) {
                    logger.error("Failed to load canon card " + entry.getFileName() + ": " + e.getMessage());
                }
            }
        }
        return cards;
    }

    private CanonCard findCardByDisplayId(String displayId) throws IOException {
        for (CanonCard card : loadAllCards()) {
            if (displayId.equalsIgnoreCase(card.getDisplayId())) {
                return card;
            }
        }
        return null;
    }

    private CanonCard findCardByDisplayIdOrSlug(String displayId) throws IOException {
        CanonCard exact = findCardByDisplayId(displayId);
        if (exact != null) {
            return exact;
        }
        String slug = extractSlug(displayId);
        if (slug == null) {
            return null;
        }
        CanonCard match = null;
        for (CanonCard card : loadAllCards()) {
            String cardSlug = extractSlug(card.getDisplayId());
            if (cardSlug != null && cardSlug.equalsIgnoreCase(slug)) {
                if (match != null) {
                    return null;
                }
                match = card;
            }
        }
        return match;
    }

    private StoryScene findSceneByDisplayId(String displayId) throws IOException {
        return findSceneByDisplayId(loadStoryRegistry(), displayId);
    }

    private StoryScene findSceneByDisplayId(StoryRegistry registry, String displayId) {
        for (StoryScene scene : registry.getScenes()) {
            if (displayId.equalsIgnoreCase(scene.getDisplayId())) {
                return scene;
            }
        }
        return null;
    }

    private void saveCard(CanonCard card) throws IOException {
        Path cardPath = resolveCardPath(card);
        Path legacyPath = resolveLegacyCardPath(card);
        if (!cardPath.equals(legacyPath) && Files.exists(legacyPath)) {
            Files.delete(legacyPath);
        }
        Files.createDirectories(cardPath.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(cardPath.toFile(), card);
    }

    private Path resolveCardPath(CanonCard card) {
        String typePrefix = CanonPath.cardPrefix(card.getType());
        String slug = CanonPath.cardSlug(card.getDisplayId(), card.getTitle());
        if (card.getDisplayId() != null && card.getDisplayId().contains(":")) {
            int colonIndex = card.getDisplayId().indexOf(':');
            typePrefix = card.getDisplayId().substring(0, colonIndex).toUpperCase(Locale.ROOT);
            String displaySlug = card.getDisplayId().substring(colonIndex + 1);
            if (!displaySlug.isBlank()) {
                slug = displaySlug.toLowerCase(Locale.ROOT);
            }
        }
        String filename = typePrefix + "-" + slug + ".json";
        return workspaceRoot.resolve(".control-room").resolve("canon").resolve("cards").resolve(filename);
    }

    private Path resolveLegacyCardPath(CanonCard card) {
        String typePrefix = CanonPath.cardPrefix(card.getType());
        String slug = slugify(card.getTitle());
        String filename = typePrefix + "-" + slug + ".json";
        return workspaceRoot.resolve(".control-room").resolve("canon").resolve("cards").resolve(filename);
    }

    private String nextCopyDisplayId(String baseDisplayId, List<StoryScene> scenes) {
        String base = baseDisplayId;
        String candidate = base + "-copy";
        int counter = 2;
        while (displayIdExists(candidate, scenes)) {
            candidate = base + "-copy-" + counter++;
        }
        return candidate;
    }

    private boolean displayIdExists(String displayId, List<StoryScene> scenes) {
        for (StoryScene scene : scenes) {
            if (displayId.equalsIgnoreCase(scene.getDisplayId())) {
                return true;
            }
        }
        return false;
    }

    private String nextCopyCardDisplayId(CanonCard card) throws IOException {
        String base = card.getDisplayId();
        String candidate = base + "-copy";
        int counter = 2;
        while (findCardByDisplayId(candidate) != null) {
            candidate = base + "-copy-" + counter++;
        }
        return candidate;
    }

    private String bucketToType(String bucket) {
        switch (bucket.toLowerCase(Locale.ROOT)) {
            case "characters": return "character";
            case "locations": return "location";
            case "lore": return "concept";
            case "factions": return "faction";
            case "technology": return "technology";
            case "culture": return "culture";
            case "events": return "event";
            case "themes": return "theme";
            case "glossary": return "glossary";
            default: return "misc";
        }
    }

    private boolean bucketMatches(String bucket, CanonCard card) {
        String displayBucket = CanonPath.bucketFromDisplayId(card.getDisplayId());
        if (displayBucket != null) {
            return bucket.equalsIgnoreCase(displayBucket);
        }
        String typeBucket = CanonPath.bucketFromType(card.getType());
        return bucket.equalsIgnoreCase(typeBucket);
    }

    private String preview(String content) {
        String trimmed = content.trim();
        if (trimmed.length() > 100) {
            return trimmed.substring(0, 100) + "...";
        }
        return trimmed;
    }

    private String extractSlug(String displayId) {
        if (displayId == null || displayId.isBlank()) {
            return null;
        }
        int colonIndex = displayId.indexOf(':');
        if (colonIndex >= 0 && colonIndex < displayId.length() - 1) {
            String slug = displayId.substring(colonIndex + 1);
            return CanonPath.stripKnownCardPrefix(slug);
        }
        int dashIndex = displayId.indexOf('-');
        if (dashIndex >= 0 && dashIndex < displayId.length() - 1) {
            String slug = displayId.substring(dashIndex + 1);
            return CanonPath.stripKnownCardPrefix(slug);
        }
        return CanonPath.stripKnownCardPrefix(displayId);
    }

    private String slugify(String value) {
        if (value == null || value.isBlank()) {
            return "item";
        }
        String slug = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        slug = slug.replaceAll("(^-|-$)", "");
        return slug.isEmpty() ? "item" : slug;
    }

    private String nowIso() {
        return DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC).format(Instant.now());
    }

    private String generateStableId() {
        return "ID-" + java.util.UUID.randomUUID().toString();
    }

    private Map<String, List<String>> loadHooksIndex() throws IOException {
        Path hooksPath = workspaceRoot.resolve(".control-room").resolve("canon").resolve("hooks-index.json");
        if (!Files.exists(hooksPath)) {
            return new LinkedHashMap<>();
        }
        return mapper.readValue(hooksPath.toFile(),
            new TypeReference<Map<String, List<String>>>() {});
    }

    private List<HookMatch> matchHooksByIndex(String content, Map<String, List<String>> hooksIndex) {
        List<HookMatch> matches = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return matches;
        }
        String lower = content.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, List<String>> entry : hooksIndex.entrySet()) {
            String hook = entry.getKey();
            if (hook == null || hook.isBlank()) {
                continue;
            }
            String hookLower = hook.toLowerCase(Locale.ROOT);
            int idx = lower.indexOf(hookLower);
            if (idx < 0) {
                continue;
            }
            List<String> stableIds = entry.getValue() != null ? entry.getValue() : List.of();
            for (String stableId : stableIds) {
                HookMatch match = new HookMatch();
                match.setHook(hook);
                match.setCardStableId(stableId);
                match.setMatchType("index");
                match.setConfidence(0.6);
                match.setStart(idx);
                match.setEnd(idx + hook.length());
                matches.add(match);
            }
        }
        return matches;
    }

    private void applyIndexResults(StoryScene scene, String content, List<HookMatch> matches) {
        String hash = sha256Hex(content != null ? content : "");
        scene.setLastIndexedHash(hash);
        scene.setIndexStatus("ok");

        Set<String> cardIds = new LinkedHashSet<>();
        Set<String> hooks = new LinkedHashSet<>();
        for (HookMatch match : matches) {
            if (match.getCardStableId() != null && !match.getCardStableId().isBlank()) {
                cardIds.add(match.getCardStableId());
            }
            if (match.getHook() != null && !match.getHook().isBlank()) {
                hooks.add(match.getHook());
            }
        }
        scene.setLinkedCardStableIds(new ArrayList<>(cardIds));
        scene.setLinkedHookIds(new ArrayList<>(hooks));
        scene.setHookMatches(matches);
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashed) {
                sb.append(String.format("%02x", b));
            }
            return "sha256:" + sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash content", e);
        }
    }

    private static class CanonPath {
        enum Kind { SCENE, CARD }
        final Kind kind;
        final String displayId;
        final String titleFallback;
        final String cardType;

        private CanonPath(Kind kind, String displayId, String titleFallback, String cardType) {
            this.kind = kind;
            this.displayId = displayId;
            this.titleFallback = titleFallback;
            this.cardType = cardType;
        }

        static CanonPath parse(String path) {
            if (path == null) return null;
            String normalized = path.replace('\\', '/');
            if ("Story/SCN-outline.md".equalsIgnoreCase(normalized)) {
                return new CanonPath(Kind.SCENE, "SCN:outline", "Outline", null);
            }
            if (normalized.startsWith("Story/Scenes/")) {
                String filename = normalized.substring("Story/Scenes/".length());
                if (!filename.endsWith(".md")) return null;
                String base = filename.substring(0, filename.length() - 3);
                String slug = base.startsWith("SCN-") ? base.substring(4) : base;
                String displayId = "SCN:" + slug;
                return new CanonPath(Kind.SCENE, displayId, titleFromBase(base), null);
            }
            if (normalized.startsWith("Compendium/")) {
                String[] parts = normalized.split("/");
                if (parts.length < 3) return null;
                String bucket = parts[1];
                String filename = parts[2];
                if (!filename.endsWith(".md")) return null;
                String base = filename.substring(0, filename.length() - 3);
                String type = bucketToTypeStatic(bucket);
                String prefix = cardPrefix(type);
                String slug = stripKnownCardPrefix(base);
                if (slug.toUpperCase(Locale.ROOT).startsWith(prefix + "-")) {
                    slug = slug.substring(prefix.length() + 1);
                }
                String displayId = prefix + ":" + slug;
                return new CanonPath(Kind.CARD, displayId, titleFromBase(base), type);
            }
            return null;
        }

        static String sceneFileName(String displayId) {
            return displayId.replace(':', '-') + ".md";
        }

        static String cardFileName(String displayId) {
            return displayId.replace(':', '-') + ".md";
        }

        static String scenePath(String displayId) {
            return "Story/Scenes/" + sceneFileName(displayId);
        }

        static String cardPath(String type, String displayId) {
            String bucket = bucketFromType(type);
            return "Compendium/" + bucket + "/" + cardFileName(displayId);
        }

        static String cardPathFromDisplayId(String displayId, String type) {
            String bucket = bucketFromDisplayId(displayId);
            if (bucket == null) {
                bucket = bucketFromType(type);
            }
            return "Compendium/" + bucket + "/" + cardFileName(displayId);
        }

        static String cardPrefix(String type) {
            switch (type.toLowerCase(Locale.ROOT)) {
                case "character": return "CHAR";
                case "location": return "LOC";
                case "concept": return "CONCEPT";
                case "faction": return "FACTION";
                case "technology": return "TECH";
                case "culture": return "CULTURE";
                case "event": return "EVENT";
                case "theme": return "THEME";
                case "glossary": return "GLOSSARY";
                default: return "MISC";
            }
        }

        static String cardSlug(String displayId, String title) {
            if (displayId != null && displayId.contains(":")) {
                return displayId.substring(displayId.indexOf(':') + 1).toLowerCase(Locale.ROOT);
            }
            return slugify(title);
        }

        static String bucketFromType(String type) {
            switch (type.toLowerCase(Locale.ROOT)) {
                case "character": return "Characters";
                case "location": return "Locations";
                case "concept": return "Lore";
                case "faction": return "Factions";
                case "technology": return "Technology";
                case "culture": return "Culture";
                case "event": return "Events";
                case "theme": return "Themes";
                case "glossary": return "Glossary";
                default: return "Misc";
            }
        }

        static String bucketFromDisplayId(String displayId) {
            if (displayId == null || displayId.isBlank()) {
                return null;
            }
            String prefix = displayId;
            int colonIndex = displayId.indexOf(':');
            if (colonIndex > 0) {
                prefix = displayId.substring(0, colonIndex);
            } else if (displayId.contains("-")) {
                prefix = displayId.substring(0, displayId.indexOf('-'));
            }
            switch (prefix.toUpperCase(Locale.ROOT)) {
                case "CHAR": return "Characters";
                case "LOC": return "Locations";
                case "CONCEPT": return "Lore";
                case "FACTION": return "Factions";
                case "TECH": return "Technology";
                case "CULTURE": return "Culture";
                case "EVENT": return "Events";
                case "THEME": return "Themes";
                case "GLOSSARY": return "Glossary";
                case "MISC": return "Misc";
                default: return null;
            }
        }

        private static String bucketToTypeStatic(String bucket) {
            switch (bucket.toLowerCase(Locale.ROOT)) {
                case "characters": return "character";
                case "locations": return "location";
                case "lore": return "concept";
                case "factions": return "faction";
                case "technology": return "technology";
                case "culture": return "culture";
                case "events": return "event";
                case "themes": return "theme";
                case "glossary": return "glossary";
                default: return "misc";
            }
        }

        private static String stripKnownCardPrefix(String base) {
            if (base == null || base.isBlank()) {
                return base;
            }
            String[] prefixes = {
                "CHAR", "LOC", "CONCEPT", "FACTION", "TECH",
                "CULTURE", "EVENT", "THEME", "GLOSSARY", "MISC"
            };
            String value = base;
            boolean trimmed = true;
            while (trimmed) {
                trimmed = false;
                String upper = value.toUpperCase(Locale.ROOT);
                for (String prefix : prefixes) {
                    String marker = prefix + "-";
                    if (upper.startsWith(marker)) {
                        value = value.substring(prefix.length() + 1);
                        trimmed = true;
                        break;
                    }
                }
            }
            return value;
        }

        private static String titleFromBase(String base) {
            String cleaned = base.replace('_', ' ').replace('-', ' ').replace(':', ' ').trim();
            return cleaned.isEmpty() ? "Untitled" : cleaned;
        }

        private static String slugify(String value) {
            if (value == null || value.isBlank()) {
                return "item";
            }
            String slug = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
            slug = slug.replaceAll("(^-|-$)", "");
            return slug.isEmpty() ? "item" : slug;
        }
    }

    public static class VirtualFile {
        private final String path;
        private final String content;

        public VirtualFile(String path, String content) {
            this.path = path;
            this.content = content;
        }

        public String getPath() {
            return path;
        }

        public String getContent() {
            return content;
        }
    }
}
