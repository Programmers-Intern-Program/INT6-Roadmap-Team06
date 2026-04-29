package com.back.coach.service.github;

import com.back.coach.domain.github.entity.GithubAnalysis;
import com.back.coach.domain.github.entity.GithubProject;
import com.back.coach.domain.github.repository.GithubAnalysisRepository;
import com.back.coach.domain.github.repository.GithubConnectionRepository;
import com.back.coach.domain.github.repository.GithubProjectRepository;
import com.back.coach.external.llm.LlmClient;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import com.back.coach.service.github.summary.DiffPreprocessor;
import com.back.coach.service.github.summary.RepoSummaryPromptBuilder;
import com.back.coach.service.github.summary.RepoSummaryResponseParser;
import com.back.coach.service.github.summary.ResolvedChampion;
import com.back.coach.service.github.synthesis.SynthesisPromptBuilder;
import com.back.coach.service.github.synthesis.SynthesisResponseParser;
import com.back.coach.service.github.triage.ChampionTriageService;
import com.back.coach.service.github.Champion;
import com.back.coach.service.github.RepoMetadata;
import com.back.coach.service.github.StaticSignalAggregator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// Slice 2 메인 orchestrator. Triage → per-repo summary → synthesis → persist.
// AnalysisPayload.meta.triageFallback은 어떤 core repo든 fallback으로 떨어졌으면 true.
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
    private final AnalysisPayloadJson payloadJson;
    private final LlmClient llmClient;

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
                                 AnalysisPayloadJson payloadJson,
                                 LlmClient llmClient) {
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
    }

    @Transactional
    public GithubAnalysisResult run(Long userId, Long githubConnectionId,
                                    List<Long> selectedRepoIds, List<Long> coreRepoIds) {
        validateInputs(selectedRepoIds, coreRepoIds);
        if (!connectionRepo.existsByIdAndUserId(githubConnectionId, userId)) {
            throw new ServiceException(ErrorCode.FORBIDDEN);
        }

        Map<Long, GithubProject> projectsById = projectRepo
                .findByUserIdAndGithubConnectionId(userId, githubConnectionId).stream()
                .collect(Collectors.toMap(GithubProject::getId, p -> p));

        List<GithubProject> selected = pickProjects(projectsById, selectedRepoIds);
        List<GithubProject> coreProjects = pickProjects(projectsById, coreRepoIds);

        List<StaticSignalAggregator.RepoSignalInput> signalInputs = selected.stream()
                .map(p -> new StaticSignalAggregator.RepoSignalInput(
                        p.getPrimaryLanguage(), parseMetadata(p.getMetadataPayload())))
                .toList();
        AnalysisPayload.StaticSignals signals = signalAggregator.aggregate(signalInputs);

        boolean anyFallback = false;
        List<AnalysisPayload.RepoSummary> repoSummaries = new ArrayList<>();
        for (GithubProject core : coreProjects) {
            RepoMetadata metadata = parseMetadata(core.getMetadataPayload());
            ChampionTriageService.TriageResult triage =
                    triageService.triage(core.getRepoFullName(), core.getRepoUrl(), metadata);
            if (triage.fallback()) anyFallback = true;

            List<ResolvedChampion> resolved = resolveChampions(triage.champions(), metadata);
            String summaryPrompt = summaryPromptBuilder.build(
                    String.valueOf(core.getId()), core.getRepoFullName(),
                    core.getPrimaryLanguage(), resolved);
            String summaryResponse = llmClient.complete(summaryPrompt);
            repoSummaries.add(summaryResponseParser.parse(summaryResponse));
        }

        String synthesisPrompt = synthesisPromptBuilder.build(signals, repoSummaries);
        String synthesisResponse = llmClient.complete(synthesisPrompt);
        SynthesisResponseParser.SynthesisResult synthesis = synthesisResponseParser.parse(synthesisResponse);

        AnalysisPayload payload = new AnalysisPayload(
                signals, repoSummaries,
                synthesis.techTags(), synthesis.depthEstimates(), synthesis.evidences(),
                List.of(), synthesis.finalTechProfile(),
                new AnalysisPayload.AnalysisMeta(anyFallback)
        );

        int version = nextVersion(userId);
        String summary = composeSummary(synthesis.finalTechProfile());
        GithubAnalysis saved = analysisRepo.save(
                GithubAnalysis.create(userId, githubConnectionId, version, summary, payloadJson.toJson(payload))
        );
        return new GithubAnalysisResult(saved.getId(), saved.getVersion(), payload, saved.getSummary(),
                saved.getCreatedAt() == null ? Instant.now() : saved.getCreatedAt());
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

    private List<ResolvedChampion> resolveChampions(List<Champion> champions, RepoMetadata metadata) {
        List<ResolvedChampion> out = new ArrayList<>();
        for (Champion c : champions) {
            switch (c.kind()) {
                case COMMIT -> findCommit(metadata, c.ref()).ifPresent(commit ->
                        out.add(new ResolvedChampion(c.kind(), c.ref(), commit.subject(),
                                diffPreprocessor.clean(commit.diffExcerpt()))));
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

    private String composeSummary(AnalysisPayload.FinalTechProfile profile) {
        String text = "확정 스킬: " + String.join(", ", profile.confirmedSkills())
                + " | 집중 영역: " + String.join(", ", profile.focusAreas());
        return text.length() > SUMMARY_TRUNCATE ? text.substring(0, SUMMARY_TRUNCATE) : text;
    }

    public record GithubAnalysisResult(Long id, int version, AnalysisPayload payload,
                                       String summary, Instant createdAt) {}
}
