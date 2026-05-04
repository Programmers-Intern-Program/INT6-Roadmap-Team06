"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState, type FormEvent } from "react";

import { StatePanel } from "@/components/state-panel";
import { TagList } from "@/components/tag-list";
import { getDashboard } from "@/features/dashboard/api";
import { createDiagnosis } from "@/features/diagnosis/api";
import {
  getGithubAnalysis,
  saveGithubAnalysisCorrections
} from "@/features/github-analysis/api";
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
      profileId: string | null;
    };

export function GithubAnalysisView({
  initialGithubAnalysisId
}: GithubAnalysisViewProps) {
  const router = useRouter();
  const [state, setState] = useState<GithubAnalysisState>({
    status: "loading"
  });
  const [isSaving, setIsSaving] = useState(false);
  const [isCreatingDiagnosis, setIsCreatingDiagnosis] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [saveMessage, setSaveMessage] = useState<string | null>(null);
  const [diagnosisError, setDiagnosisError] = useState<string | null>(null);

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
            profileId: dashboard?.profile?.profileId ?? null,
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

  async function handleCorrectionSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (state.status !== "success") {
      return;
    }

    const formData = new FormData(event.currentTarget);
    const correctionText = getStringFormValue(formData, "userCorrections");
    const confirmedSkillsText = getStringFormValue(formData, "confirmedSkills");
    const focusAreasText = getStringFormValue(formData, "focusAreas");
    const parsedCorrections = parseCorrectionLines(correctionText);

    if (parsedCorrections.error) {
      setSaveError(parsedCorrections.error);
      setSaveMessage(null);
      return;
    }

    setIsSaving(true);
    setSaveError(null);
    setSaveMessage(null);

    try {
      const saved = await saveGithubAnalysisCorrections(
        state.analysis.githubAnalysisId,
        {
          finalTechProfile: {
            confirmedSkills: parseLineList(confirmedSkillsText),
            focusAreas: parseLineList(focusAreasText)
          },
          userCorrections: parsedCorrections.items
        }
      );

      setState((current) => {
        if (current.status !== "success") {
          return current;
        }

        return {
          ...current,
          analysis: {
            ...current.analysis,
            finalTechProfile: saved.finalTechProfile,
            userCorrections: parsedCorrections.items
          }
        };
      });
      setSaveMessage(`저장 완료 ${formatDateTime(saved.savedAt)}`);
    } catch (error) {
      setSaveError(getSaveErrorMessage(error));
    } finally {
      setIsSaving(false);
    }
  }

  async function handleDiagnosisCreate() {
    if (state.status !== "success" || !state.profileId) {
      setDiagnosisError("진단 생성 전에 프로필을 먼저 저장해 주세요.");
      return;
    }

    setIsCreatingDiagnosis(true);
    setDiagnosisError(null);

    try {
      const diagnosis = await createDiagnosis({
        githubAnalysisId: state.analysis.githubAnalysisId,
        profileId: state.profileId
      });

      router.push(`/diagnoses/${diagnosis.diagnosisId}`);
    } catch (error) {
      setDiagnosisError(getDiagnosisErrorMessage(error));
    } finally {
      setIsCreatingDiagnosis(false);
    }
  }

  if (state.status === "loading") {
    return (
      <StatePanel
        className="github-analysis-state-panel"
        message="GitHub 분석 결과를 불러오는 중입니다."
      />
    );
  }

  if (state.status === "empty") {
    return (
      <StatePanel
        className="github-analysis-state-panel"
        message={state.message}
      />
    );
  }

  if (state.status === "error") {
    return (
      <StatePanel
        className="github-analysis-state-panel"
        message={state.message}
        tone="danger"
      />
    );
  }

  const { analysis, diagnosisId, profileId } = state;

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
          ) : (
            <button
              className="action-link primary"
              disabled={isCreatingDiagnosis || !profileId}
              onClick={handleDiagnosisCreate}
              type="button"
            >
              {isCreatingDiagnosis ? "진단 생성 중" : "진단 생성"}
            </button>
          )}
          <Link className="action-link" href="/github">
            저장소 선택
          </Link>
        </div>
        {diagnosisError ? (
          <p className="github-analysis-action-error">{diagnosisError}</p>
        ) : null}
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

      <GithubCorrectionForm
        analysis={analysis}
        isSaving={isSaving}
        onSubmit={handleCorrectionSubmit}
        saveError={saveError}
        saveMessage={saveMessage}
      />
      <UserCorrectionPreview corrections={analysis.userCorrections} />
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
        emptyLabel="없음"
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
        emptyLabel="없음"
        items={analysis.finalTechProfile.confirmedSkills}
        label="확정 기술"
      />
      <TagList
        emptyLabel="없음"
        items={analysis.finalTechProfile.focusAreas}
        label="집중 영역"
      />
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

