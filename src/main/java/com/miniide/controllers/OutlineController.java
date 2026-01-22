package com.miniide.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniide.ProjectContext;
import com.miniide.models.OutlineDocument;
import com.miniide.models.OutlineScene;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OutlineController implements Controller {

    private static final int MIN_SCENES = 6;
    private static final int MIN_MEDIAN_BODY = 80;
    private static final double MAX_TINY_RATIO = 0.35;
    private static final int MAX_EMPTY_BODIES = 2;
    private static final int LABEL_MAX_WORDS = 16;
    private static final int FINGERPRINT_SNIPPET = 240;
    private static final String OUTLINE_VIRTUAL_PATH = "Story/SCN-outline.md";

    private static final Pattern HEADING_H1 = Pattern.compile("^#\\s+\\S");
    private static final Pattern HEADING_H2 = Pattern.compile("^##\\s+\\S");
    private static final Pattern HEADING_H3 = Pattern.compile("^###\\s+\\S");
    private static final Pattern HEADING_ANY = Pattern.compile("^#{1,6}\\s+\\S");

    private static final Pattern LABEL_SCENE_NUMBER = Pattern.compile("^scene\\s*\\d+\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern LABEL_SCENE_PREFIX = Pattern.compile("^scene\\s*[:\\u2014-]\\s*\\S", Pattern.CASE_INSENSITIVE);
    private static final Pattern LABEL_SCENE_WITH_TITLE = Pattern.compile("^scene\\s+\\d+\\s*[:\\u2014-]\\s*\\S", Pattern.CASE_INSENSITIVE);

    private final ProjectContext projectContext;
    private final ObjectMapper objectMapper;

    public OutlineController(ProjectContext projectContext, ObjectMapper objectMapper) {
        this.projectContext = projectContext;
        this.objectMapper = objectMapper;
    }

    @Override
    public void registerRoutes(Javalin app) {
        app.get("/api/outline", this::getOutline);
        app.put("/api/outline", this::saveOutline);
    }

    private void getOutline(Context ctx) {
        try {
            OutlineDocument outline = loadOutline();
            Map<String, Object> payload = new HashMap<>();
            payload.put("outline", outline);
            ctx.json(payload);
        } catch (Exception e) {
            ctx.status(500).json(Controller.errorBody(e));
        }
    }

    private void saveOutline(Context ctx) {
        try {
            OutlineDocument outline = objectMapper.readValue(ctx.body(), OutlineDocument.class);
            OutlineDocument saved = persistOutline(outline);
            ctx.json(saved);
        } catch (Exception e) {
            ctx.status(400).json(Controller.errorBody(e));
        }
    }

    private OutlineDocument persistOutline(OutlineDocument outline) throws Exception {
        if (outline == null) {
            throw new IllegalArgumentException("Outline payload is required");
        }
        long now = System.currentTimeMillis();
        if (outline.getId() == null || outline.getId().isBlank()) {
            outline.setId(UUID.randomUUID().toString());
        }
        if (outline.getTitle() == null || outline.getTitle().isBlank()) {
            outline.setTitle("Story Outline");
        }
        if (outline.getVersion() <= 0) {
            outline.setVersion(1);
        }
        if (outline.getCreatedAt() <= 0) {
            outline.setCreatedAt(now);
        }
        outline.setUpdatedAt(now);

        Path path = outlinePath();
        Files.createDirectories(path.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), outline);
        return outline;
    }

    private OutlineDocument loadOutline() throws Exception {
        Path outlinePath = outlinePath();
        OutlineDocument outline = null;
        if (Files.exists(outlinePath)) {
            outline = objectMapper.readValue(outlinePath.toFile(), OutlineDocument.class);
        }
        outline = ensureOutlineDefaults(outline);

        boolean hasScenes = outline != null && outline.getScenes() != null && !outline.getScenes().isEmpty();
        boolean needsParse = !hasScenes || outlineScenesBlank(outline);
        if (needsParse) {
            String source = loadOutlineSource();
            if (source != null && !source.isBlank()) {
                OutlineDocument parsed = parseOutlineFromSourceContent(source, outline);
                return persistOutline(parsed);
            }
        }

        return outline;
    }

    private boolean outlineScenesBlank(OutlineDocument outline) {
        if (outline == null || outline.getScenes() == null || outline.getScenes().isEmpty()) {
            return true;
        }
        for (OutlineScene scene : outline.getScenes()) {
            if (scene == null) {
                continue;
            }
            if (scene.getSummary() != null && !scene.getSummary().isBlank()) {
                return false;
            }
            if (scene.getTitle() != null && !scene.getTitle().isBlank()) {
                return false;
            }
        }
        return true;
    }

    private OutlineDocument ensureOutlineDefaults(OutlineDocument outline) {
        long now = System.currentTimeMillis();
        if (outline == null) {
            outline = new OutlineDocument();
        }
        if (outline.getId() == null || outline.getId().isBlank()) {
            outline.setId(UUID.randomUUID().toString());
        }
        if (outline.getTitle() == null || outline.getTitle().isBlank()) {
            outline.setTitle("Story Outline");
        }
        if (outline.getVersion() <= 0) {
            outline.setVersion(1);
        }
        if (outline.getCreatedAt() <= 0) {
            outline.setCreatedAt(now);
        }
        if (outline.getUpdatedAt() <= 0) {
            outline.setUpdatedAt(outline.getCreatedAt());
        }
        if (outline.getScenes() == null) {
            outline.setScenes(new ArrayList<>());
        }
        return outline;
    }

    private OutlineDocument parseOutlineFromSourceContent(String raw, OutlineDocument base) {
        String normalized = normalizeNewlines(raw);
        List<String> lines = Arrays.asList(normalized.split("\\n", -1));

        ParseCandidate best = selectBestCandidate(lines);
        List<OutlineScene> scenes = buildScenes(best.segments, base);

        OutlineDocument outline = base != null ? base : new OutlineDocument();
        String docTitle = extractDocumentTitle(lines);
        if (outline.getTitle() == null || outline.getTitle().isBlank()) {
            outline.setTitle(docTitle != null ? docTitle : "Story Outline");
        }
        outline.setScenes(scenes);
        return outline;
    }

    private String loadOutlineSource() throws Exception {
        if (isPreparedProject()) {
            try {
                return projectContext.preparedWorkspace().readFile(OUTLINE_VIRTUAL_PATH);
            } catch (Exception e) {
                return null;
            }
        }
        Path sourcePath = outlineSourcePath();
        if (Files.exists(sourcePath)) {
            return Files.readString(sourcePath, StandardCharsets.UTF_8);
        }
        return null;
    }

    private boolean isPreparedProject() {
        return projectContext != null
            && projectContext.preparation() != null
            && projectContext.preparation().isVirtualReady();
    }

    private List<OutlineScene> buildScenes(List<SceneSegment> segments, OutlineDocument base) {
        List<OutlineScene> scenes = new ArrayList<>();
        Map<String, String> fingerprintToId = new HashMap<>();
        Set<String> usedIds = new HashSet<>();

        if (base != null && base.getScenes() != null) {
            for (OutlineScene scene : base.getScenes()) {
                if (scene == null || scene.getSceneId() == null) {
                    continue;
                }
                String fingerprint = fingerprint(scene.getTitle(), scene.getSummary());
                fingerprintToId.put(fingerprint, scene.getSceneId());
                usedIds.add(scene.getSceneId());
            }
        }

        int index = 1;
        for (SceneSegment segment : segments) {
            TitleAndBody resolved = resolveTitleAndBody(segment);
            String body = resolved.body;
            String title = resolved.title;

            String sceneId = fingerprintToId.get(fingerprint(title, body));
            if (sceneId == null || sceneId.isBlank()) {
                String baseId = "scn-" + fingerprint(title, body);
                sceneId = baseId;
                int suffix = 2;
                while (usedIds.contains(sceneId)) {
                    sceneId = baseId + "-" + suffix;
                    suffix++;
                }
            }
            usedIds.add(sceneId);

            OutlineScene scene = new OutlineScene();
            scene.setSceneId(sceneId);
            scene.setTitle(title.isBlank() ? "Scene " + index : title);
            scene.setSummary(body);
            scenes.add(scene);
            index++;
        }

        return scenes;
    }

    private TitleAndBody resolveTitleAndBody(SceneSegment segment) {
        String body = segment.body == null ? "" : segment.body.trim();
        String heading = extractHeadingText(segment.headingLine);
        if (heading == null || heading.isBlank()) {
            heading = extractHeadingTextFromBody(body);
            if (heading != null && !heading.isBlank()) {
                body = stripLeadingHeading(body);
            }
        }
        String title = heading != null && !heading.isBlank() ? heading.trim() : firstSentence(body);
        return new TitleAndBody(title, body);
    }

    private ParseCandidate selectBestCandidate(List<String> lines) {
        List<ParseCandidate> candidates = new ArrayList<>();
        candidates.add(buildCandidate("headings", buildHeadingSegments(lines), 0.10));
        candidates.add(buildCandidate("labels", buildLabelSegments(lines), 0.06));
        candidates.add(buildCandidate("dividers", buildDividerSegments(lines), 0.03));
        candidates.add(buildCandidate("paragraphs", buildParagraphSegments(lines), 0.0));

        List<ParseCandidate> viable = new ArrayList<>();
        for (ParseCandidate candidate : candidates) {
            if (candidate.passesGates()) {
                viable.add(candidate);
            }
        }

        List<ParseCandidate> pool = viable.isEmpty() ? candidates : viable;
        pool.sort(Comparator.comparingDouble((ParseCandidate c) -> c.score).reversed());
        return pool.get(0);
    }

    private ParseCandidate buildCandidate(String method, List<SceneSegment> segments, double bias) {
        CandidateStats stats = computeStats(segments);
        double score = computeScore(stats, bias);
        return new ParseCandidate(method, segments, stats, score);
    }

    private CandidateStats computeStats(List<SceneSegment> segments) {
        List<Integer> lengths = new ArrayList<>();
        int emptyBodies = 0;
        int tinyBodies = 0;

        for (SceneSegment segment : segments) {
            String body = segment.body == null ? "" : segment.body.trim();
            int length = body.length();
            lengths.add(length);
            if (length == 0) {
                emptyBodies++;
            }
            if (length > 0 && length < 40) {
                tinyBodies++;
            }
        }

        double median = median(lengths);
        double mean = mean(lengths);
        double stddev = standardDeviation(lengths, mean);
        double coefVar = mean > 0 ? stddev / mean : 1.0;
        double tinyRatio = lengths.isEmpty() ? 0.0 : (double) tinyBodies / lengths.size();

        return new CandidateStats(lengths.size(), median, tinyRatio, emptyBodies, coefVar);
    }

    private double computeScore(CandidateStats stats, double bias) {
        double medianScore = clamp(stats.medianBodyChars / 300.0, 0.0, 1.0);
        double varianceScore = 1.0 - clamp(stats.coefVar, 0.0, 1.0);
        double countScore = clamp(stats.count / 50.0, 0.0, 1.0);
        return 0.45 * medianScore + 0.35 * varianceScore + 0.20 * countScore + bias;
    }

    private List<SceneSegment> buildHeadingSegments(List<String> lines) {
        List<Integer> headingIndexes = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (HEADING_H2.matcher(line).find()) {
                headingIndexes.add(i);
            }
        }
        if (headingIndexes.isEmpty()) {
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (HEADING_H3.matcher(line).find()) {
                    headingIndexes.add(i);
                }
            }
        }

        if (headingIndexes.isEmpty()) {
            return new ArrayList<>();
        }

        List<SceneSegment> segments = new ArrayList<>();
        for (int i = 0; i < headingIndexes.size(); i++) {
            int start = headingIndexes.get(i);
            int end = (i + 1 < headingIndexes.size()) ? headingIndexes.get(i + 1) : lines.size();
            String headingLine = lines.get(start);
            String body = joinLines(lines, start + 1, end);
            segments.add(new SceneSegment(headingLine, body));
        }
        return segments;
    }

    private List<SceneSegment> buildLabelSegments(List<String> lines) {
        List<Integer> labelIndexes = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (isLabelLine(lines.get(i))) {
                labelIndexes.add(i);
            }
        }

        if (labelIndexes.isEmpty()) {
            return new ArrayList<>();
        }

        List<SceneSegment> segments = new ArrayList<>();
        for (int i = 0; i < labelIndexes.size(); i++) {
            int start = labelIndexes.get(i);
            int end = (i + 1 < labelIndexes.size()) ? labelIndexes.get(i + 1) : lines.size();
            String body = joinLines(lines, start, end);
            segments.add(new SceneSegment(null, body));
        }
        return segments;
    }

    private boolean isLabelLine(String line) {
        if (line == null) {
            return false;
        }
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        boolean matches = LABEL_SCENE_NUMBER.matcher(trimmed).find()
            || LABEL_SCENE_PREFIX.matcher(trimmed).find()
            || LABEL_SCENE_WITH_TITLE.matcher(trimmed).find();
        if (!matches) {
            return false;
        }
        int wordCount = countWords(trimmed);
        if (wordCount > LABEL_MAX_WORDS && !LABEL_SCENE_WITH_TITLE.matcher(trimmed).find()) {
            return false;
        }
        return true;
    }

    private List<SceneSegment> buildDividerSegments(List<String> lines) {
        List<SceneSegment> segments = new ArrayList<>();
        List<String> buffer = new ArrayList<>();

        for (String line : lines) {
            if (isDividerLine(line)) {
                if (!buffer.isEmpty()) {
                    segments.add(new SceneSegment(null, String.join("\n", buffer)));
                    buffer.clear();
                }
            } else {
                buffer.add(line);
            }
        }

        if (!buffer.isEmpty()) {
            segments.add(new SceneSegment(null, String.join("\n", buffer)));
        }

        return segments;
    }

    private boolean isDividerLine(String line) {
        if (line == null) {
            return false;
        }
        String trimmed = line.trim();
        if (trimmed.equals("***") || trimmed.equals("---") || trimmed.equals("___")) {
            return true;
        }
        if (trimmed.matches("^(\\*\\s*){3,}$")) {
            return true;
        }
        if (trimmed.matches("^(-\\s*){3,}$")) {
            return true;
        }
        if (trimmed.matches("^(_\\s*){3,}$")) {
            return true;
        }
        return false;
    }

    private List<SceneSegment> buildParagraphSegments(List<String> lines) {
        List<SceneSegment> segments = new ArrayList<>();
        List<String> buffer = new ArrayList<>();
        boolean seenContent = false;

        for (String line : lines) {
            if (line.trim().isEmpty()) {
                if (seenContent && !buffer.isEmpty()) {
                    segments.add(new SceneSegment(null, String.join("\n", buffer)));
                    buffer.clear();
                    seenContent = false;
                }
                continue;
            }
            seenContent = true;
            buffer.add(line);
        }

        if (!buffer.isEmpty()) {
            segments.add(new SceneSegment(null, String.join("\n", buffer)));
        }

        return segments;
    }

    private String extractDocumentTitle(List<String> lines) {
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (HEADING_H1.matcher(trimmed).find()) {
                return stripHeadingPrefix(trimmed);
            }
            break;
        }
        return null;
    }

    private String extractHeadingText(String headingLine) {
        if (headingLine == null) {
            return null;
        }
        String trimmed = headingLine.trim();
        if (!HEADING_ANY.matcher(trimmed).find()) {
            return null;
        }
        return stripHeadingPrefix(trimmed);
    }

    private String extractHeadingTextFromBody(String body) {
        if (body == null) {
            return null;
        }
        String[] lines = body.split("\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (HEADING_ANY.matcher(trimmed).find()) {
                return stripHeadingPrefix(trimmed);
            }
            break;
        }
        return null;
    }

    private String stripHeadingPrefix(String line) {
        return line.replaceFirst("^#{1,6}\\s+", "").trim();
    }

    private String stripLeadingHeading(String body) {
        if (body == null) {
            return "";
        }
        String[] lines = body.split("\\n", -1);
        int start = 0;
        while (start < lines.length && lines[start].trim().isEmpty()) {
            start++;
        }
        if (start < lines.length && HEADING_ANY.matcher(lines[start].trim()).find()) {
            start++;
        }
        return joinLines(Arrays.asList(lines), start, lines.length).trim();
    }

    private String firstSentence(String body) {
        if (body == null) {
            return "";
        }
        String trimmed = body.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        Matcher matcher = Pattern.compile("(.+?[.!?])(?:\\s|$)", Pattern.DOTALL).matcher(trimmed);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        int newline = trimmed.indexOf('\n');
        if (newline > 0) {
            return trimmed.substring(0, newline).trim();
        }
        return trimmed;
    }

    private String fingerprint(String title, String body) {
        String safeTitle = title == null ? "" : title.trim();
        String safeBody = body == null ? "" : body.trim();
        String snippet = safeBody.length() > FINGERPRINT_SNIPPET
            ? safeBody.substring(0, FINGERPRINT_SNIPPET)
            : safeBody;
        String base = safeTitle + "\n" + snippet;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(base.getBytes(StandardCharsets.UTF_8));
            return toHex(hash).substring(0, 12);
        } catch (Exception e) {
            return Integer.toHexString(base.hashCode());
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String joinLines(List<String> lines, int start, int end) {
        if (start >= end || start < 0 || end > lines.size()) {
            return "";
        }
        return String.join("\n", lines.subList(start, end));
    }

    private String normalizeNewlines(String text) {
        return text.replace("\r\n", "\n").replace("\r", "\n");
    }

    private double median(List<Integer> values) {
        if (values.isEmpty()) {
            return 0.0;
        }
        List<Integer> sorted = new ArrayList<>(values);
        sorted.sort(Integer::compareTo);
        int mid = sorted.size() / 2;
        if (sorted.size() % 2 == 0) {
            return (sorted.get(mid - 1) + sorted.get(mid)) / 2.0;
        }
        return sorted.get(mid);
    }

    private double mean(List<Integer> values) {
        if (values.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        for (int val : values) {
            sum += val;
        }
        return sum / values.size();
    }

    private double standardDeviation(List<Integer> values, double mean) {
        if (values.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        for (int val : values) {
            double diff = val - mean;
            sum += diff * diff;
        }
        return Math.sqrt(sum / values.size());
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private int countWords(String line) {
        if (line == null) {
            return 0;
        }
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return 0;
        }
        return trimmed.split("\\s+").length;
    }

    private Path outlinePath() {
        return projectContext.currentRoot()
            .resolve(".control-room")
            .resolve("story")
            .resolve("outline.json");
    }

    private Path outlineSourcePath() {
        return projectContext.currentRoot()
            .resolve("docs")
            .resolve("outline.md");
    }

    private static class SceneSegment {
        private final String headingLine;
        private final String body;

        private SceneSegment(String headingLine, String body) {
            this.headingLine = headingLine;
            this.body = body;
        }
    }

    private static class CandidateStats {
        private final int count;
        private final double medianBodyChars;
        private final double tinyRatio;
        private final int emptyBodies;
        private final double coefVar;

        private CandidateStats(int count, double medianBodyChars, double tinyRatio, int emptyBodies, double coefVar) {
            this.count = count;
            this.medianBodyChars = medianBodyChars;
            this.tinyRatio = tinyRatio;
            this.emptyBodies = emptyBodies;
            this.coefVar = coefVar;
        }
    }

    private static class ParseCandidate {
        private final String method;
        private final List<SceneSegment> segments;
        private final CandidateStats stats;
        private final double score;

        private ParseCandidate(String method, List<SceneSegment> segments, CandidateStats stats, double score) {
            this.method = method;
            this.segments = segments;
            this.stats = stats;
            this.score = score;
        }

        private boolean passesGates() {
            return stats.count >= MIN_SCENES
                && stats.medianBodyChars >= MIN_MEDIAN_BODY
                && stats.tinyRatio <= MAX_TINY_RATIO
                && stats.emptyBodies <= MAX_EMPTY_BODIES;
        }
    }

    private static class TitleAndBody {
        private final String title;
        private final String body;

        private TitleAndBody(String title, String body) {
            this.title = title == null ? "" : title;
            this.body = body == null ? "" : body;
        }
    }
}
