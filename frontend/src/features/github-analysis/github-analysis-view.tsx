"use client";

import Link from "next/link";
import { useEffect, useState } from "react";

import { getDashboard } from "@/features/dashboard/api";
import { getGithubAnalysis } from "@/features/github-analysis/api";
import {
  githubDepthLevelClassNames,
  githubDepthLevelLabels,
  githubEvidenceTypeLabels
} from "@/features/github-analysis/labels";
import type {
  DepthEstimate,
  GithubAnalysis,
  GithubUserCorrection
} from "@/features/github-analysis/types";
import { ApiError } from "@/lib/api";

type GithubAnalysisViewProps = {
  initialGithubAnalysisId?: string | null;
};

type GithubAnalysisState =
  | { status: "loading" }
  | { status: "empty"; message: string }
  | { status: "error"; message: string }
  | {
      status: "success";
      analysis: GithubAnalysis;
      diagnosisId: string | null;
    };

export function GithubAnalysisView({
  initialGithubAnalysisId
}: GithubAnalysisViewProps) {
  const [state, setState] = useState<GithubAnalysisState>({
    status: "loading"
  });

  useEffect(() => {
    let ignore = false;

    async function loadGithubAnalysis() {
      setState({ status: "loading" });

      try {
        const queryGithubAnalysisId = normalizeOptionalId(initialGithubAnalysisId);
        const dashboard = queryGithubAnalysisId
          ? await getDashboard().catch(() => null)
          : await getDashboard();
        const githubAnalysisId =
          queryGithubAnalysisId ??
          dashboard?.githubAnalysis?.githubAnalysisId ??
          null;

        if (!githubAnalysisId) {
          if (!ignore) {
            setState({
              message: "저장된 GitHub 분석 결과가 없습니다.",
              status: "empty"
            });
          }
          return;
        }

        const analysis = await getGithubAnalysis(githubAnalysisId);

        if (!ignore) {
          setState({
            analysis,
            diagnosisId: dashboard?.diagnosis?.diagnosisId ?? null,
            status: "success"
          });
        }
      } catch (error) {
        if (!ignore) {
          setState({
            message: getErrorMessage(error),
            status: "error"
          });
        }
      }
    }

    loadGithubAnalysis();

    return () => {
      ignore = true;
    };
  }, [initialGithubAnalysisId]);

  if (state.status === "loading") {
    return <GithubAnalysisStatePanel message="GitHub 분석 결과를 불러오는 중입니다." />;
  }

  if (state.status === "empty") {
    return <GithubAnalysisStatePanel message={state.message} />;
  }

  if (state.status === "error") {
    return <GithubAnalysisStatePanel message={state.message} tone="danger" />;
  }

  const { analysis, diagnosisId } = state;

  return (
    <section className="github-analysis-page" aria-labelledby="analysis-title">
      <div className="screen-hero">
        <p className="eyebrow">v{analysis.version} GitHub 분석</p>
        <div className="screen-heading">
          <h1 id="analysis-title">GitHub 분석 결과 / 사용자 보정</h1>
          <p>정적 분석 결과와 근거를 확인하고 최종 기술 프로필을 검수합니다.</p>
        </div>
        <div className="action-row" aria-label="GitHub 분석 관련 화면 이동">
          {diagnosisId ? (
            <Link
              className="action-link primary"
              href={`/diagnoses/${diagnosisId}`}
            >
              진단 결과 보기
            </Link>
          ) : null}
          <Link className="action-link" href="/github">
            저장소 선택
          </Link>
        </div>
        <dl className="github-analysis-summary-list" aria-label="분석 요약">
          <div>
            <dt>분석 ID</dt>
            <dd>{analysis.githubAnalysisId}</dd>
          </div>
          <div>
            <dt>활성 저장소</dt>
            <dd>{analysis.staticSignals.activeRepos}개</dd>
          </div>
          <div>
            <dt>사용자 보정</dt>
            <dd>{analysis.userCorrections.length}개</dd>
          </div>
          <div>
            <dt>생성일</dt>
            <dd>{formatDateTime(analysis.createdAt)}</dd>
          </div>
        </dl>
      </div>

      <div className="github-analysis-grid">
        <StaticSignalsPanel analysis={analysis} />
        <FinalTechProfilePanel analysis={analysis} />
      </div>

      <section className="panel github-analysis-section">
        <h2>저장소 요약</h2>
        {analysis.repoSummaries.length === 0 ? (
          <p className="github-analysis-empty-text">표시할 저장소 요약이 없습니다.</p>
        ) : (
          <div className="github-repo-summary-list">
            {analysis.repoSummaries.map((repo) => (
              <article className="github-repo-summary-card" key={repo.repoId}>
                <p>{repo.repoName}</p>
                <h3>{repo.summary}</h3>
                <ul>
                  {repo.highlights.map((highlight) => (
                    <li key={highlight}>{highlight}</li>
                  ))}
                </ul>
              </article>
            ))}
          </div>
        )}
      </section>

      <div className="github-analysis-grid">
        <TechTagPanel analysis={analysis} />
        <DepthEstimatePanel depthEstimates={analysis.depthEstimates} />
      </div>

      <section className="panel github-analysis-section">
        <h2>근거</h2>
        {analysis.evidences.length === 0 ? (
          <p className="github-analysis-empty-text">표시할 근거가 없습니다.</p>
        ) : (
          <ul className="github-evidence-list">
            {analysis.evidences.map((evidence) => (
              <li
                key={`${evidence.repoName}-${evidence.type}-${evidence.source}`}
              >
                <span>{githubEvidenceTypeLabels[evidence.type]}</span>
                <strong>{evidence.repoName}</strong>
                <code>{evidence.source}</code>
                <p>{evidence.summary}</p>
              </li>
            ))}
          </ul>
        )}
      </section>

      <UserCorrectionPreview corrections={analysis.userCorrections} />
    </section>
  );
}

