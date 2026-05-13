package com.back.coach.domain.github.service;

import com.back.coach.domain.context.service.ContextSnapshotPublisher;
import com.back.coach.domain.github.dto.GithubAnalysisPayload;
import com.back.coach.domain.github.entity.GithubAnalysis;
import com.back.coach.domain.github.entity.GithubConnection;
import com.back.coach.domain.github.entity.GithubProject;
import com.back.coach.domain.github.repository.GithubAnalysisRepository;
import com.back.coach.domain.github.repository.GithubConnectionRepository;
import com.back.coach.domain.github.repository.GithubProjectRepository;
import com.back.coach.domain.github.service.fetcher.GithubMetadataFetcher;
import com.back.coach.domain.github.service.summary.DiffPreprocessor;
import com.back.coach.domain.github.service.summary.RepoSummaryPromptBuilder;
import com.back.coach.domain.github.service.summary.RepoSummaryResponseParser;
import com.back.coach.domain.github.service.summary.ResolvedChampion;
import com.back.coach.domain.github.service.synthesis.SynthesisPromptBuilder;
import com.back.coach.domain.github.service.synthesis.SynthesisResponseParser;
import com.back.coach.domain.github.service.triage.ChampionTriageService;
import com.back.coach.external.llm.LlmClient;
import com.back.coach.external.llm.PromptDirectives;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// Slice 2 메인 orchestrator. Triage → per-repo summary → synthesis → persist.
@Service
public class GithubAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(GithubAnalysisService.class);
    private static final int SUMMARY_TRUNCATE = 500;
    private static final ObjectMapper METADATA_MAPPER = new ObjectMapper();

    private final GithubConnectionRepository connectionRepo;
    private final GithubProjectRepository projectRepo;
    private final GithubAnalysisRepository analysisRepo;
    private final StaticSignalAggregator signalAggregator;
    private final ChampionTriageService triageService;
    private final DiffPreprocessor diffPreprocessor;
    private final RepoSummaryPromptBuilder summaryPromptBuilder;
    private final RepoSummaryResponseParser summaryResponseParser;
    private final SynthesisPromptBuilder synthesisPromptBuilder;
    private final SynthesisResponseParser synthesisResponseParser;
    private final GithubAnalysisPayloadJson payloadJson;
    private final LlmClient llmClient;
    private final TransactionTemplate transactionTemplate;
    private final ContextSnapshotPublisher contextSnapshotPublisher;
    private final GithubMetadataFetcher metadataFetcher;

    public GithubAnalysisService(GithubConnectionRepository connectionRepo,
                                 GithubProjectRepository projectRepo,
                                 GithubAnalysisRepository analysisRepo,
                                 StaticSignalAggregator signalAggregator,
                                 ChampionTriageService triageService,
                                 DiffPreprocessor diffPreprocessor,
                                 RepoSummaryPromptBuilder summaryPromptBuilder,
                                 RepoSummaryResponseParser summaryResponseParser,
                                 SynthesisPromptBuilder synthesisPromptBuilder,
                                 SynthesisResponseParser synthesisResponseParser,
                                 GithubAnalysisPayloadJson payloadJson,
                                 LlmClient llmClient,
                                 TransactionTemplate transactionTemplate,
                                 ContextSnapshotPublisher contextSnapshotPublisher,
                                 GithubMetadataFetcher metadataFetcher) {
        this.connectionRepo = connectionRepo;
        this.projectRepo = projectRepo;
        this.analysisRepo = analysisRepo;
        this.signalAggregator = signalAggregator;
        this.triageService = triageService;
        this.diffPreprocessor = diffPreprocessor;
        this.summaryPromptBuilder = summaryPromptBuilder;
        this.summaryResponseParser = summaryResponseParser;
        this.synthesisPromptBuilder = synthesisPromptBuilder;
        this.synthesisResponseParser = synthesisResponseParser;
        this.payloadJson = payloadJson;
        this.llmClient = llmClient;
        this.transactionTemplate = transactionTemplate;
        this.contextSnapshotPublisher = contextSnapshotPublisher;
        this.metadataFetcher = metadataFetcher;
    }

    public GithubAnalysisResult run(Long userId, Long githubConnectionId,
                                    List<Long> selectedRepoIds, List<Long> coreRepoIds) {
        long analysisStartMs = System.currentTimeMillis();
        validateInputs(selectedRepoIds, coreRepoIds);

        // Slice 6: 선택된 repo에 한해 metadata를 분석 시점에 fetch한다.
        // #283 트랜잭션 분리 의도와 맞게 HTTP 호출은 트랜잭션 밖에서 수행하고,
        // 각 projectRepo.save 만 짧은 auto-tx로 들어간다.
        fetchAndPersistMetadataForSelected(userId, githubConnectionId, selectedRepoIds);

        AnalysisInputs inputs = transactionTemplate.execute(status ->
                loadInputs(userId, githubConnectionId, selectedRepoIds, coreRepoIds)
        );

        List<GithubAnalysisPayload.RepoSummary> repoSummaries = new ArrayList<>();
        long triageElapsedMs = 0, summaryElapsedMs = 0;
        int triagePromptBytes = 0, summaryPromptBytes = 0;

        for (RepoAnalysisInput core : inputs.coreProjects()) {
            long repoStartMs = System.currentTimeMillis();
            RepoMetadata metadata = core.metadata();
            if (isEmptyMetadata(metadata)) {
                log.warn("분석 skip: 메타데이터 없음 repo={}", core.repoFullName());
                continue;
            }
            ChampionTriageService.TriageResult triage;
            try {
                long triageStartMs = System.currentTimeMillis();
                triage = triageService.triage(core.repoFullName(), core.repoUrl(), metadata);
                triageElapsedMs += System.currentTimeMillis() - triageStartMs;
                log.debug("Triage completed: repo={}, elapsedMs={}, champions={}",
                        core.repoFullName(), triageElapsedMs, triage.champions().size());
            } catch (ServiceException e) {
                log.warn("분석 skip: 기여 커밋 없음 repo={}", core.repoFullName());
                continue;
            }

            List<ResolvedChampion> resolved = resolveChampions(triage.champions(), metadata);
            String summaryPrompt = summaryPromptBuilder.build(
                    String.valueOf(core.id()), core.repoFullName(),
                    core.primaryLanguage(), resolved);
            summaryPromptBytes += summaryPrompt.getBytes().length;
            long summaryStartMs = System.currentTimeMillis();
            String summaryResponse = llmClient.complete(PromptDirectives.USER_VISIBLE_KOREAN_JSON_ONLY, summaryPrompt);
            summaryElapsedMs += System.currentTimeMillis() - summaryStartMs;
            repoSummaries.add(summaryResponseParser.parse(summaryResponse));
            long repoElapsedMs = System.currentTimeMillis() - repoStartMs;
            log.debug("Repo analysis completed: repo={}, totalElapsedMs={}",
                    core.repoFullName(), repoElapsedMs);
        }

        long synthesisStartMs = System.currentTimeMillis();
        String synthesisPrompt = synthesisPromptBuilder.build(inputs.signals(), repoSummaries);
        int synthesisPromptBytes = synthesisPrompt.getBytes().length;
        log.debug("Synthesis prompt built: bytes={}", synthesisPromptBytes);
        String synthesisResponse = llmClient.complete(PromptDirectives.USER_VISIBLE_KOREAN_JSON_ONLY, synthesisPrompt);
        SynthesisResponseParser.SynthesisResult synthesis = synthesisResponseParser.parse(synthesisResponse);
        long synthesisElapsedMs = System.currentTimeMillis() - synthesisStartMs;
        log.debug("Synthesis completed: elapsedMs={}", synthesisElapsedMs);

        GithubAnalysisPayload payload = new GithubAnalysisPayload(
                inputs.signals(), repoSummaries,
                synthesis.techTags(), synthesis.depthEstimates(), synthesis.evidences(),
                List.of(), synthesis.finalTechProfile()
        );

        GithubAnalysis saved = transactionTemplate.execute(status -> {
            int version = nextVersion(userId);
            String summary = composeSummary(synthesis.finalTechProfile());
            GithubAnalysis savedAnalysis = analysisRepo.save(
                    GithubAnalysis.create(userId, githubConnectionId, version, summary, payloadJson.toJson(payload))
            );
            contextSnapshotPublisher.publishProfile(userId);
            return savedAnalysis;
        });
        long totalElapsedMs = System.currentTimeMillis() - analysisStartMs;
        AnalysisMetrics metrics = new AnalysisMetrics(
                totalElapsedMs,
                repoSummaries.size(),
                triagePromptBytes,
                triageElapsedMs,
                summaryPromptBytes,
                summaryElapsedMs,
                synthesisPromptBytes,
                synthesisElapsedMs
        );
        log.debug("Analysis completed: totalElapsedMs={}, repo={}, metrics={}",
                totalElapsedMs, repoSummaries.size(), metrics);
        return new GithubAnalysisResult(saved.getId(), saved.getVersion(), payload, saved.getSummary(),
                saved.getCreatedAt() == null ? Instant.now() : saved.getCreatedAt(), metrics);
    }

    private void fetchAndPersistMetadataForSelected(Long userId, Long githubConnectionId, List<Long> selectedRepoIds) {
        GithubConnection connection = connectionRepo.findByIdAndUserId(githubConnectionId, userId)
                .orElseThrow(() -> new ServiceException(ErrorCode.FORBIDDEN));

        Set<Long> selectedIdSet = new HashSet<>(selectedRepoIds);
        List<GithubProject> selected = projectRepo
                .findByUserIdAndGithubConnectionId(userId, githubConnectionId).stream()
                .filter(p -> selectedIdSet.contains(p.getId()))
                .toList();
        if (selected.size() != selectedRepoIds.size()) {
            throw new ServiceException(ErrorCode.INVALID_INPUT, "선택된 저장소 일부가 사용자의 것이 아닙니다");
        }

        String token = connection.getAccessToken();
        String login = connection.getGithubLogin();
        for (GithubProject project : selected) {
            String[] parts = project.getRepoFullName().split("/", 2);
            if (parts.length < 2) {
                log.warn("repo_full_name 형식 비정상, metadata fetch skip: {}", project.getRepoFullName());
                continue;
            }
            String owner = parts[0];
            String repo = parts[1];
            try {
                RepoMetadata metadata = metadataFetcher.fetch(token, owner, repo, login);
                String json = METADATA_MAPPER.writeValueAsString(metadata);
                project.updateMetadataPayload(json);
                projectRepo.save(project);
            } catch (JsonProcessingException e) {
                log.warn("metadata 직렬화 실패 repo={} reason={}", project.getRepoFullName(), e.getMessage());
            } catch (ServiceException e) {
                log.warn("GitHub API 오류로 metadata fetch 실패 repo={} code={} message={}",
                        project.getRepoFullName(), e.getErrorCode(), e.getMessage());
            }
        }
    }

    private AnalysisInputs loadInputs(Long userId, Long githubConnectionId,
                                      List<Long> selectedRepoIds, List<Long> coreRepoIds) {
        if (!connectionRepo.existsByIdAndUserId(githubConnectionId, userId)) {
            throw new ServiceException(ErrorCode.FORBIDDEN);
        }

        Map<Long, GithubProject> projectsById = projectRepo
                .findByUserIdAndGithubConnectionId(userId, githubConnectionId).stream()
                .collect(Collectors.toMap(GithubProject::getId, p -> p));

        List<RepoAnalysisInput> selected = pickProjects(projectsById, selectedRepoIds).stream()
                .map(this::toRepoInput)
                .toList();
        List<RepoAnalysisInput> coreProjects = pickProjects(projectsById, coreRepoIds).stream()
                .map(this::toRepoInput)
                .toList();

        List<StaticSignalAggregator.RepoSignalInput> signalInputs = selected.stream()
                .map(p -> new StaticSignalAggregator.RepoSignalInput(p.primaryLanguage(), p.metadata()))
                .toList();
        GithubAnalysisPayload.StaticSignals signals = signalAggregator.aggregate(signalInputs);
        return new AnalysisInputs(signals, coreProjects);
    }

    private RepoAnalysisInput toRepoInput(GithubProject project) {
        return new RepoAnalysisInput(
                project.getId(),
                project.getRepoFullName(),
                project.getRepoUrl(),
                project.getPrimaryLanguage(),
                parseMetadata(project.getMetadataPayload())
        );
    }

    private void validateInputs(List<Long> selectedRepoIds, List<Long> coreRepoIds) {
        if (selectedRepoIds == null || selectedRepoIds.isEmpty()) {
            throw new ServiceException(ErrorCode.INVALID_INPUT, "selectedRepositoryIds 가 비어 있습니다.");
        }
        Set<Long> selectedSet = new HashSet<>(selectedRepoIds);
        if (coreRepoIds != null && !selectedSet.containsAll(coreRepoIds)) {
            throw new ServiceException(ErrorCode.INVALID_INPUT, "coreRepositoryIds 는 selectedRepositoryIds 의 부분집합이어야 합니다.");
        }
    }

    private List<GithubProject> pickProjects(Map<Long, GithubProject> byId, List<Long> ids) {
        List<GithubProject> picked = new ArrayList<>();
        for (Long id : ids) {
            GithubProject p = byId.get(id);
            if (p == null) {
                throw new ServiceException(ErrorCode.INVALID_INPUT, "해당 사용자의 저장소가 아닙니다: " + id);
            }
            picked.add(p);
        }
        return picked;
    }

    private RepoMetadata parseMetadata(String json) {
        if (json == null || json.isBlank()) return emptyMetadata();
        try {
            return METADATA_MAPPER.readValue(json, RepoMetadata.class);
        } catch (IOException e) {
            log.warn("github_projects.metadata_payload 파싱 실패, 빈 메타로 대체: {}", e.getMessage());
            return emptyMetadata();
        }
    }

    private static RepoMetadata emptyMetadata() {
        return new RepoMetadata(null, Map.of(), List.of(), List.of(), List.of(), List.of());
    }

    private boolean isEmptyMetadata(RepoMetadata metadata) {
        return (metadata.commits() == null || metadata.commits().isEmpty())
                && (metadata.pullRequests() == null || metadata.pullRequests().isEmpty())
                && (metadata.issues() == null || metadata.issues().isEmpty());
    }

    private List<ResolvedChampion> resolveChampions(List<Champion> champions, RepoMetadata metadata) {
        List<ResolvedChampion> out = new ArrayList<>();
        for (Champion c : champions) {
            switch (c.kind()) {
                case COMMIT -> findCommit(metadata, c.ref()).ifPresent(commit -> {
                    List<String> subsequent = subsequentSubjects(metadata, c.ref());
                    out.add(new ResolvedChampion(c.kind(), c.ref(), commit.subject(),
                            diffPreprocessor.clean(commit.diffExcerpt()), subsequent));
                });
                case PR -> findPr(metadata, c.ref()).ifPresent(pr ->
                        out.add(new ResolvedChampion(c.kind(), c.ref(), pr.title(),
                                pr.bodyExcerpt() == null ? "" : pr.bodyExcerpt())));
                case ISSUE -> findIssue(metadata, c.ref()).ifPresent(issue ->
                        out.add(new ResolvedChampion(c.kind(), c.ref(), issue.title(),
                                buildIssueBody(issue))));
            }
        }
        return out;
    }

    private static java.util.Optional<RepoMetadata.CommitItem> findCommit(RepoMetadata m, String sha) {
        return m.commits() == null ? java.util.Optional.empty()
                : m.commits().stream().filter(c -> sha.equals(c.sha())).findFirst();
    }

    // GitHub API는 커밋을 최신순(newest-first)으로 반환한다.
    // 따라서 champion SHA의 인덱스보다 앞(lower index)에 있는 커밋이 champion 이후에 온 커밋이다.
    private static List<String> subsequentSubjects(RepoMetadata m, String sha) {
        if (m.commits() == null) return List.of();
        List<RepoMetadata.CommitItem> commits = m.commits();
        int idx = -1;
        for (int i = 0; i < commits.size(); i++) {
            if (sha.equals(commits.get(i).sha())) { idx = i; break; }
        }
        if (idx <= 0) return List.of();
        return commits.subList(0, Math.min(idx, 5)).stream()
                .map(RepoMetadata.CommitItem::subject)
                .toList();
    }

    private static java.util.Optional<RepoMetadata.PullRequestItem> findPr(RepoMetadata m, String ref) {
        if (m.pullRequests() == null) return java.util.Optional.empty();
        try {
            int n = Integer.parseInt(ref);
            return m.pullRequests().stream().filter(p -> p.number() == n).findFirst();
        } catch (NumberFormatException e) {
            return java.util.Optional.empty();
        }
    }

    private static java.util.Optional<RepoMetadata.IssueItem> findIssue(RepoMetadata m, String ref) {
        if (m.issues() == null) return java.util.Optional.empty();
        try {
            int n = Integer.parseInt(ref);
            return m.issues().stream().filter(i -> i.number() == n).findFirst();
        } catch (NumberFormatException e) {
            return java.util.Optional.empty();
        }
    }

    private static String buildIssueBody(RepoMetadata.IssueItem issue) {
        StringBuilder sb = new StringBuilder();
        if (issue.bodyExcerpt() != null) sb.append(issue.bodyExcerpt()).append("\n");
        if (issue.commentExcerpts() != null) {
            issue.commentExcerpts().forEach(c -> sb.append("- ").append(c).append("\n"));
        }
        return sb.toString();
    }

    private int nextVersion(Long userId) {
        Integer max = analysisRepo.findMaxVersionByUserId(userId);
        return max == null ? 1 : max + 1;
    }

    private String composeSummary(GithubAnalysisPayload.FinalTechProfile profile) {
        String text = "확정 스킬: " + String.join(", ", profile.confirmedSkills())
                + " | 집중 영역: " + String.join(", ", profile.focusAreas());
        return text.length() > SUMMARY_TRUNCATE ? text.substring(0, SUMMARY_TRUNCATE) : text;
    }

    public record GithubAnalysisResult(Long id, int version, GithubAnalysisPayload payload,
                                       String summary, Instant createdAt, AnalysisMetrics metrics) {}

    public record AnalysisMetrics(
            long totalElapsedMs,
            int repoCount,
            int triagePromptBytes,
            long triageElapsedMs,
            int summaryPromptBytes,
            long summaryElapsedMs,
            int synthesisPromptBytes,
            long synthesisElapsedMs
    ) {}

    private record AnalysisInputs(
            GithubAnalysisPayload.StaticSignals signals,
            List<RepoAnalysisInput> coreProjects
    ) {}

    private record RepoAnalysisInput(
            Long id,
            String repoFullName,
            String repoUrl,
            String primaryLanguage,
            RepoMetadata metadata
    ) {}
}