function GithubCorrectionForm({
  analysis,
  isSaving,
  onSubmit,
  saveError,
  saveMessage
}: {
  analysis: GithubAnalysis;
  isSaving: boolean;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  saveError: string | null;
  saveMessage: string | null;
}) {
  return (
    <form
      className="panel github-correction-form"
      key={getCorrectionFormKey(analysis)}
      onSubmit={onSubmit}
    >
      <div className="github-analysis-section-heading">
        <h2>보정 저장</h2>
        <p>사용자 판단을 반영해 최종 기술 프로필을 저장합니다.</p>
      </div>

      <label>
        <span>사용자 보정</span>
        <textarea
          defaultValue={formatCorrections(analysis.userCorrections)}
          name="userCorrections"
          placeholder="Redis|학습만 해봄"
          rows={5}
        />
      </label>

      <div className="github-correction-fields">
        <label>
          <span>확정 기술</span>
          <textarea
            defaultValue={analysis.finalTechProfile.confirmedSkills.join("\n")}
            name="confirmedSkills"
            placeholder="Java&#10;Spring Boot"
            rows={5}
          />
        </label>
        <label>
          <span>집중 영역</span>
          <textarea
            defaultValue={analysis.finalTechProfile.focusAreas.join("\n")}
            name="focusAreas"
            placeholder="백엔드&#10;성능 개선"
            rows={5}
          />
        </label>
      </div>

      <div className="github-correction-actions">
        <div>
          {saveError ? <p className="github-correction-error">{saveError}</p> : null}
          {saveMessage ? (
            <p className="github-correction-success">{saveMessage}</p>
          ) : null}
        </div>
        <button disabled={isSaving} type="submit">
          {isSaving ? "저장 중" : "보정 저장"}
        </button>
      </div>
    </form>
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

function normalizeOptionalId(value?: string | null) {
  if (!value) {
    return null;
  }

  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : null;
}

function parseCorrectionLines(value: string): {
  error: string | null;
  items: GithubUserCorrection[];
} {
  const items: GithubUserCorrection[] = [];
  const lines = value.split(/\r?\n/);

  for (const [index, line] of lines.entries()) {
    const trimmed = line.trim();

    if (trimmed.length === 0) {
      continue;
    }

    const [skillName, ...correctionParts] = trimmed.split("|");
    const correction = correctionParts.join("|").trim();

    if (!skillName.trim() || !correction) {
      return {
        error: `${index + 1}번째 보정은 기술명|보정내용 형식으로 입력해 주세요.`,
        items: []
      };
    }

    items.push({
      correction,
      skillName: skillName.trim()
    });
  }

  return { error: null, items };
}

function parseLineList(value: string) {
  return value
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean);
}

function getStringFormValue(formData: FormData, key: string) {
  const value = formData.get(key);

  return typeof value === "string" ? value : "";
}

function formatCorrections(corrections: GithubUserCorrection[]) {
  return corrections
    .map((correction) => `${correction.skillName}|${correction.correction}`)
    .join("\n");
}

function getCorrectionFormKey(analysis: GithubAnalysis) {
  return [
    analysis.githubAnalysisId,
    analysis.userCorrections
      .map((correction) => `${correction.skillName}:${correction.correction}`)
      .join(","),
    analysis.finalTechProfile.confirmedSkills.join(","),
    analysis.finalTechProfile.focusAreas.join(",")
  ].join("|");
}

function getErrorMessage(error: unknown) {
  if (error instanceof ApiError) {
    return error.message;
  }

  return "GitHub 분석 결과를 불러오지 못했습니다.";
}

function getSaveErrorMessage(error: unknown) {
  if (error instanceof ApiError) {
    return error.message;
  }

  return "GitHub 분석 보정을 저장하지 못했습니다.";
}

function getDiagnosisErrorMessage(error: unknown) {
  if (error instanceof ApiError) {
    return error.message;
  }

  return "진단을 생성하지 못했습니다.";
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