function GithubAnalysisStatePanel({
  message,
  tone = "neutral"
}: {
  message: string;
  tone?: "danger" | "neutral";
}) {
  return (
    <section className="panel github-analysis-state-panel" data-tone={tone}>
      <p>{message}</p>
    </section>
  );
}

function StaticSignalsPanel({ analysis }: { analysis: GithubAnalysis }) {
  const { staticSignals } = analysis;

  return (
    <section className="panel github-analysis-section">
      <h2>정적 신호</h2>
      <dl className="github-analysis-metric-list">
        <div>
          <dt>커밋 빈도</dt>
          <dd>{staticSignals.commitFrequency ?? "미확인"}</dd>
        </div>
        <div>
          <dt>기여 패턴</dt>
          <dd>{staticSignals.contributionPattern ?? "미확인"}</dd>
        </div>
      </dl>
      <TagList
        items={staticSignals.primaryLanguages.map(
          (language) => `${language.lang} ${formatRatio(language.ratio)}`
        )}
        label="주요 언어"
      />
    </section>
  );
}

function FinalTechProfilePanel({ analysis }: { analysis: GithubAnalysis }) {
  return (
    <section className="panel github-analysis-section">
      <h2>최종 기술 프로필</h2>
      <TagList
        items={analysis.finalTechProfile.confirmedSkills}
        label="확정 기술"
      />
      <TagList items={analysis.finalTechProfile.focusAreas} label="집중 영역" />
    </section>
  );
}

function TechTagPanel({ analysis }: { analysis: GithubAnalysis }) {
  return (
    <section className="panel github-analysis-section">
      <h2>기술 태그</h2>
      {analysis.techTags.length === 0 ? (
        <p className="github-analysis-empty-text">표시할 기술 태그가 없습니다.</p>
      ) : (
        <ul className="github-tech-tag-list">
          {analysis.techTags.map((tag) => (
            <li key={`${tag.skillName}-${tag.tagReason}`}>
              <strong>{tag.skillName}</strong>
              <p>{tag.tagReason}</p>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function DepthEstimatePanel({
  depthEstimates
}: {
  depthEstimates: DepthEstimate[];
}) {
  return (
    <section className="panel github-analysis-section">
      <h2>숙련도 추정</h2>
      {depthEstimates.length === 0 ? (
        <p className="github-analysis-empty-text">표시할 숙련도 추정이 없습니다.</p>
      ) : (
        <ul className="github-depth-list">
          {depthEstimates.map((estimate) => (
            <li key={`${estimate.skillName}-${estimate.level}`}>
              <div>
                <strong>{estimate.skillName}</strong>
                <DepthBadge estimate={estimate} />
              </div>
              <p>{estimate.reason}</p>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function UserCorrectionPreview({
  corrections
}: {
  corrections: GithubUserCorrection[];
}) {
  return (
    <section className="panel github-analysis-section">
      <h2>사용자 보정</h2>
      {corrections.length === 0 ? (
        <p className="github-analysis-empty-text">저장된 사용자 보정이 없습니다.</p>
      ) : (
        <ul className="github-tech-tag-list">
          {corrections.map((correction) => (
            <li key={`${correction.skillName}-${correction.correction}`}>
              <strong>{correction.skillName}</strong>
              <p>{correction.correction}</p>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function DepthBadge({ estimate }: { estimate: DepthEstimate }) {
  return (
    <span
      className="github-depth-badge"
      data-depth={githubDepthLevelClassNames[estimate.level]}
    >
      {githubDepthLevelLabels[estimate.level]}
    </span>
  );
}

function TagList({ items, label }: { items: string[]; label: string }) {
  if (items.length === 0) {
    return (
      <div className="github-tag-group">
        <p>{label}</p>
        <span>없음</span>
      </div>
    );
  }

  return (
    <div className="github-tag-group">
      <p>{label}</p>
      <div>
        {items.map((item) => (
          <span key={item}>{item}</span>
        ))}
      </div>
    </div>
  );
}

function normalizeOptionalId(value?: string | null) {
  if (!value) {
    return null;
  }

  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : null;
}

function getErrorMessage(error: unknown) {
  if (error instanceof ApiError) {
    return error.message;
  }

  return "GitHub 분석 결과를 불러오지 못했습니다.";
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    dateStyle: "medium",
    timeStyle: "short"
  }).format(new Date(value));
}

function formatRatio(value: number) {
  const ratio = value <= 1 ? value * 100 : value;

  return `${ratio.toLocaleString("ko-KR", {
    maximumFractionDigits: 1
  })}%`;
}
