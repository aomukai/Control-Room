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
import com.miniide.models.OutlineDocument;
import com.miniide.models.OutlineScene;
import com.miniide.models.StoryManifest;
import com.miniide.models.StoryRegistry;
import com.miniide.models.StoryScene;
import com.miniide.models.WorkspaceMetadata;
import com.miniide.outline.OutlineParser;
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
        if (meta == null) {
            return false;
        }
        String stage = meta.getPrepStage();
        return stage != null && !stage.isBlank() && !"none".equalsIgnoreCase(stage);
    }

    public boolean isVirtualReady() {
        WorkspaceMetadata meta = workspaceService.loadMetadata();
        if (meta == null) {
            return false;
        }
        String stage = meta.getPrepStage();
        return stage != null && !stage.isBlank() && !"none".equalsIgnoreCase(stage);
    }

    public boolean areAgentsUnlocked() {
        WorkspaceMetadata meta = workspaceService.loadMetadata();
        return meta != null && meta.isAgentsUnlocked();
    }

    public WorkspaceMetadata getMetadata() {
        return workspaceService.loadMetadata();
    }

    public PreparationResult prepareEmpty(PrepareEmptyPayload payload) throws IOException {
        if (isPrepared()) {
            return PreparationResult.alreadyPrepared();
        }
        if (payload == null) {
            payload = new PrepareEmptyPayload();
        }
        String premise = payload.premise != null ? payload.premise.trim() : "";
        String storyIdea = payload.storyIdea != null ? payload.storyIdea.trim() : "";
        if (premise.isBlank() && storyIdea.isBlank()) {
            throw new IllegalArgumentException("Premise or story idea is required.");
        }
        logger.info("Preparing empty project scaffold in " + workspaceRoot);
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
        canonManifest.setReviewedAt(now);

        StoryManifest storyManifest = new StoryManifest();
        storyManifest.setSchemaVersion(SCHEMA_VERSION);
        storyManifest.setPreparedAt(now);
        storyManifest.setStatus("prepared");

        List<CanonCard> cards = new ArrayList<>();
        List<StoryScene> scenes = new ArrayList<>();

        if (payload.protagonistName != null && !payload.protagonistName.isBlank()) {
            CanonCard card = new CanonCard();
            card.setOrigin("native");
            card.setType("character");
            card.setTitle(payload.protagonistName.trim());
            card.setStableId(generateStableId());
            card.setDisplayId("CHAR:" + slugify(card.getTitle()));
            card.setContent(buildProtagonistContent(payload));
            card.setCreatedAt(now);
            card.setUpdatedAt(now);
            applyAnnotationDefaults(card, card.getContent());
            card.setAnnotationStatus("complete");
            card.setStatus("active");
            cards.add(card);
        }

        StoryScene scene = new StoryScene();
        scene.setOrigin("native");
        scene.setStableId(generateStableId());
        scene.setDisplayId("SCN:opening");
        scene.setTitle("Story Idea");
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

        saveManifests(ingestManifest, canonManifest, storyManifest, storyRegistry, cards,
            buildEntitiesIndex(cards), buildHooksIndex(cards));
        writeOutlineDocument(scenes);
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

        logger.info("Preparing ingest in " + workspaceRoot + " (files=" + payload.totalFiles() + ")");
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
                    String displayId = "SCN:" + slugify(stripExtension(file.filename()));
                    scene.setStableId(generateIngestStableId(projectSalt, ingestId, evidence.getExcerptHash(), displayId, "SCN"));
                    scene.setDisplayId(displayId);
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
            if (payload.outlines != null && !payload.outlines.isEmpty()) {
                writeOutlineFromUploads(payload.outlines);
            }
            if (payload.canonFiles != null) {
                for (UploadedFile file : payload.canonFiles) {
                    if (file == null) continue;
                    IngestEvidence evidence = storeEvidence(file, now);
                    excerptsStored += 1;
                    CanonCard card = new CanonCard();
                    card.setOrigin("ingest");
                    card.setType(inferCardType(file.filename(), evidence.getContent()));
                    card.setTitle(prettifyName(file.filename()));
                    String cardDisplayId = typePrefix(card.getType()) + ":" + slugify(card.getTitle());
                    card.setDisplayId(cardDisplayId);
                    card.setStableId(generateIngestStableId(projectSalt, ingestId, evidence.getExcerptHash(), cardDisplayId, "CAN"));
                    card.setContent(evidence.getContent());
                    card.setCreatedAt(now);
                    card.setUpdatedAt(now);
                    applyAnnotationDefaults(card, evidence.getContent());
                    card.setAnnotationStatus("complete");
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
        canonManifest.setStatus("draft");

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

        saveManifests(ingestManifest, canonManifest, storyManifest, storyRegistry, cards,
            buildEntitiesIndex(cards), buildHooksIndex(cards));
        if (payload == null || payload.outlines == null || payload.outlines.isEmpty()) {
            writeOutlineDocument(scenes);
        }
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
                               StoryRegistry registry, List<CanonCard> cards,
                               java.util.Map<String, java.util.List<String>> entitiesIndex,
                               java.util.Map<String, java.util.List<String>> hooksIndex) throws IOException {
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
        mapper.writerWithDefaultPrettyPrinter().writeValue(entitiesPath.toFile(), entitiesIndex);
        mapper.writerWithDefaultPrettyPrinter().writeValue(hooksPath.toFile(), hooksIndex);

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

    private void writeOutlineDocument(List<StoryScene> scenes) throws IOException {
        OutlineDocument outline = new OutlineDocument();
        outline.setId(UUID.randomUUID().toString());
        outline.setTitle("Story Outline");
        outline.setVersion(1);
        long now = System.currentTimeMillis();
        outline.setCreatedAt(now);
        outline.setUpdatedAt(now);

        List<OutlineScene> outlineScenes = new ArrayList<>();
        if (scenes != null) {
            int index = 0;
            for (StoryScene scene : scenes) {
                if (scene == null) continue;
                if ("SCN:outline".equalsIgnoreCase(scene.getDisplayId())) {
                    continue;
                }
                OutlineScene outlineScene = new OutlineScene();
                outlineScene.setSceneId(scene.getStableId());
                outlineScene.setTitle(outlineTitleForScene(scene, index));
                outlineScene.setSummary(outlineSummaryForScene(scene));
                outlineScenes.add(outlineScene);
                index += 1;
            }
        }
        outline.setScenes(outlineScenes);

        Path outlinePath = workspaceRoot.resolve(".control-room").resolve("story").resolve("outline.json");
        mapper.writerWithDefaultPrettyPrinter().writeValue(outlinePath.toFile(), outline);
    }

    private void writeOutlineFromUploads(List<UploadedFile> outlines) throws IOException {
        String merged = mergeOutlineUploads(outlines);
        if (merged.isBlank()) {
            return;
        }
        long now = System.currentTimeMillis();
        OutlineDocument base = new OutlineDocument();
        base.setId(UUID.randomUUID().toString());
        base.setTitle("Story Outline");
        base.setVersion(1);
        base.setCreatedAt(now);
        base.setUpdatedAt(now);

        OutlineDocument parsed = OutlineParser.parseOutlineFromSourceContent(merged, base);
        parsed.setUpdatedAt(now);

        Path outlinePath = workspaceRoot.resolve(".control-room").resolve("story").resolve("outline.json");
        mapper.writerWithDefaultPrettyPrinter().writeValue(outlinePath.toFile(), parsed);
    }

    private String mergeOutlineUploads(List<UploadedFile> outlines) throws IOException {
        if (outlines == null || outlines.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (UploadedFile file : outlines) {
            if (file == null) continue;
            String content = readUploadedFile(file);
            if (content == null || content.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append("\n\n---\n\n");
            }
            builder.append(content.trim());
        }
        return builder.toString();
    }

    private void markPrepared(String mode, String now) throws IOException {
        WorkspaceMetadata meta = workspaceService.loadMetadata();
        meta.setPrepared(false);
        meta.setPreparedMode(mode);
        meta.setPreparedAt(now);
        meta.setPrepStage("draft");
        meta.setAgentsUnlocked(false);
        workspaceService.saveMetadata(meta);
    }

    public void finalizePreparation() throws IOException {
        WorkspaceMetadata meta = workspaceService.loadMetadata();
        String now = nowIso();
        meta.setPrepared(true);
        meta.setPreparedAt(now);
        meta.setPrepStage("prepared");
        meta.setAgentsUnlocked(true);
        workspaceService.saveMetadata(meta);
    }

    public PreparationResult prepareSupplementalIngest(PrepareIngestPayload payload) throws IOException {
        WorkspaceMetadata meta = workspaceService.loadMetadata();
        if (meta == null || !"draft".equalsIgnoreCase(meta.getPrepStage())) {
            throw new IllegalStateException("Supplemental ingest is only allowed during preparation.");
        }
        if (payload == null || payload.totalFiles() == 0) {
            throw new IllegalArgumentException("No files provided for ingest.");
        }
        ensurePrepDirectories();
        String now = nowIso();

        IngestManifest ingestManifest = loadIngestManifest();
        if (ingestManifest == null) {
            ingestManifest = new IngestManifest();
            ingestManifest.setSchemaVersion(SCHEMA_VERSION);
            ingestManifest.setIngestId(generateIngestId());
            ingestManifest.setProjectSalt(generateSalt());
            ingestManifest.setStatus("complete");
            ingestManifest.setMode("ingest");
            ingestManifest.setIngestedAt(now);
            ingestManifest.setStats(new IngestStats());
        }
        if (ingestManifest.getIngestId() == null || ingestManifest.getIngestId().isBlank()) {
            ingestManifest.setIngestId(generateIngestId());
        }
        if (ingestManifest.getProjectSalt() == null || ingestManifest.getProjectSalt().isBlank()) {
            ingestManifest.setProjectSalt(generateSalt());
        }
        IngestStats stats = ingestManifest.getStats();
        if (stats == null) {
            stats = new IngestStats();
            ingestManifest.setStats(stats);
        }
        String projectSalt = ingestManifest.getProjectSalt();
        String ingestId = ingestManifest.getIngestId();

        StoryRegistry storyRegistry = loadStoryRegistry();
        List<StoryScene> scenes = storyRegistry.getScenes() != null
            ? new ArrayList<>(storyRegistry.getScenes())
            : new ArrayList<>();
        int baseOrder = scenes.size();
        List<StoryScene> newScenes = new ArrayList<>();
        List<CanonCard> newCards = new ArrayList<>();
        int excerptsStored = 0;

        if (payload.manuscripts != null) {
            for (UploadedFile file : payload.manuscripts) {
                if (file == null) continue;
                IngestEvidence evidence = storeEvidence(file, now);
                excerptsStored += 1;
                StoryScene scene = new StoryScene();
                scene.setOrigin("ingest");
                String displayId = "SCN:" + slugify(stripExtension(file.filename()));
                scene.setStableId(generateIngestStableId(projectSalt, ingestId, evidence.getExcerptHash(), displayId, "SCN"));
                scene.setDisplayId(displayId);
                scene.setTitle(prettifyName(file.filename()));
                scene.setOrder(baseOrder + newScenes.size() + 1);
                scene.setContent(evidence.getContent());
                scene.setCreatedAt(now);
                scene.setUpdatedAt(now);
                scene.setStatus("active");
                scene.setIngestPointers(List.of(pointerForEvidence(evidence)));
                newScenes.add(scene);
            }
        }

        if (payload.canonFiles != null) {
            for (UploadedFile file : payload.canonFiles) {
                if (file == null) continue;
                IngestEvidence evidence = storeEvidence(file, now);
                excerptsStored += 1;
                CanonCard card = new CanonCard();
                card.setOrigin("ingest");
                card.setType(inferCardType(file.filename(), evidence.getContent()));
                card.setTitle(prettifyName(file.filename()));
                String cardDisplayId = typePrefix(card.getType()) + ":" + slugify(card.getTitle());
                card.setDisplayId(cardDisplayId);
                card.setStableId(generateIngestStableId(projectSalt, ingestId, evidence.getExcerptHash(), cardDisplayId, "CAN"));
                card.setContent(evidence.getContent());
                card.setCreatedAt(now);
                card.setUpdatedAt(now);
                applyAnnotationDefaults(card, evidence.getContent());
                card.setAnnotationStatus("complete");
                card.setStatus("active");
                card.setIngestPointers(List.of(pointerForEvidence(evidence)));
                newCards.add(card);
            }
        }

        if (payload.outlines != null && !payload.outlines.isEmpty()) {
            writeOutlineFromUploads(payload.outlines);
        }

        scenes.addAll(newScenes);
        storyRegistry.setScenes(scenes);
        saveStoryRegistry(storyRegistry);

        List<CanonCard> allCards = loadCanonCards();
        allCards.addAll(newCards);
        for (CanonCard card : newCards) {
            saveCanonCard(card);
        }

        CanonManifest canonManifest = loadCanonManifest();
        if (canonManifest == null) {
            canonManifest = new CanonManifest();
            canonManifest.setSchemaVersion(SCHEMA_VERSION);
            canonManifest.setPreparedAt(now);
            canonManifest.setStatus("draft");
        }
        canonManifest.setCardCount(allCards.size());
        saveCanonManifest(canonManifest);

        StoryManifest storyManifest = loadStoryManifest();
        if (storyManifest == null) {
            storyManifest = new StoryManifest();
            storyManifest.setSchemaVersion(SCHEMA_VERSION);
            storyManifest.setPreparedAt(now);
            storyManifest.setStatus("prepared");
        }
        storyManifest.setSceneCount(scenes.size());
        saveStoryManifest(storyManifest);

        stats.setFilesProcessed(stats.getFilesProcessed() + payload.totalFiles());
        stats.setExcerptsStored(stats.getExcerptsStored() + excerptsStored);
        stats.setScenesCreated(stats.getScenesCreated() + newScenes.size());
        stats.setCardsCreated(stats.getCardsCreated() + newCards.size());
        ingestManifest.setIgnoredInputs(payload.ignoredInputs != null ? payload.ignoredInputs : new ArrayList<>());
        ingestManifest.setIngestedAt(now);
        saveIngestManifest(ingestManifest);

        java.util.Map<String, java.util.List<String>> entitiesIndex = buildEntitiesIndex(allCards);
        java.util.Map<String, java.util.List<String>> hooksIndex = buildHooksIndex(allCards);
        Path entitiesPath = workspaceRoot.resolve(".control-room").resolve("canon").resolve("entities.json");
        Path hooksPath = workspaceRoot.resolve(".control-room").resolve("canon").resolve("hooks-index.json");
        mapper.writerWithDefaultPrettyPrinter().writeValue(entitiesPath.toFile(), entitiesIndex);
        mapper.writerWithDefaultPrettyPrinter().writeValue(hooksPath.toFile(), hooksIndex);

        return PreparationResult.success("ingest", newCards.size(), newScenes.size());
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
        if (payload.storyIdea != null && !payload.storyIdea.isBlank()) {
            builder.append("## Story Idea\n\n");
            builder.append(payload.storyIdea.trim()).append("\n\n");
        }
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

    private String outlineSummaryForScene(StoryScene scene) {
        if (scene == null || scene.getContent() == null || scene.getContent().isBlank()) {
            return "";
        }
        String snippet = buildSnippet(scene.getContent(), 240, 4).trim();
        if (snippet.isBlank()) return "";
        int sentenceEnd = -1;
        for (int i = 0; i < snippet.length(); i++) {
            char ch = snippet.charAt(i);
            if (ch == '.' || ch == '!' || ch == '?') {
                sentenceEnd = i + 1;
                break;
            }
        }
        if (sentenceEnd > 0) {
            return snippet.substring(0, sentenceEnd).trim();
        }
        int newline = snippet.indexOf('\n');
        if (newline > 0) {
            return snippet.substring(0, newline).trim();
        }
        return snippet;
    }

    private String outlineTitleForScene(StoryScene scene, int index) {
        if (scene != null && scene.getTitle() != null && !scene.getTitle().isBlank()) {
            return scene.getTitle().trim();
        }
        if (scene != null && scene.getDisplayId() != null && scene.getDisplayId().contains(":")) {
            String slug = scene.getDisplayId().substring(scene.getDisplayId().indexOf(':') + 1);
            return prettifyName(slug);
        }
        return "Scene " + (index + 1);
    }

    private String cardFileName(CanonCard card) {
        String prefix = typePrefix(card.getType());
        String slug = slugify(card.getTitle());
        if (card.getDisplayId() != null && card.getDisplayId().contains(":")) {
            int colonIndex = card.getDisplayId().indexOf(':');
            prefix = card.getDisplayId().substring(0, colonIndex).toUpperCase(Locale.ROOT);
            String displaySlug = card.getDisplayId().substring(colonIndex + 1);
            if (!displaySlug.isBlank()) {
                slug = displaySlug.toLowerCase(Locale.ROOT);
            }
        }
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

    private String generateIngestStableId(String projectSalt, String ingestId, String excerptHash, String anchorKey, String prefix) {
        String seed = String.join(":", projectSalt, ingestId, excerptHash, anchorKey);
        String hash = sha256Hex(seed.getBytes(StandardCharsets.UTF_8));
        String trimmed = hash.length() > 24 ? hash.substring(0, 24) : hash;
        return prefix + "-" + trimmed;
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

    public CanonReviewSummary getCanonReviewSummary() throws IOException {
        CanonManifest manifest = loadCanonManifest();
        CanonReviewSummary summary = new CanonReviewSummary();
        summary.status = manifest != null ? manifest.getStatus() : null;
        summary.preparedAt = manifest != null ? manifest.getPreparedAt() : null;
        summary.reviewedAt = manifest != null ? manifest.getReviewedAt() : null;
        List<CanonCard> cards = loadCanonCards();
        summary.cardCount = cards.size();
        summary.cards = new ArrayList<>();
        for (CanonCard card : cards) {
            CanonCardSummary cardSummary = new CanonCardSummary();
            cardSummary.displayId = card.getDisplayId();
            cardSummary.title = card.getTitle();
            cardSummary.type = card.getType();
            cardSummary.status = card.getStatus();
            cardSummary.annotationStatus = card.getAnnotationStatus();
            cardSummary.path = canonCardPath(card);
            summary.cards.add(cardSummary);
        }
        summary.cards.sort((a, b) -> String.valueOf(a.title).compareToIgnoreCase(String.valueOf(b.title)));
        return summary;
    }

    public PreparationDebugSnapshot getDebugSnapshot() throws IOException {
        PreparationDebugSnapshot snapshot = new PreparationDebugSnapshot();
        snapshot.workspaceRoot = workspaceRoot.toString();
        snapshot.workspaceName = workspaceRoot.getFileName() != null ? workspaceRoot.getFileName().toString() : "";
        WorkspaceMetadata meta = workspaceService.loadMetadata();
        snapshot.prepared = meta != null && meta.isPrepared();
        snapshot.prepStage = meta != null ? meta.getPrepStage() : null;
        snapshot.agentsUnlocked = meta != null && meta.isAgentsUnlocked();
        snapshot.preparedMode = meta != null ? meta.getPreparedMode() : null;
        snapshot.preparedAt = meta != null ? meta.getPreparedAt() : null;

        CanonManifest canonManifest = loadCanonManifest();
        snapshot.canonStatus = canonManifest != null ? canonManifest.getStatus() : null;
        snapshot.canonReviewedAt = canonManifest != null ? canonManifest.getReviewedAt() : null;
        snapshot.canonCardsPath = workspaceRoot.resolve(".control-room").resolve("canon").resolve("cards").toString();
        snapshot.canonCardCount = loadCanonCards().size();

        StoryRegistry storyRegistry = loadStoryRegistry();
        snapshot.sceneCount = storyRegistry.getScenes() != null ? storyRegistry.getScenes().size() : 0;
        snapshot.storyPath = workspaceRoot.resolve(".control-room").resolve("story").toString();
        snapshot.ingestPath = workspaceRoot.resolve(".control-room").resolve("ingest").toString();
        return snapshot;
    }

    public void confirmCanonReview() throws IOException {
        CanonManifest manifest = loadCanonManifest();
        if (manifest == null) {
            throw new IllegalStateException("Canon manifest not found.");
        }
        String now = nowIso();
        manifest.setStatus("prepared");
        manifest.setReviewedAt(now);
        saveCanonManifest(manifest);
    }

    public CanonIndexResult compileCanonIndex(List<CanonIndexEntry> entries) throws IOException {
        if (entries == null || entries.isEmpty()) {
            entries = List.of();
        }
        String now = nowIso();

        // Save canon-meta.json
        Path metaPath = workspaceRoot.resolve(".control-room").resolve("canon").resolve("canon-meta.json");
        Files.createDirectories(metaPath.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(metaPath.toFile(), entries);

        // Render Canon.md deterministically
        String canonMd = renderCanonIndex(entries);
        Path indexPath = workspaceRoot.resolve(".control-room").resolve("canon").resolve("canon-index.md");
        Files.writeString(indexPath, canonMd, StandardCharsets.UTF_8);

        // Update manifest status
        CanonManifest manifest = loadCanonManifest();
        if (manifest == null) {
            manifest = new CanonManifest();
            manifest.setSchemaVersion(1);
            manifest.setPreparedAt(now);
        }
        manifest.setStatus("indexed");
        manifest.setIndexedAt(now);
        saveCanonManifest(manifest);

        // Collect unique categories
        List<String> categories = entries.stream()
            .map(e -> e.type != null ? e.type : "unknown")
            .distinct()
            .sorted()
            .collect(java.util.stream.Collectors.toList());

        CanonIndexResult result = new CanonIndexResult();
        result.status = "indexed";
        result.fileCount = entries.size();
        result.categories = categories;
        result.indexedAt = now;
        return result;
    }

    public boolean isCanonIndexed() throws IOException {
        CanonManifest manifest = loadCanonManifest();
        return manifest != null && "indexed".equals(manifest.getStatus());
    }

    public String readCanonIndex() throws IOException {
        Path indexPath = workspaceRoot.resolve(".control-room").resolve("canon").resolve("canon-index.md");
        if (!Files.exists(indexPath)) return null;
        return Files.readString(indexPath, StandardCharsets.UTF_8);
    }

    private String renderCanonIndex(List<CanonIndexEntry> entries) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Canon Index\n");
        sb.append("> Auto-generated. Maps topics to canon files for agent reference.\n\n");

        // Routing table — group by type
        java.util.Map<String, List<CanonIndexEntry>> byType = new java.util.LinkedHashMap<>();
        for (CanonIndexEntry e : entries) {
            String type = e.type != null ? e.type : "misc";
            byType.computeIfAbsent(type, k -> new ArrayList<>()).add(e);
        }

        sb.append("## Routing Table\n");
        for (java.util.Map.Entry<String, List<CanonIndexEntry>> group : byType.entrySet()) {
            String type = group.getKey();
            int count = group.getValue().size();
            sb.append("- **").append(capitalize(type)).append("** (").append(count)
              .append(count == 1 ? " file" : " files").append(")\n");
        }
        sb.append("\n");

        // Topics index — collect all topics with their source files
        java.util.Map<String, List<String>> topicToFiles = new java.util.LinkedHashMap<>();
        for (CanonIndexEntry e : entries) {
            if (e.topics != null) {
                for (String topic : e.topics) {
                    topicToFiles.computeIfAbsent(topic.toLowerCase(java.util.Locale.ROOT),
                        k -> new ArrayList<>()).add(e.path);
                }
            }
        }
        if (!topicToFiles.isEmpty()) {
            sb.append("## Topics\n");
            List<String> sortedTopics = new ArrayList<>(topicToFiles.keySet());
            java.util.Collections.sort(sortedTopics);
            for (String topic : sortedTopics) {
                List<String> files = topicToFiles.get(topic);
                sb.append("- **").append(topic).append("**: ");
                sb.append(String.join(", ", files)).append("\n");
            }
            sb.append("\n");
        }

        // File catalogue
        sb.append("## File Catalogue\n");
        sb.append("| Path | Type | Scope | Hardness | Key Terms | Summary |\n");
        sb.append("|------|------|-------|----------|-----------|--------|\n");
        for (CanonIndexEntry e : entries) {
            String path = e.path != null ? e.path : "";
            String type = e.type != null ? e.type : "";
            String scope = e.scope != null ? String.join("/", e.scope) : "";
            String hardness = e.hardness != null ? e.hardness : "";
            String terms = "";
            if (e.defines != null && !e.defines.isEmpty()) {
                terms = String.join(", ", e.defines);
            } else if (e.topics != null && !e.topics.isEmpty()) {
                terms = String.join(", ", e.topics);
            }
            String summary = e.summary != null ? e.summary : "";
            sb.append("| ").append(path).append(" | ").append(type)
              .append(" | ").append(scope).append(" | ").append(hardness)
              .append(" | ").append(terms).append(" | ").append(summary).append(" |\n");
        }

        return sb.toString();
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + s.substring(1);
    }

    public static class CanonIndexEntry {
        public String path;
        public String type;
        public List<String> scope;
        public String hardness;
        public List<String> topics;
        public List<String> defines;
        public List<String> constraints;
        public String summary;
    }

    public static class CanonIndexResult {
        public String status;
        public int fileCount;
        public List<String> categories;
        public String indexedAt;
    }

    public CanonManifest loadCanonManifest() throws IOException {
        Path manifestPath = workspaceRoot.resolve(".control-room").resolve("canon").resolve("manifest.json");
        if (!Files.exists(manifestPath)) {
            return null;
        }
        return mapper.readValue(manifestPath.toFile(), CanonManifest.class);
    }

    private StoryManifest loadStoryManifest() throws IOException {
        Path manifestPath = workspaceRoot.resolve(".control-room").resolve("story").resolve("manifest.json");
        if (!Files.exists(manifestPath)) {
            return null;
        }
        return mapper.readValue(manifestPath.toFile(), StoryManifest.class);
    }

    private IngestManifest loadIngestManifest() throws IOException {
        Path manifestPath = workspaceRoot.resolve(".control-room").resolve("ingest").resolve("manifest.json");
        if (!Files.exists(manifestPath)) {
            return null;
        }
        return mapper.readValue(manifestPath.toFile(), IngestManifest.class);
    }

    private void saveCanonManifest(CanonManifest manifest) throws IOException {
        Path manifestPath = workspaceRoot.resolve(".control-room").resolve("canon").resolve("manifest.json");
        Files.createDirectories(manifestPath.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(manifestPath.toFile(), manifest);
    }

    private void saveStoryManifest(StoryManifest manifest) throws IOException {
        Path manifestPath = workspaceRoot.resolve(".control-room").resolve("story").resolve("manifest.json");
        Files.createDirectories(manifestPath.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(manifestPath.toFile(), manifest);
    }

    private void saveIngestManifest(IngestManifest manifest) throws IOException {
        Path manifestPath = workspaceRoot.resolve(".control-room").resolve("ingest").resolve("manifest.json");
        Files.createDirectories(manifestPath.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(manifestPath.toFile(), manifest);
    }

    private List<CanonCard> loadCanonCards() throws IOException {
        List<CanonCard> cards = new ArrayList<>();
        Path cardsDir = workspaceRoot.resolve(".control-room").resolve("canon").resolve("cards");
        if (!Files.exists(cardsDir)) {
            return cards;
        }
        try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(cardsDir, "*.json")) {
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

    private void saveCanonCard(CanonCard card) throws IOException {
        if (card == null) {
            return;
        }
        Path cardPath = workspaceRoot.resolve(".control-room").resolve("canon").resolve("cards")
            .resolve(cardFileName(card));
        Files.createDirectories(cardPath.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(cardPath.toFile(), card);
    }

    private String canonCardPath(CanonCard card) {
        String bucket = bucketFromDisplayId(card.getDisplayId());
        if (bucket == null) {
            bucket = bucketFromType(card.getType());
        }
        String filename = (card.getDisplayId() != null ? card.getDisplayId() : "MISC:item").replace(':', '-') + ".md";
        return "Compendium/" + bucket + "/" + filename;
    }

    private StoryRegistry loadStoryRegistry() throws IOException {
        Path scenesPath = workspaceRoot.resolve(".control-room").resolve("story").resolve("scenes.json");
        if (!Files.exists(scenesPath)) {
            StoryRegistry registry = new StoryRegistry();
            registry.setSchemaVersion(SCHEMA_VERSION);
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
            registry.setSchemaVersion(SCHEMA_VERSION);
            registry.setScenes(new ArrayList<>());
            return registry;
        }
    }

    private void saveStoryRegistry(StoryRegistry registry) throws IOException {
        Path scenesPath = workspaceRoot.resolve(".control-room").resolve("story").resolve("scenes.json");
        Files.createDirectories(scenesPath.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(scenesPath.toFile(), registry);
    }

    private String bucketFromType(String type) {
        if (type == null) {
            return "Misc";
        }
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

    private String bucketFromDisplayId(String displayId) {
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

    private void applyAnnotationDefaults(CanonCard card, String content) {
        if (card == null) {
            return;
        }
        List<String> aliases = new ArrayList<>(card.getAliases());
        aliases.addAll(extractAliases(content));
        card.setAliases(uniqueTrimmed(aliases));

        List<String> hooks = new ArrayList<>();
        addHook(hooks, card.getTitle());
        card.getAliases().forEach(alias -> addHook(hooks, alias));
        extractHeadings(content, 12).forEach(heading -> addHook(hooks, heading));
        if (hooks.size() < 3) {
            addHook(hooks, card.getDisplayId());
        }
        card.setCanonHooks(limitList(uniqueTrimmed(hooks), 12));

        List<String> entities = new ArrayList<>();
        addHook(entities, card.getTitle());
        card.getAliases().forEach(alias -> addHook(entities, alias));
        card.setEntities(uniqueTrimmed(entities));

        String lower = content != null ? content.toLowerCase(Locale.ROOT) : "";
        card.setContinuityCritical(lower.contains("continuity") || lower.contains("critical canon"));
    }

    private List<String> extractAliases(String content) {
        List<String> aliases = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return aliases;
        }
        String[] lines = content.split("\\R");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.toLowerCase(Locale.ROOT).startsWith("aliases:")) {
                String raw = trimmed.substring("aliases:".length()).trim();
                for (String part : raw.split(",")) {
                    String alias = part.trim();
                    if (!alias.isBlank()) {
                        aliases.add(alias);
                    }
                }
                break;
            }
        }
        return aliases;
    }

    private List<String> extractHeadings(String content, int limit) {
        List<String> headings = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return headings;
        }
        for (String line : content.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) {
                String heading = trimmed.replaceFirst("^#+", "").trim();
                if (!heading.isBlank()) {
                    headings.add(heading);
                    if (headings.size() >= limit) {
                        break;
                    }
                }
            }
        }
        return headings;
    }

    private void addHook(List<String> hooks, String value) {
        if (value == null) {
            return;
        }
        String trimmed = value.trim();
        if (!trimmed.isEmpty()) {
            hooks.add(trimmed);
        }
    }

    private List<String> uniqueTrimmed(List<String> values) {
        List<String> result = new ArrayList<>();
        if (values == null) {
            return result;
        }
        for (String value : values) {
            if (value == null) continue;
            String trimmed = value.trim();
            if (trimmed.isEmpty()) continue;
            boolean exists = result.stream().anyMatch(existing -> existing.equalsIgnoreCase(trimmed));
            if (!exists) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private List<String> limitList(List<String> values, int limit) {
        if (values.size() <= limit) {
            return values;
        }
        return new ArrayList<>(values.subList(0, limit));
    }

    private java.util.Map<String, java.util.List<String>> buildEntitiesIndex(List<CanonCard> cards) {
        java.util.Map<String, java.util.List<String>> index = new java.util.LinkedHashMap<>();
        for (CanonCard card : cards) {
            addIndexEntry(index, card.getTitle(), card.getStableId());
            for (String alias : card.getAliases()) {
                addIndexEntry(index, alias, card.getStableId());
            }
        }
        return index;
    }

    private java.util.Map<String, java.util.List<String>> buildHooksIndex(List<CanonCard> cards) {
        java.util.Map<String, java.util.List<String>> index = new java.util.LinkedHashMap<>();
        for (CanonCard card : cards) {
            for (String hook : card.getCanonHooks()) {
                addIndexEntry(index, hook, card.getStableId());
            }
        }
        return index;
    }

    private void addIndexEntry(java.util.Map<String, java.util.List<String>> index, String key, String stableId) {
        if (key == null || key.isBlank() || stableId == null || stableId.isBlank()) {
            return;
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        java.util.List<String> entries = index.computeIfAbsent(normalized, k -> new ArrayList<>());
        boolean exists = entries.stream().anyMatch(existing -> existing.equalsIgnoreCase(stableId));
        if (!exists) {
            entries.add(stableId);
        }
    }

    public static class PrepareEmptyPayload {
        public String premise;
        public String genre;
        public String storyIdea;
        public String protagonistName;
        public String protagonistRole;
        public String themes;
    }

    public static class PrepareIngestPayload {
        public List<UploadedFile> manuscripts = new ArrayList<>();
        public List<UploadedFile> outlines = new ArrayList<>();
        public List<UploadedFile> canonFiles = new ArrayList<>();
        public List<IngestIgnoredInput> ignoredInputs = new ArrayList<>();

        public int totalFiles() {
            return (manuscripts != null ? manuscripts.size() : 0)
                + (outlines != null ? outlines.size() : 0)
                + (canonFiles != null ? canonFiles.size() : 0);
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

    public static class CanonCardSummary {
        public String displayId;
        public String title;
        public String type;
        public String status;
        public String annotationStatus;
        public String path;
    }

    public static class CanonReviewSummary {
        public String status;
        public String preparedAt;
        public String reviewedAt;
        public int cardCount;
        public List<CanonCardSummary> cards = new ArrayList<>();
    }

    public static class PreparationDebugSnapshot {
        public String workspaceRoot;
        public String workspaceName;
        public boolean prepared;
        public String prepStage;
        public boolean agentsUnlocked;
        public String preparedMode;
        public String preparedAt;
        public String canonStatus;
        public String canonReviewedAt;
        public String canonCardsPath;
        public int canonCardCount;
        public int sceneCount;
        public String storyPath;
        public String ingestPath;
    }
}
