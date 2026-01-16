package com.miniide;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniide.models.CanonCard;
import com.miniide.models.CanonManifest;
import com.miniide.models.IngestEvidence;
import com.miniide.models.IngestIgnoredInput;
import com.miniide.models.IngestManifest;
import com.miniide.models.IngestPointer;
import com.miniide.models.IngestSourceContext;
import com.miniide.models.IngestStats;
import com.miniide.models.StoryManifest;
import com.miniide.models.StoryRegistry;
import com.miniide.models.StoryScene;
import com.miniide.models.WorkspaceMetadata;
import io.javalin.http.UploadedFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class ProjectPreparationService {
    private static final int SCHEMA_VERSION = 1;
    private final Path workspaceRoot;
    private final WorkspaceService workspaceService;
    private final ObjectMapper mapper;
    private final AppLogger logger;

    public ProjectPreparationService(Path workspaceRoot, WorkspaceService workspaceService, ObjectMapper mapper) {
        this.workspaceRoot = workspaceRoot;
        this.workspaceService = workspaceService;
        this.mapper = mapper;
        this.logger = AppLogger.get();
    }

    public boolean isPrepared() {
        WorkspaceMetadata meta = workspaceService.loadMetadata();
        return meta != null && meta.isPrepared();
    }

    public WorkspaceMetadata getMetadata() {
        return workspaceService.loadMetadata();
    }

    public PreparationResult prepareEmpty(PrepareEmptyPayload payload) throws IOException {
        if (isPrepared()) {
            return PreparationResult.alreadyPrepared();
        }
        if (payload == null || payload.protagonistName == null || payload.protagonistName.isBlank()) {
            throw new IllegalArgumentException("Protagonist name is required.");
        }
        String now = nowIso();
        String ingestId = generateIngestId();
        String projectSalt = generateSalt();

        ensurePrepDirectories();

        IngestManifest ingestManifest = new IngestManifest();
        ingestManifest.setSchemaVersion(SCHEMA_VERSION);
        ingestManifest.setIngestId(ingestId);
        ingestManifest.setIngestedAt(now);
        ingestManifest.setProjectSalt(projectSalt);
        ingestManifest.setStatus("complete");
        ingestManifest.setMode("empty");
        ingestManifest.setStats(new IngestStats());
        ingestManifest.getStats().setFilesProcessed(0);
        ingestManifest.getStats().setExcerptsStored(0);

        StoryRegistry storyRegistry = new StoryRegistry();
        storyRegistry.setSchemaVersion(SCHEMA_VERSION);

        CanonManifest canonManifest = new CanonManifest();
        canonManifest.setSchemaVersion(SCHEMA_VERSION);
        canonManifest.setPreparedAt(now);
        canonManifest.setStatus("prepared");

        StoryManifest storyManifest = new StoryManifest();
        storyManifest.setSchemaVersion(SCHEMA_VERSION);
        storyManifest.setPreparedAt(now);
        storyManifest.setStatus("prepared");

        List<CanonCard> cards = new ArrayList<>();
        List<StoryScene> scenes = new ArrayList<>();

        CanonCard card = new CanonCard();
        card.setOrigin("native");
        card.setType("character");
        card.setTitle(payload.protagonistName.trim());
        card.setStableId(generateStableId());
        card.setDisplayId("CHAR:" + slugify(card.getTitle()));
        card.setContent(buildProtagonistContent(payload));
        card.setCreatedAt(now);
        card.setUpdatedAt(now);
        card.setAnnotationStatus("complete");
        card.setStatus("active");
        cards.add(card);

        StoryScene scene = new StoryScene();
        scene.setOrigin("native");
        scene.setStableId(generateStableId());
        scene.setDisplayId("SCN:opening");
        scene.setTitle("Opening Scene");
        scene.setOrder(1);
        scene.setContent(buildSceneSeed(payload));
        scene.setCreatedAt(now);
        scene.setUpdatedAt(now);
        scene.setStatus("active");
        scenes.add(scene);

        storyRegistry.setScenes(scenes);
        canonManifest.setCardCount(cards.size());
        storyManifest.setSceneCount(scenes.size());
        ingestManifest.getStats().setScenesCreated(scenes.size());
        ingestManifest.getStats().setCardsCreated(cards.size());

        saveManifests(ingestManifest, canonManifest, storyManifest, storyRegistry, cards);
        writeEmptyIndices();
        markPrepared("empty", now);

        return PreparationResult.success("empty", cards.size(), scenes.size());
    }

    public PreparationResult prepareIngest(PrepareIngestPayload payload) throws IOException {
        if (isPrepared()) {
            return PreparationResult.alreadyPrepared();
        }
        if (payload == null || payload.totalFiles() == 0) {
            throw new IllegalArgumentException("No files provided for ingest.");
        }

        String now = nowIso();
        String ingestId = generateIngestId();
        String projectSalt = generateSalt();
        ensurePrepDirectories();

        List<CanonCard> cards = new ArrayList<>();
        List<StoryScene> scenes = new ArrayList<>();
        int excerptsStored = 0;

        if (payload != null) {
            if (payload.manuscripts != null) {
                for (UploadedFile file : payload.manuscripts) {
                    if (file == null) continue;
                    IngestEvidence evidence = storeEvidence(file, now);
                    excerptsStored += 1;
                    StoryScene scene = new StoryScene();
                    scene.setOrigin("ingest");
                    scene.setStableId(generateStableId());
                    scene.setDisplayId("SCN:" + slugify(stripExtension(file.filename())));
                    scene.setTitle(prettifyName(file.filename()));
                    scene.setOrder(scenes.size() + 1);
                    scene.setContent(evidence.getContent());
                    scene.setCreatedAt(now);
                    scene.setUpdatedAt(now);
                    scene.setStatus("active");
                    scene.setIngestPointers(List.of(pointerForEvidence(evidence)));
                    scenes.add(scene);
                }
            }
            if (payload.canonFiles != null) {
                for (UploadedFile file : payload.canonFiles) {
                    if (file == null) continue;
                    IngestEvidence evidence = storeEvidence(file, now);
                    excerptsStored += 1;
                    CanonCard card = new CanonCard();
                    card.setOrigin("ingest");
                    card.setStableId(generateStableId());
                    card.setType(inferCardType(file.filename(), evidence.getContent()));
                    card.setTitle(prettifyName(file.filename()));
                    card.setDisplayId(typePrefix(card.getType()) + ":" + slugify(card.getTitle()));
                    card.setContent(evidence.getContent());
                    card.setCreatedAt(now);
                    card.setUpdatedAt(now);
                    card.setAnnotationStatus("draft");
                    card.setStatus("active");
                    card.setIngestPointers(List.of(pointerForEvidence(evidence)));
                    cards.add(card);
                }
            }
        }

        StoryRegistry storyRegistry = new StoryRegistry();
        storyRegistry.setSchemaVersion(SCHEMA_VERSION);
        storyRegistry.setScenes(scenes);

        CanonManifest canonManifest = new CanonManifest();
        canonManifest.setSchemaVersion(SCHEMA_VERSION);
        canonManifest.setPreparedAt(now);
        canonManifest.setCardCount(cards.size());
        canonManifest.setStatus("prepared");

        StoryManifest storyManifest = new StoryManifest();
        storyManifest.setSchemaVersion(SCHEMA_VERSION);
        storyManifest.setPreparedAt(now);
        storyManifest.setSceneCount(scenes.size());
        storyManifest.setStatus("prepared");

        IngestManifest ingestManifest = new IngestManifest();
        ingestManifest.setSchemaVersion(SCHEMA_VERSION);
        ingestManifest.setIngestId(ingestId);
        ingestManifest.setIngestedAt(now);
        ingestManifest.setProjectSalt(projectSalt);
        ingestManifest.setStatus("complete");
        ingestManifest.setMode("ingest");
        IngestStats stats = new IngestStats();
        stats.setFilesProcessed((payload != null ? payload.totalFiles() : 0));
        stats.setExcerptsStored(excerptsStored);
        stats.setScenesCreated(scenes.size());
        stats.setCardsCreated(cards.size());
        ingestManifest.setStats(stats);
        ingestManifest.setIgnoredInputs(payload != null ? payload.ignoredInputs : new ArrayList<>());

        saveManifests(ingestManifest, canonManifest, storyManifest, storyRegistry, cards);
        writeEmptyIndices();
        markPrepared("ingest", now);

        return PreparationResult.success("ingest", cards.size(), scenes.size());
    }

    private void ensurePrepDirectories() throws IOException {
        Files.createDirectories(workspaceRoot.resolve(".control-room").resolve("ingest").resolve("excerpts"));
        Files.createDirectories(workspaceRoot.resolve(".control-room").resolve("canon").resolve("cards"));
        Files.createDirectories(workspaceRoot.resolve(".control-room").resolve("story"));
    }

    private void saveManifests(IngestManifest ingest, CanonManifest canon, StoryManifest story,
                               StoryRegistry registry, List<CanonCard> cards) throws IOException {
        Path ingestPath = workspaceRoot.resolve(".control-room").resolve("ingest").resolve("manifest.json");
        Path canonManifestPath = workspaceRoot.resolve(".control-room").resolve("canon").resolve("manifest.json");
        Path entitiesPath = workspaceRoot.resolve(".control-room").resolve("canon").resolve("entities.json");
        Path hooksPath = workspaceRoot.resolve(".control-room").resolve("canon").resolve("hooks-index.json");
        Path storyManifestPath = workspaceRoot.resolve(".control-room").resolve("story").resolve("manifest.json");
        Path scenesPath = workspaceRoot.resolve(".control-room").resolve("story").resolve("scenes.json");
        Path chaptersPath = workspaceRoot.resolve(".control-room").resolve("story").resolve("chapters.json");

        mapper.writerWithDefaultPrettyPrinter().writeValue(ingestPath.toFile(), ingest);
        mapper.writerWithDefaultPrettyPrinter().writeValue(canonManifestPath.toFile(), canon);
        mapper.writerWithDefaultPrettyPrinter().writeValue(storyManifestPath.toFile(), story);
        mapper.writerWithDefaultPrettyPrinter().writeValue(scenesPath.toFile(), registry);
        mapper.writerWithDefaultPrettyPrinter().writeValue(chaptersPath.toFile(), new ArrayList<>());
        mapper.writerWithDefaultPrettyPrinter().writeValue(entitiesPath.toFile(), new java.util.HashMap<>());
        mapper.writerWithDefaultPrettyPrinter().writeValue(hooksPath.toFile(), new java.util.HashMap<>());

        for (CanonCard card : cards) {
            Path cardPath = workspaceRoot.resolve(".control-room").resolve("canon").resolve("cards")
                .resolve(cardFileName(card));
            mapper.writerWithDefaultPrettyPrinter().writeValue(cardPath.toFile(), card);
        }
    }

    private void writeEmptyIndices() throws IOException {
        Path entitiesPath = workspaceRoot.resolve(".control-room").resolve("canon").resolve("entities.json");
        Path hooksPath = workspaceRoot.resolve(".control-room").resolve("canon").resolve("hooks-index.json");
        if (!Files.exists(entitiesPath)) {
            mapper.writerWithDefaultPrettyPrinter().writeValue(entitiesPath.toFile(), new java.util.HashMap<>());
        }
        if (!Files.exists(hooksPath)) {
            mapper.writerWithDefaultPrettyPrinter().writeValue(hooksPath.toFile(), new java.util.HashMap<>());
        }
    }

    private void markPrepared(String mode, String now) throws IOException {
        WorkspaceMetadata meta = workspaceService.loadMetadata();
        meta.setPrepared(true);
        meta.setPreparedMode(mode);
        meta.setPreparedAt(now);
        workspaceService.saveMetadata(meta);
    }

    private String buildProtagonistContent(PrepareEmptyPayload payload) {
        StringBuilder builder = new StringBuilder();
        builder.append("# ").append(payload.protagonistName.trim()).append("\n\n");
        if (payload.protagonistRole != null && !payload.protagonistRole.isBlank()) {
            builder.append("Role: ").append(payload.protagonistRole.trim()).append("\n\n");
        }
        if (payload.premise != null && !payload.premise.isBlank()) {
            builder.append("Premise: ").append(payload.premise.trim()).append("\n");
        }
        return builder.toString().trim();
    }

    private String buildSceneSeed(PrepareEmptyPayload payload) {
        StringBuilder builder = new StringBuilder();
        builder.append("# Opening Scene\n\n");
        if (payload.premise != null && !payload.premise.isBlank()) {
            builder.append(payload.premise.trim()).append("\n\n");
        }
        if (payload.genre != null && !payload.genre.isBlank()) {
            builder.append("Genre/Tone: ").append(payload.genre.trim()).append("\n\n");
        }
        if (payload.themes != null && !payload.themes.isBlank()) {
            builder.append("Themes: ").append(payload.themes.trim()).append("\n\n");
        }
        builder.append("_Draft your opening scene here._\n");
        return builder.toString();
    }

    private IngestEvidence storeEvidence(UploadedFile file, String now) throws IOException {
        String filename = file.filename();
        String content = readUploadedFile(file);
        String hash = "sha256:" + sha256Hex(content.getBytes(StandardCharsets.UTF_8));

        IngestSourceContext context = new IngestSourceContext();
        context.setFilename(filename);
        context.setHeading("");
        context.setByteRange(new int[] { 0, content.getBytes(StandardCharsets.UTF_8).length });

        IngestEvidence evidence = new IngestEvidence();
        evidence.setExcerptHash(hash);
        evidence.setContent(content);
        evidence.setOriginalContext(context);
        evidence.setIngestedAt(now);

        Path evidencePath = workspaceRoot.resolve(".control-room").resolve("ingest")
            .resolve("excerpts").resolve(hash.replace("sha256:", "") + ".json");
        mapper.writerWithDefaultPrettyPrinter().writeValue(evidencePath.toFile(), evidence);

        return evidence;
    }

    private IngestPointer pointerForEvidence(IngestEvidence evidence) {
        IngestPointer pointer = new IngestPointer();
        pointer.setExcerptHash(evidence.getExcerptHash());
        pointer.setOriginalContext(evidence.getOriginalContext());
        return pointer;
    }

    private String readUploadedFile(UploadedFile file) throws IOException {
        try (InputStream input = file.content()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String inferCardType(String filename, String content) {
        String filenameLower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        String snippet = buildSnippet(content, 4000, 50);
        String snippetLower = snippet.toLowerCase(Locale.ROOT);

        int characterScore = 0;
        if (filenameLower.startsWith("pov-") || filenameLower.startsWith("support-")) characterScore += 3;
        if (filenameLower.contains("character") || filenameLower.contains("char")) characterScore += 2;
        if (snippetLower.contains(" pov ") || snippetLower.contains("pov character")) characterScore += 3;
        if (snippetLower.contains("role summary")) characterScore += 2;
        if (snippetLower.contains("appearance")) characterScore += 1;
        if (snippetLower.contains("personality")) characterScore += 1;
        if (snippetLower.contains("backstory")) characterScore += 1;
        if (snippetLower.contains("motivation")) characterScore += 1;
        if (snippetLower.contains("goal")) characterScore += 1;
        if (snippetLower.contains("arc")) characterScore += 1;
        if (snippetLower.contains("relationships")) characterScore += 1;
        if (snippetLower.contains("aliases")) characterScore += 1;
        if (snippetLower.contains("occupation")) characterScore += 1;
        if (snippetLower.contains("pronouns")) characterScore += 1;
        if (snippetLower.contains("age:")) characterScore += 1;

        if (characterScore >= 3) {
            return "character";
        }

        if (filenameLower.contains("loc") || filenameLower.contains("place")) return "location";
        if (filenameLower.contains("faction")) return "faction";
        if (filenameLower.contains("tech")) return "technology";
        if (filenameLower.contains("culture")) return "culture";
        if (filenameLower.contains("event")) return "event";
        if (filenameLower.contains("theme")) return "theme";
        if (filenameLower.contains("glossary")) return "glossary";
        return "misc";
    }

    private String buildSnippet(String content, int maxChars, int maxLines) {
        if (content == null || content.isBlank()) return "";
        String[] lines = content.split("\\R");
        StringBuilder builder = new StringBuilder();
        int lineCount = Math.min(lines.length, maxLines);
        for (int i = 0; i < lineCount; i++) {
            if (builder.length() >= maxChars) break;
            String line = lines[i];
            if (line == null) continue;
            if (builder.length() + line.length() + 1 > maxChars) {
                builder.append(line, 0, Math.max(0, maxChars - builder.length()));
                break;
            }
            builder.append(line).append(' ');
        }
        return builder.toString();
    }

    private String cardFileName(CanonCard card) {
        String prefix = typePrefix(card.getType());
        String slug = slugify(card.getTitle());
        return prefix + "-" + slug + ".json";
    }

    private String typePrefix(String type) {
        if (type == null) return "MISC";
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

    private String stripExtension(String filename) {
        if (filename == null) return "item";
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private String prettifyName(String filename) {
        String base = stripExtension(filename);
        return base.replace('_', ' ').replace('-', ' ').trim();
    }

    private String generateStableId() {
        return UUID.randomUUID().toString();
    }

    private String generateIngestId() {
        return "ING-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private String generateSalt() {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
    }

    private String nowIso() {
        return DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC).format(Instant.now());
    }

    private String sha256Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(content);
            StringBuilder sb = new StringBuilder();
            for (byte b : hashed) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash content", e);
        }
    }

    private String slugify(String value) {
        if (value == null || value.isBlank()) {
            return "item";
        }
        String slug = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        slug = slug.replaceAll("(^-|-$)", "");
        return slug.isEmpty() ? "item" : slug;
    }

    public static class PrepareEmptyPayload {
        public String premise;
        public String genre;
        public String protagonistName;
        public String protagonistRole;
        public String themes;
    }

    public static class PrepareIngestPayload {
        public List<UploadedFile> manuscripts = new ArrayList<>();
        public List<UploadedFile> canonFiles = new ArrayList<>();
        public List<IngestIgnoredInput> ignoredInputs = new ArrayList<>();

        public int totalFiles() {
            return (manuscripts != null ? manuscripts.size() : 0) + (canonFiles != null ? canonFiles.size() : 0);
        }
    }

    public static class PreparationResult {
        public boolean ok;
        public boolean alreadyPrepared;
        public String mode;
        public int cardsCreated;
        public int scenesCreated;

        static PreparationResult alreadyPrepared() {
            PreparationResult result = new PreparationResult();
            result.ok = false;
            result.alreadyPrepared = true;
            return result;
        }

        static PreparationResult success(String mode, int cardsCreated, int scenesCreated) {
            PreparationResult result = new PreparationResult();
            result.ok = true;
            result.mode = mode;
            result.cardsCreated = cardsCreated;
            result.scenesCreated = scenesCreated;
            return result;
        }
    }
}
