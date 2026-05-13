package com.back.coach.domain.portfolio.service;

import com.back.coach.domain.coach.entity.CoachConversation;
import com.back.coach.domain.coach.repository.CoachConversationRepository;
import com.back.coach.domain.github.dto.GithubAnalysisPayload;
import com.back.coach.domain.github.entity.GithubAnalysis;
import com.back.coach.domain.github.repository.GithubAnalysisRepository;
import com.back.coach.domain.github.service.GithubAnalysisPayloadJson;
import com.back.coach.domain.portfolio.dto.PortfolioDraftDetailResponse;
import com.back.coach.domain.portfolio.dto.PortfolioDraftPayload;
import com.back.coach.domain.portfolio.dto.PortfolioDraftSummaryResponse;
import com.back.coach.domain.portfolio.dto.PortfolioDraftUpdateRequest;
import com.back.coach.domain.portfolio.dto.PortfolioDraftVariant;
import com.back.coach.domain.portfolio.entity.PortfolioDraft;
import com.back.coach.domain.portfolio.repository.PortfolioDraftRepository;
import com.back.coach.domain.roadmap.entity.LearningRoadmap;
import com.back.coach.domain.roadmap.entity.ProgressLog;
import com.back.coach.domain.roadmap.entity.RoadmapWeek;
import com.back.coach.domain.roadmap.repository.LearningRoadmapRepository;
import com.back.coach.domain.roadmap.repository.ProgressLogRepository;
import com.back.coach.domain.roadmap.repository.RoadmapWeekRepository;
import com.back.coach.external.llm.LlmClient;
import com.back.coach.external.llm.LlmJsonResponseExtractor;
import com.back.coach.external.llm.PromptDirectives;
import com.back.coach.global.code.CoachMessageRole;
import com.back.coach.global.code.ProgressStatus;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class PortfolioDraftService {

    private static final int RECENT_GITHUB_ANALYSIS_LIMIT = 5;
    private static final int RECENT_CONVERSATION_LIMIT = 20;
    private static final int PORTFOLIO_DRAFT_MAX_TOKENS = 6000;
    private static final String DEFAULT_DRAFT_TITLE = "포트폴리오 초안";
    private static final List<String> VARIANT_KEYS = List.of("DONE", "DONE_IN_PROGRESS", "ALL");
    private static final List<SectionSpec> SECTION_SPECS = List.of(
            new SectionSpec("overview", "개요", SectionRenderMode.PARAGRAPH),
            new SectionSpec("problemGoal", "문제/목표", SectionRenderMode.PARAGRAPH),
            new SectionSpec("studyAndImplementation", "구현 내용", SectionRenderMode.BULLET),
            new SectionSpec("techStack", "사용 기술", SectionRenderMode.BULLET),
            new SectionSpec("lessons", "기술적 판단과 배운 점", SectionRenderMode.PARAGRAPH),
            new SectionSpec("nextImprovements", "다음 개선", SectionRenderMode.BULLET),
            new SectionSpec("memoCandidates", "학습 메모 후보", SectionRenderMode.BULLET),
            new SectionSpec("recommendationCandidates", "추천/예정 후보", SectionRenderMode.BULLET),
            new SectionSpec("sourceSummary", "사용 근거", SectionRenderMode.BULLET)
    );

    private static final String SYSTEM_PROMPT = """
            You create Korean project write-up draft sections from the provided source JSON.
            The goal is a portfolio/project description draft, not a study checklist or simple learning summary.
            Output exactly one JSON object. Do not output markdown, prose, code fences, or explanations.

            Evidence rules:
            - DONE may use only officialStudyRecords.done.
            - DONE_IN_PROGRESS may use only officialStudyRecords.done and officialStudyRecords.inProgress.
            - ALL may use done, inProgress, planned, and coachRecommendations, but planned and coachRecommendations must be written only as future plans.
            - coachConversationCandidates.userMemos are memo candidates only, never completed facts.
            - Use github.repoSummaries, github.evidences, and github.skills only as GitHub evidence.
            - Do not invent URLs, books, courses, repository names, metrics, or project names.
            - Never convert IN_PROGRESS or TODO/planned records into completed facts.
            - Do not claim performance improvement, verification completion, production operation, or project completion unless a DONE progress note explicitly supports it.

            Writing rules:
            - Reconstruct learning records as project experience candidates: problem, implementation context, technical decision, and next improvement.
            - Do not write bare checklist fragments like "Redis 기본 명령어 학습 완료" unless the source only supports a candidate note.
            - overview, problemGoal, and lessons must be paragraph strings that explain meaning and context.
            - studyAndImplementation should describe what was implemented, organized, or prepared and why it matters for a project write-up.
            - IN_PROGRESS work must use Korean expressions like "진행 중", "확인 중", "설계 중", or "적용 중"; never "완료", "검증했다", or "운영했다".
            - TODO/planned work must appear only as next improvement or recommendation candidates, using future tense.
            - If evidence is weak, phrase it as "초안 후보" or "다음 개선 후보"; never present it as completed work.

            Required JSON schema:
            {
              "title": "프로젝트 기술서 초안 제목",
              "sections": { "overview": [], "problemGoal": [], "studyAndImplementation": [], "techStack": [], "lessons": [], "nextImprovements": [], "memoCandidates": [], "recommendationCandidates": [], "sourceSummary": [] }
            }

            Hard output constraints:
            - Top-level fields must be exactly title and sections.
            - Do not output fields named variants, content, draftPayload, data, markdown, or responseText.
            - Every section field value must be an array of Korean strings. Never use objects or nested arrays.
            - Each array must contain 1 to 3 strings. Each string must be 260 Korean characters or fewer.
            - Do not include markdown headings such as ##. The server will render markdown later.
            """;

    private final PortfolioDraftRepository portfolioDraftRepository;
    private final LearningRoadmapRepository learningRoadmapRepository;
    private final RoadmapWeekRepository roadmapWeekRepository;
    private final ProgressLogRepository progressLogRepository;
    private final GithubAnalysisRepository githubAnalysisRepository;
    private final GithubAnalysisPayloadJson githubAnalysisPayloadJson;
    private final CoachConversationRepository coachConversationRepository;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public PortfolioDraftService(
            PortfolioDraftRepository portfolioDraftRepository,
            LearningRoadmapRepository learningRoadmapRepository,
            RoadmapWeekRepository roadmapWeekRepository,
            ProgressLogRepository progressLogRepository,
            GithubAnalysisRepository githubAnalysisRepository,
            GithubAnalysisPayloadJson githubAnalysisPayloadJson,
            CoachConversationRepository coachConversationRepository,
            LlmClient llmClient,
            ObjectMapper objectMapper
    ) {
        this.portfolioDraftRepository = portfolioDraftRepository;
        this.learningRoadmapRepository = learningRoadmapRepository;
        this.roadmapWeekRepository = roadmapWeekRepository;
        this.progressLogRepository = progressLogRepository;
        this.githubAnalysisRepository = githubAnalysisRepository;
        this.githubAnalysisPayloadJson = githubAnalysisPayloadJson;
        this.coachConversationRepository = coachConversationRepository;
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    public PortfolioDraftDetailResponse createDraft(Long userId) {
        PortfolioSource source = buildSource(userId);
        PortfolioDraft draft = portfolioDraftRepository.save(PortfolioDraft.create(
                userId,
                DEFAULT_DRAFT_TITLE,
                writeJson(emptyDraftPayload()),
                writeJson(source.sourceRefs())
        ));
        return toDetailResponse(draft);
    }

    @Transactional
    public PortfolioDraftDetailResponse generateVariant(Long userId, Long draftId, String variantKey) {
        VariantSpec spec = variantSpec(variantKey);
        PortfolioDraft draft = findOwnedDraft(userId, draftId);
        PortfolioDraftPayload currentPayload = parseDraftPayload(draft.getDraftPayload());
        PortfolioDraftVariant currentVariant = findVariant(currentPayload, spec.key());
        if (isGenerated(currentVariant)) {
            return toDetailResponse(draft);
        }

        PortfolioSource source = buildSource(userId);
        ObjectNode variantSource = filterSourceForVariant(source.sourcePayload(), spec);
        GeneratedVariant generated = generateVariantContent(variantSource, spec);
        PortfolioDraftPayload updatedPayload = mergeVariant(
                currentPayload,
                new PortfolioDraftVariant(spec.key(), spec.label(), generated.content(), true)
        );
        draft.updateGeneratedContent(
                nextTitle(draft.getTitle(), generated.title()),
                writeJson(updatedPayload),
                writeJson(source.sourceRefs())
        );
        return toDetailResponse(draft);
    }

    private PortfolioDraftPayload emptyDraftPayload() {
        return new PortfolioDraftPayload(
                "PROJECT_WRITEUP",
                VARIANT_KEYS.stream()
                        .map(key -> new PortfolioDraftVariant(key, variantLabel(key), "", false))
                        .toList()
        );
    }

    private GeneratedVariant generateVariantContent(ObjectNode sourcePayload, VariantSpec spec) {
        try {
            String raw = llmClient.complete(
                    PromptDirectives.USER_VISIBLE_KOREAN_JSON_ONLY + "\n\n" + SYSTEM_PROMPT,
                    buildVariantUserPrompt(sourcePayload, spec),
                    PORTFOLIO_DRAFT_MAX_TOKENS
            );
            return parseVariantGeneration(raw);
        } catch (ServiceException e) {
            if (isFallbackAllowed(e.getErrorCode())) {
                return new GeneratedVariant(
                        "로드맵 기반 포트폴리오 초안",
                        buildFallbackContent(sourcePayload, spec.includeDone(), spec.includeInProgress(), spec.includePlanned())
                );
            }
            throw e;
        }
    }

    private boolean isFallbackAllowed(ErrorCode errorCode) {
        return errorCode == ErrorCode.LLM_INVALID_RESPONSE
                || errorCode == ErrorCode.LLM_TIMEOUT
                || errorCode == ErrorCode.LLM_RATE_LIMITED
                || errorCode == ErrorCode.ANALYSIS_FAILED;
    }

    @Transactional(readOnly = true)
    public List<PortfolioDraftSummaryResponse> listDrafts(Long userId) {
        return portfolioDraftRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(PortfolioDraftSummaryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PortfolioDraftDetailResponse getDraft(Long userId, Long draftId) {
        return toDetailResponse(findOwnedDraft(userId, draftId));
    }

    @Transactional
    public PortfolioDraftDetailResponse updateDraft(Long userId, Long draftId, PortfolioDraftUpdateRequest request) {
        PortfolioDraft draft = findOwnedDraft(userId, draftId);
        draft.update(request.title(), writeJson(request.draftPayload()));
        return toDetailResponse(draft);
    }

    private PortfolioDraft findOwnedDraft(Long userId, Long draftId) {
        return portfolioDraftRepository.findByIdAndUserId(draftId, userId)
                .orElseThrow(() -> new ServiceException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private PortfolioDraftPayload parseDraftPayload(String draftPayload) {
        try {
            return objectMapper.readValue(draftPayload, PortfolioDraftPayload.class);
        } catch (JsonProcessingException e) {
            throw new ServiceException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private PortfolioDraftVariant findVariant(PortfolioDraftPayload payload, String key) {
        if (payload == null || payload.variants() == null) {
            return null;
        }
        return payload.variants().stream()
                .filter(variant -> key.equals(variant.key()))
                .findFirst()
                .orElse(null);
    }

    private boolean isGenerated(PortfolioDraftVariant variant) {
        if (variant == null) {
            return false;
        }
        return Boolean.TRUE.equals(variant.generated())
                || (variant.content() != null && !variant.content().isBlank());
    }

    private PortfolioDraftPayload mergeVariant(PortfolioDraftPayload payload, PortfolioDraftVariant generatedVariant) {
        LinkedHashMap<String, PortfolioDraftVariant> byKey = new LinkedHashMap<>();
        if (payload != null && payload.variants() != null) {
            for (PortfolioDraftVariant variant : payload.variants()) {
                byKey.put(variant.key(), variant);
            }
        }
        byKey.put(generatedVariant.key(), generatedVariant);

        List<PortfolioDraftVariant> variants = new ArrayList<>();
        for (String key : VARIANT_KEYS) {
            variants.add(byKey.getOrDefault(key, new PortfolioDraftVariant(key, variantLabel(key), "", false)));
        }
        for (Map.Entry<String, PortfolioDraftVariant> entry : byKey.entrySet()) {
            if (!VARIANT_KEYS.contains(entry.getKey())) {
                variants.add(entry.getValue());
            }
        }
        String format = payload == null || payload.format() == null || payload.format().isBlank()
                ? "PROJECT_WRITEUP"
                : payload.format();
        return new PortfolioDraftPayload(format, variants);
    }

    private String nextTitle(String currentTitle, String generatedTitle) {
        if (generatedTitle == null || generatedTitle.isBlank()) {
            return currentTitle == null || currentTitle.isBlank() ? DEFAULT_DRAFT_TITLE : currentTitle;
        }
        if (currentTitle == null || currentTitle.isBlank()
                || DEFAULT_DRAFT_TITLE.equals(currentTitle)
                || "로드맵 기반 포트폴리오 초안".equals(currentTitle)) {
            return generatedTitle;
        }
        return currentTitle;
    }

    private VariantSpec variantSpec(String key) {
        return switch (key) {
            case "DONE" -> new VariantSpec(
                    "DONE",
                    "완료 기반",
                    true,
                    false,
                    false,
                    false,
                    "Use only DONE progress records as official study evidence. Exclude in-progress, planned, and coach recommendations."
            );
            case "DONE_IN_PROGRESS" -> new VariantSpec(
                    "DONE_IN_PROGRESS",
                    "완료 + 진행 중",
                    true,
                    true,
                    false,
                    false,
                    "Use DONE and IN_PROGRESS records. Treat in-progress work as ongoing, never as completed. Exclude planned and coach recommendations."
            );
            case "ALL" -> new VariantSpec(
                    "ALL",
                    "전체 계획 포함",
                    true,
                    true,
                    true,
                    true,
                    "Use done, in-progress, planned, and coach recommendations. Planned and recommendation items must be future plans only."
            );
            default -> throw new ServiceException(ErrorCode.INVALID_INPUT, "지원하지 않는 포트폴리오 초안 버전입니다.");
        };
    }

    private PortfolioSource buildSource(Long userId) {
        ObjectNode source = objectMapper.createObjectNode();
        ObjectNode refs = objectMapper.createObjectNode();
        source.put("generatedAt", Instant.now().toString());

        ObjectNode official = source.putObject("officialStudyRecords");
        ArrayNode done = official.putArray("done");
        ArrayNode inProgress = official.putArray("inProgress");
        ArrayNode planned = official.putArray("planned");

        List<LearningRoadmap> roadmaps = learningRoadmapRepository.findByUserIdOrderByCreatedAtDesc(userId);
        Map<Long, LearningRoadmap> roadmapById = new LinkedHashMap<>();
        List<RoadmapWeek> allWeeks = new ArrayList<>();
        for (LearningRoadmap roadmap : roadmaps) {
            roadmapById.put(roadmap.getId(), roadmap);
            allWeeks.addAll(roadmapWeekRepository.findByRoadmapIdOrderByWeekNumberAsc(roadmap.getId()));
        }

        List<Long> weekIds = allWeeks.stream().map(RoadmapWeek::getId).toList();
        Map<Long, ProgressLog> latestProgressByWeekId = latestProgressByWeekId(userId, weekIds);
        Long latestRoadmapId = roadmaps.isEmpty() ? null : roadmaps.get(0).getId();
        LinkedHashMap<Long, LearningRoadmap> usedRoadmaps = new LinkedHashMap<>();
        LinkedHashSet<Long> usedWeekIds = new LinkedHashSet<>();
        LinkedHashSet<Long> usedProgressIds = new LinkedHashSet<>();

        for (RoadmapWeek week : allWeeks) {
            LearningRoadmap roadmap = roadmapById.get(week.getRoadmapId());
            ProgressLog progress = latestProgressByWeekId.get(week.getId());
            ProgressStatus status = progress == null ? null : progress.getStatus();

            if (status == ProgressStatus.DONE) {
                done.add(weekNode(roadmap, week, progress));
            } else if (status == ProgressStatus.IN_PROGRESS) {
                inProgress.add(weekNode(roadmap, week, progress));
            } else if (week.getRoadmapId().equals(latestRoadmapId)
                    && (status == null || status == ProgressStatus.TODO)) {
                planned.add(weekNode(roadmap, week, progress));
            } else {
                continue;
            }

            if (roadmap != null) {
                usedRoadmaps.put(roadmap.getId(), roadmap);
            }
            usedWeekIds.add(week.getId());
            if (progress != null) {
                usedProgressIds.add(progress.getId());
            }
        }

        addGithubSource(userId, source, refs);
        addCoachSource(userId, source, refs);
        addRoadmapRefs(refs, usedRoadmaps.values(), usedWeekIds, usedProgressIds);
        return new PortfolioSource(source, refs);
    }

    private Map<Long, ProgressLog> latestProgressByWeekId(Long userId, Collection<Long> weekIds) {
        Map<Long, ProgressLog> latest = new LinkedHashMap<>();
        if (weekIds.isEmpty()) {
            return latest;
        }
        for (ProgressLog progressLog : progressLogRepository
                .findByUserIdAndRoadmapWeekIdInOrderByCreatedAtDesc(userId, weekIds)) {
            latest.putIfAbsent(progressLog.getRoadmapWeekId(), progressLog);
        }
        return latest;
    }

    private ObjectNode weekNode(LearningRoadmap roadmap, RoadmapWeek week, ProgressLog progress) {
        ObjectNode node = objectMapper.createObjectNode();
        if (roadmap != null) {
            node.put("roadmapId", String.valueOf(roadmap.getId()));
            node.put("roadmapVersion", roadmap.getVersion());
            node.put("roadmapSummary", roadmap.getSummary());
        }
        node.put("roadmapWeekId", String.valueOf(week.getId()));
        node.put("weekNumber", week.getWeekNumber());
        node.put("topic", week.getTopic());
        node.put("reason", week.getReasonText());
        if (week.getEstimatedHours() != null) {
            node.put("estimatedHours", week.getEstimatedHours());
        }
        node.set("tasks", readArrayOrEmpty(week.getTasksJson()));
        node.set("materials", readArrayOrEmpty(week.getMaterialsJson()));
        if (progress != null) {
            node.put("progressLogId", String.valueOf(progress.getId()));
            node.put("progressStatus", progress.getStatus().name());
            if (progress.getNote() != null && !progress.getNote().isBlank()) {
                node.put("progressNote", progress.getNote());
            }
            if (progress.getCreatedAt() != null) {
                node.put("progressUpdatedAt", progress.getCreatedAt().toString());
            }
        } else {
            node.put("progressStatus", "NOT_STARTED");
        }
        return node;
    }

    private void addGithubSource(Long userId, ObjectNode source, ObjectNode refs) {
        List<GithubAnalysis> analyses = githubAnalysisRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .limit(RECENT_GITHUB_ANALYSIS_LIMIT)
                .toList();
        ObjectNode github = source.putObject("github");
        ArrayNode repoSummaries = github.putArray("repoSummaries");
        ArrayNode evidences = github.putArray("evidences");
        ArrayNode skills = github.putArray("skills");
        ArrayNode focusAreas = github.putArray("focusAreas");
        ArrayNode analysisRefs = refs.putArray("githubAnalyses");

        LinkedHashMap<String, GithubAnalysisPayload.RepoSummary> repoByName = new LinkedHashMap<>();
        LinkedHashMap<String, GithubAnalysisPayload.GithubEvidence> evidenceByKey = new LinkedHashMap<>();
        LinkedHashSet<String> skillSet = new LinkedHashSet<>();
        LinkedHashSet<String> focusAreaSet = new LinkedHashSet<>();

        for (GithubAnalysis analysis : analyses) {
            GithubAnalysisPayload payload;
            try {
                payload = githubAnalysisPayloadJson.fromJson(analysis.getAnalysisPayload());
            } catch (Exception ignored) {
                continue;
            }
            ObjectNode ref = analysisRefs.addObject();
            ref.put("githubAnalysisId", String.valueOf(analysis.getId()));
            ref.put("githubAnalysisVersion", analysis.getVersion());
            for (GithubAnalysisPayload.RepoSummary summary : nullSafe(payload.repoSummaries())) {
                repoByName.putIfAbsent(summary.repoName(), summary);
            }
            for (GithubAnalysisPayload.GithubEvidence evidence : nullSafe(payload.evidences())) {
                evidenceByKey.putIfAbsent(evidence.repoName() + "|" + evidence.source() + "|" + evidence.summary(), evidence);
            }
            if (payload.finalTechProfile() != null) {
                skillSet.addAll(nullSafe(payload.finalTechProfile().confirmedSkills()));
                focusAreaSet.addAll(nullSafe(payload.finalTechProfile().focusAreas()));
            }
        }

        for (GithubAnalysisPayload.RepoSummary summary : repoByName.values()) {
            ObjectNode node = repoSummaries.addObject();
            node.put("repoName", summary.repoName());
            node.put("summary", summary.summary());
            ArrayNode highlights = node.putArray("highlights");
            for (GithubAnalysisPayload.Highlight highlight : nullSafe(summary.highlights())) {
                ObjectNode hn = highlights.addObject();
                hn.put("text", highlight.text());
                if (highlight.status() != null) {
                    hn.put("status", highlight.status().name());
                }
            }
        }
        for (GithubAnalysisPayload.GithubEvidence evidence : evidenceByKey.values()) {
            ObjectNode node = evidences.addObject();
            node.put("repoName", evidence.repoName());
            if (evidence.type() != null) {
                node.put("type", evidence.type().name());
            }
            node.put("source", evidence.source());
            node.put("summary", evidence.summary());
        }
        skillSet.forEach(skills::add);
        focusAreaSet.forEach(focusAreas::add);
    }

    private void addCoachSource(Long userId, ObjectNode source, ObjectNode refs) {
        ObjectNode coach = source.putObject("coachConversationCandidates");
        ArrayNode userMemos = coach.putArray("userMemos");
        ArrayNode coachRecommendations = coach.putArray("coachRecommendations");
        ArrayNode conversationRefs = refs.putArray("coachConversationIds");

        int userCount = 0;
        int coachCount = 0;
        for (CoachConversation conversation : coachConversationRepository.findTop20ByUserIdOrderByCreatedAtDesc(userId)) {
            if (conversation.getRole() == CoachMessageRole.USER && userCount < RECENT_CONVERSATION_LIMIT / 2) {
                userMemos.add(conversationNode(conversation));
                conversationRefs.add(String.valueOf(conversation.getId()));
                userCount++;
            } else if (conversation.getRole() == CoachMessageRole.COACH && coachCount < RECENT_CONVERSATION_LIMIT / 2) {
                coachRecommendations.add(conversationNode(conversation));
                conversationRefs.add(String.valueOf(conversation.getId()));
                coachCount++;
            }
        }
    }

    private ObjectNode conversationNode(CoachConversation conversation) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("conversationId", String.valueOf(conversation.getId()));
        node.put("role", conversation.getRole().name());
        node.put("messageText", truncate(conversation.getMessageText(), 800));
        if (conversation.getDetectedIntent() != null) {
            node.put("detectedIntent", conversation.getDetectedIntent());
        }
        if (conversation.getCreatedAt() != null) {
            node.put("createdAt", conversation.getCreatedAt().toString());
        }
        return node;
    }

    private void addRoadmapRefs(
            ObjectNode refs,
            Collection<LearningRoadmap> roadmaps,
            Collection<Long> weekIds,
            Collection<Long> progressIds
    ) {
        ArrayNode roadmapRefs = refs.putArray("roadmaps");
        for (LearningRoadmap roadmap : roadmaps) {
            ObjectNode ref = roadmapRefs.addObject();
            ref.put("roadmapId", String.valueOf(roadmap.getId()));
            ref.put("roadmapVersion", roadmap.getVersion());
        }
        ArrayNode weekRefs = refs.putArray("roadmapWeekIds");
        weekIds.forEach(id -> weekRefs.add(String.valueOf(id)));
        ArrayNode progressRefs = refs.putArray("progressLogIds");
        progressIds.forEach(id -> progressRefs.add(String.valueOf(id)));
    }

    private ObjectNode filterSourceForVariant(ObjectNode sourcePayload, VariantSpec spec) {
        ObjectNode filtered = sourcePayload.deepCopy();
        ObjectNode official = (ObjectNode) filtered.path("officialStudyRecords");
        official.set("done", spec.includeDone()
                ? sourcePayload.path("officialStudyRecords").path("done").deepCopy()
                : objectMapper.createArrayNode());
        official.set("inProgress", spec.includeInProgress()
                ? sourcePayload.path("officialStudyRecords").path("inProgress").deepCopy()
                : objectMapper.createArrayNode());
        official.set("planned", spec.includePlanned()
                ? sourcePayload.path("officialStudyRecords").path("planned").deepCopy()
                : objectMapper.createArrayNode());

        ObjectNode coach = (ObjectNode) filtered.path("coachConversationCandidates");
        coach.set("coachRecommendations", spec.includeCoachRecommendations()
                ? sourcePayload.path("coachConversationCandidates").path("coachRecommendations").deepCopy()
                : objectMapper.createArrayNode());
        filtered.put("targetVariant", spec.key());
        filtered.put("targetVariantLabel", spec.label());
        return filtered;
    }

    private String buildVariantUserPrompt(ObjectNode sourcePayload, VariantSpec spec) {
        return """
                Create only the %s portfolio draft variant.
                Variant label: %s
                Allowed evidence policy: %s
                Write for a Korean project write-up draft, not a study checklist.
                The server renders markdown later, so return section arrays only.
                Repeat: do not return sectionsByVariant, variants[].content, or markdown.

                Source JSON:
                %s
                """.formatted(spec.key(), spec.label(), spec.policy(), sourcePayload.toPrettyString());
    }

    private GeneratedVariant parseVariantGeneration(String llmRaw) {
        JsonNode root;
        try {
            root = objectMapper.readTree(LlmJsonResponseExtractor.extractJson(llmRaw));
        } catch (IOException e) {
            throw new ServiceException(ErrorCode.LLM_INVALID_RESPONSE);
        }
        String title = text(root, "title");
        if (title == null || title.isBlank()) {
            title = "포트폴리오 기술서 초안";
        }

        JsonNode sections = root.path("sections");
        if (!sections.isObject()) {
            throw new ServiceException(ErrorCode.LLM_INVALID_RESPONSE);
        }
        return new GeneratedVariant(title, renderSections(sections));
    }

    private String renderSections(JsonNode sectionNode) {
        StringBuilder sb = new StringBuilder();
        for (SectionSpec section : SECTION_SPECS) {
            appendSection(sb, section.title());
            if (section.renderMode() == SectionRenderMode.PARAGRAPH) {
                appendParagraphValues(sb, sectionNode.path(section.fieldName()));
            } else {
                appendBulletValues(sb, sectionNode.path(section.fieldName()));
            }
        }
        return sb.toString().trim();
    }

    private void appendParagraphValues(StringBuilder sb, JsonNode values) {
        if (!values.isArray() || values.isEmpty()) {
            appendLine(sb, "작성 가능한 근거가 없습니다.");
            return;
        }
        int appended = 0;
        for (JsonNode value : values) {
            String text = value.isTextual() ? value.asText() : null;
            if (text != null && !text.isBlank()) {
                if (appended > 0) {
                    sb.append('\n');
                }
                appendLine(sb, text);
                appended++;
            }
        }
        if (appended == 0) {
            appendLine(sb, "작성 가능한 근거가 없습니다.");
        }
    }

    private void appendBulletValues(StringBuilder sb, JsonNode values) {
        if (!values.isArray() || values.isEmpty()) {
            appendLine(sb, "- 작성 가능한 근거가 없습니다.");
            return;
        }
        int appended = 0;
        for (JsonNode value : values) {
            String text = value.isTextual() ? value.asText() : null;
            if (text != null && !text.isBlank()) {
                appendLine(sb, "- " + text);
                appended++;
            }
        }
        if (appended == 0) {
            appendLine(sb, "- 작성 가능한 근거가 없습니다.");
        }
    }

    private String variantLabel(String key) {
        return switch (key) {
            case "DONE" -> "완료 기반";
            case "DONE_IN_PROGRESS" -> "완료 + 진행 중";
            case "ALL" -> "전체 계획 포함";
            default -> key;
        };
    }

    private String buildFallbackContent(
            JsonNode sourcePayload,
            boolean includeDone,
            boolean includeInProgress,
            boolean includePlanned
    ) {
        StringBuilder sb = new StringBuilder();
        JsonNode official = sourcePayload.path("officialStudyRecords");
        JsonNode github = sourcePayload.path("github");
        JsonNode coach = sourcePayload.path("coachConversationCandidates");

        appendSection(sb, "개요");
        appendLine(sb, "저장된 로드맵 진도와 GitHub 분석 근거를 프로젝트 기술서 초안으로 연결한 문서입니다. 확인된 완료 기록은 구현 경험의 후보로, 진행 중이거나 예정인 기록은 다음 개선 후보로 분리했습니다.");

        appendSection(sb, "문제/목표");
        appendLine(sb, "로드맵에 흩어진 학습 기록을 구현 맥락, 기술 선택 이유, 다음 개선 계획으로 재구성하는 것이 목표입니다. 근거가 부족한 항목은 완료 사실로 확정하지 않고 초안 후보로만 남깁니다.");

        appendSection(sb, "구현 내용");
        if (includeDone) {
            appendWeekList(sb, "완료", official.path("done"));
        }
        if (includeInProgress) {
            appendWeekList(sb, "진행 중", official.path("inProgress"));
        }
        if (includePlanned) {
            appendWeekList(sb, "예정", official.path("planned"));
        }
        if (sb.charAt(sb.length() - 1) == '\n' && sb.toString().endsWith("구현 내용\n")) {
            appendLine(sb, "- 아직 확정된 학습 진도 근거가 없습니다.");
        }

        appendSection(sb, "사용 기술");
        appendTextValues(sb, github.path("skills"), "- ");

        appendSection(sb, "기술적 판단과 배운 점");
        appendGithubEvidence(sb, github);

        appendSection(sb, "다음 개선");
        if (includePlanned) {
            appendWeekList(sb, "다음 계획", official.path("planned"));
            appendConversationList(sb, coach.path("coachRecommendations"));
        } else {
            appendLine(sb, "- 이 버전에서는 완료 또는 진행 중으로 확인된 항목만 사용했습니다.");
        }

        appendSection(sb, "학습 메모 후보");
        appendConversationList(sb, coach.path("userMemos"));

        appendSection(sb, "추천/예정 후보");
        if (includePlanned) {
            appendConversationList(sb, coach.path("coachRecommendations"));
        } else {
            appendLine(sb, "- 완료 사실로 확정하지 않고 전체 계획 포함 버전에서만 예정 후보로 다룹니다.");
        }

        appendSection(sb, "사용 근거");
        appendLine(sb, "- 로드맵 진도: progress_logs + roadmap_weeks");
        appendLine(sb, "- GitHub 근거: 최근 github_analyses 병합 결과");
        appendLine(sb, "- Coach 대화: 학습 메모와 추천 후보로만 사용");
        return sb.toString().trim();
    }

    private void appendSection(StringBuilder sb, String title) {
        if (!sb.isEmpty()) {
            sb.append("\n\n");
        }
        sb.append("## ").append(title).append('\n');
    }

    private void appendWeekList(StringBuilder sb, String label, JsonNode weeks) {
        if (!weeks.isArray() || weeks.isEmpty()) {
            return;
        }
        for (JsonNode week : weeks) {
            String topic = text(week, "topic");
            String note = text(week, "progressNote");
            appendLine(sb, "- %s: %s%s".formatted(
                    label,
                    topic == null ? "주차 학습" : topic,
                    note == null ? "" : " - " + note
            ));
            appendTaskTitles(sb, week.path("tasks"));
            appendMaterialTitles(sb, week.path("materials"));
        }
    }

    private void appendTaskTitles(StringBuilder sb, JsonNode tasks) {
        if (!tasks.isArray()) {
            return;
        }
        for (JsonNode task : tasks) {
            String title = text(task, "title");
            if (title != null && !title.isBlank()) {
                appendLine(sb, "  - 실행: " + title);
            }
        }
    }

    private void appendMaterialTitles(StringBuilder sb, JsonNode materials) {
        if (!materials.isArray()) {
            return;
        }
        for (JsonNode material : materials) {
            String title = text(material, "title");
            String url = text(material, "url");
            if (title != null && !title.isBlank()) {
                appendLine(sb, "  - 자료: " + title + (url == null ? "" : " (" + url + ")"));
            }
        }
    }

    private void appendGithubEvidence(StringBuilder sb, JsonNode github) {
        JsonNode summaries = github.path("repoSummaries");
        if (summaries.isArray() && !summaries.isEmpty()) {
            for (JsonNode summary : summaries) {
                appendLine(sb, "- " + fallbackText(text(summary, "repoName"), "GitHub repo")
                        + ": " + fallbackText(text(summary, "summary"), "저장된 GitHub 분석 요약"));
            }
        }
        JsonNode evidences = github.path("evidences");
        if (evidences.isArray() && !evidences.isEmpty()) {
            for (JsonNode evidence : evidences) {
                appendLine(sb, "- 근거: " + fallbackText(text(evidence, "summary"), text(evidence, "source")));
            }
        }
    }

    private void appendTextValues(StringBuilder sb, JsonNode values, String prefix) {
        if (!values.isArray() || values.isEmpty()) {
            appendLine(sb, "- 저장된 기술 근거가 부족합니다.");
            return;
        }
        for (JsonNode value : values) {
            appendLine(sb, prefix + value.asText());
        }
    }

    private void appendConversationList(StringBuilder sb, JsonNode conversations) {
        if (!conversations.isArray() || conversations.isEmpty()) {
            appendLine(sb, "- 후보로 사용할 Coach 대화가 없습니다.");
            return;
        }
        for (JsonNode conversation : conversations) {
            String message = text(conversation, "messageText");
            if (message != null && !message.isBlank()) {
                appendLine(sb, "- " + message);
            }
        }
    }

    private void appendLine(StringBuilder sb, String line) {
        sb.append(line).append('\n');
    }

    private String fallbackText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private PortfolioDraftDetailResponse toDetailResponse(PortfolioDraft draft) {
        try {
            return PortfolioDraftDetailResponse.of(
                    draft,
                    objectMapper.readValue(draft.getDraftPayload(), PortfolioDraftPayload.class),
                    objectMapper.readValue(draft.getSourceRefs(), new TypeReference<Map<String, Object>>() {
                    })
            );
        } catch (JsonProcessingException e) {
            throw new ServiceException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new ServiceException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private ArrayNode readArrayOrEmpty(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.createArrayNode();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node.isArray()) {
                return (ArrayNode) node;
            }
        } catch (Exception ignored) {
            // malformed roadmap JSON should not block portfolio draft generation
        }
        return objectMapper.createArrayNode();
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    private <T> List<T> nullSafe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private record PortfolioSource(ObjectNode sourcePayload, ObjectNode sourceRefs) {
    }

    private record GeneratedVariant(String title, String content) {
    }

    private record VariantSpec(
            String key,
            String label,
            boolean includeDone,
            boolean includeInProgress,
            boolean includePlanned,
            boolean includeCoachRecommendations,
            String policy
    ) {
    }

    private enum SectionRenderMode {
        PARAGRAPH,
        BULLET
    }

    private record SectionSpec(String fieldName, String title, SectionRenderMode renderMode) {
    }
}
