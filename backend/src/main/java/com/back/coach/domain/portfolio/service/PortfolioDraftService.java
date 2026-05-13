package com.back.coach.domain.portfolio.service;

import com.back.coach.domain.coach.entity.CoachConversation;
import com.back.coach.domain.coach.repository.CoachConversationRepository;
import com.back.coach.domain.github.dto.GithubAnalysisPayload;
import com.back.coach.domain.github.entity.GithubAnalysis;
import com.back.coach.domain.github.repository.GithubAnalysisRepository;
import com.back.coach.domain.github.service.GithubAnalysisPayloadJson;
import com.back.coach.domain.portfolio.dto.PortfolioDraftDetailResponse;
import com.back.coach.domain.portfolio.dto.PortfolioDraftGenerationResult;
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
import java.util.Set;

@Service
public class PortfolioDraftService {

    private static final int RECENT_GITHUB_ANALYSIS_LIMIT = 5;
    private static final int RECENT_CONVERSATION_LIMIT = 20;
    private static final int PORTFOLIO_DRAFT_MAX_TOKENS = 6000;
    private static final Set<String> REQUIRED_VARIANT_KEYS = Set.of("DONE", "DONE_IN_PROGRESS", "ALL");

    private static final String SYSTEM_PROMPT = """
            당신은 개발자 포트폴리오 기술서 초안 작성 도우미입니다.
            반드시 한국어 JSON만 출력합니다.

            원칙:
            - 완료 사실은 officialStudyRecords.done에 있는 항목만 사용합니다.
            - 진행 중 사실은 officialStudyRecords.inProgress에 있는 항목만 사용합니다.
            - planned, coachRecommendations는 예정, 다음 개선, 확장 계획으로만 작성합니다.
            - coachConversationCandidates.userMemos는 학습 메모 후보로만 표시하고 완료 사실로 확정하지 않습니다.
            - 입력에 없는 URL, 책, 강의, 프로젝트명, 수치 성과를 새로 만들지 않습니다.
            - GitHub 근거는 github.repoSummaries, github.evidences, github.skills에 있는 내용만 사용합니다.

            응답 schema:
            {
              "title": "프로젝트 기술서 초안 제목",
              "variants": [
                {"key": "DONE", "label": "완료 기반", "content": "markdown"},
                {"key": "DONE_IN_PROGRESS", "label": "완료 + 진행 중", "content": "markdown"},
                {"key": "ALL", "label": "전체 계획 포함", "content": "markdown"}
              ]
            }

            출력 길이:
            - variants는 정확히 3개만 출력합니다.
            - 각 content는 700~1000자 안에서 작성합니다.
            - 세 content 전체를 합쳐 3500자를 넘기지 않습니다.
            - JSON 밖 설명, markdown fence, 중첩된 draftPayload/data wrapper를 출력하지 않습니다.

            각 content에는 개요, 문제/목표, 진행한 학습과 구현, 사용 기술, 배운 점,
            다음 개선, 학습 메모 후보, 추천/예정 후보, 사용 근거 섹션을 포함합니다.
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
        PortfolioDraftGenerationResult generated = generateDraft(source.sourcePayload());
        PortfolioDraft draft = portfolioDraftRepository.save(PortfolioDraft.create(
                userId,
                generated.title(),
                writeJson(generated.draftPayload()),
                writeJson(source.sourceRefs())
        ));
        return toDetailResponse(draft);
    }

    private PortfolioDraftGenerationResult generateDraft(ObjectNode sourcePayload) {
        try {
            String raw = llmClient.complete(
                    PromptDirectives.USER_VISIBLE_KOREAN_JSON_ONLY + "\n\n" + SYSTEM_PROMPT,
                    buildUserPrompt(sourcePayload),
                    PORTFOLIO_DRAFT_MAX_TOKENS
            );
            return parseGeneration(raw);
        } catch (ServiceException e) {
            if (isFallbackAllowed(e.getErrorCode())) {
                return buildFallbackDraft(sourcePayload);
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

    private String buildUserPrompt(ObjectNode sourcePayload) {
        return """
                아래 source JSON만 근거로 프로젝트 기술서 초안 3개 버전을 작성하세요.
                DONE 버전은 done만 사용하세요.
                DONE_IN_PROGRESS 버전은 done과 inProgress만 사용하세요.
                ALL 버전은 done, inProgress, planned, coachRecommendations를 사용하되 planned와 coachRecommendations는 예정으로만 쓰세요.

                source:
                %s
                """.formatted(sourcePayload.toPrettyString());
    }

    private PortfolioDraftGenerationResult parseGeneration(String llmRaw) {
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

        JsonNode variantsNode = root.path("variants");
        if (!variantsNode.isArray()) {
            throw new ServiceException(ErrorCode.LLM_INVALID_RESPONSE);
        }
        List<PortfolioDraftVariant> variants = new ArrayList<>();
        Set<String> seenKeys = new LinkedHashSet<>();
        for (JsonNode node : variantsNode) {
            String key = text(node, "key");
            String label = text(node, "label");
            String content = text(node, "content");
            if (key == null || label == null || content == null || content.isBlank()) {
                throw new ServiceException(ErrorCode.LLM_INVALID_RESPONSE);
            }
            variants.add(new PortfolioDraftVariant(key, label, content));
            seenKeys.add(key);
        }
        if (!seenKeys.containsAll(REQUIRED_VARIANT_KEYS)) {
            throw new ServiceException(ErrorCode.LLM_INVALID_RESPONSE);
        }
        return new PortfolioDraftGenerationResult(title, new PortfolioDraftPayload("PROJECT_WRITEUP", variants));
    }

    private PortfolioDraftGenerationResult buildFallbackDraft(ObjectNode sourcePayload) {
        List<PortfolioDraftVariant> variants = List.of(
                new PortfolioDraftVariant(
                        "DONE",
                        "완료 기반",
                        buildFallbackContent(sourcePayload, true, false, false)
                ),
                new PortfolioDraftVariant(
                        "DONE_IN_PROGRESS",
                        "완료 + 진행 중",
                        buildFallbackContent(sourcePayload, true, true, false)
                ),
                new PortfolioDraftVariant(
                        "ALL",
                        "전체 계획 포함",
                        buildFallbackContent(sourcePayload, true, true, true)
                )
        );
        return new PortfolioDraftGenerationResult(
                "로드맵 기반 포트폴리오 초안",
                new PortfolioDraftPayload("PROJECT_WRITEUP", variants)
        );
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
        appendLine(sb, "저장된 로드맵 진도와 GitHub 분석 근거를 바탕으로 정리한 프로젝트 기술서 초안입니다.");

        appendSection(sb, "문제/목표");
        appendLine(sb, "학습 로드맵에서 확인된 보완 주제를 실제 구현 경험과 정리 가능한 산출물로 연결하는 것이 목표입니다.");

        appendSection(sb, "진행한 학습과 구현");
        if (includeDone) {
            appendWeekList(sb, "완료", official.path("done"));
        }
        if (includeInProgress) {
            appendWeekList(sb, "진행 중", official.path("inProgress"));
        }
        if (includePlanned) {
            appendWeekList(sb, "예정", official.path("planned"));
        }
        if (sb.charAt(sb.length() - 1) == '\n' && sb.toString().endsWith("진행한 학습과 구현\n")) {
            appendLine(sb, "- 아직 확정된 학습 진도 근거가 없습니다.");
        }

        appendSection(sb, "사용 기술");
        appendTextValues(sb, github.path("skills"), "- ");

        appendSection(sb, "배운 점");
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
}
