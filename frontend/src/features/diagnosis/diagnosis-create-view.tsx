"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { AuthRequiredPanel } from "@/components/auth-required-panel";
import { ApiError } from "@/lib/api";
import { isUnauthorizedError } from "@/lib/auth";
import { createDiagnosis } from "@/features/diagnosis/api";
import { getMyProfile } from "@/features/profile/api";
import { listGithubAnalyses } from "@/features/github-analysis/api";
import type { GithubAnalysisSummary } from "@/features/github-analysis/types";
import type { ProfileDetail } from "@/features/profile/types";
import { currentLevelLabels } from "@/features/diagnosis/labels";
import { StatePanel } from "@/components/state-panel";

type CreateState =
  | { status: "loading" }
  | { status: "auth-required" }
  | {
      status: "ready";
      profile: ProfileDetail;
      analyses: GithubAnalysisSummary[];
    }
  | { status: "submitting" }
  | { status: "error"; message: string };

function getErrorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  return "오류가 발생했습니다. 다시 시도해주세요.";
}

type Props = {
  githubAnalysisId: string;
};

export function DiagnosisCreateView({ githubAnalysisId }: Props) {
  const router = useRouter();
  const [state, setState] = useState<CreateState>({ status: "loading" });
  const [selectedAnalysisId, setSelectedAnalysisId] = useState(githubAnalysisId);
  const loaded = useRef(false);

  useEffect(() => {
    if (loaded.current) return;
    loaded.current = true;
    Promise.all([getMyProfile(), listGithubAnalyses()])
      .then(([profile, analyses]) => {
        // URL의 githubAnalysisId가 본인 목록에 없으면 (다른 사용자 ID이거나
        // 삭제된 ID) 첫 번째 본인 분석으로 보정. 백엔드도 userId 필터링하므로
        // 다중 방어.
        const valid = analyses.some(
          (a) => a.githubAnalysisId === githubAnalysisId
        );
        if (!valid && analyses.length > 0) {
          setSelectedAnalysisId(analyses[0].githubAnalysisId);
        }
        setState({ status: "ready", profile, analyses });
      })
      .catch((err) => {
        if (isUnauthorizedError(err)) {
          setState({ status: "auth-required" });
          return;
        }
        setState({ status: "error", message: getErrorMessage(err) });
      });
  }, []);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (state.status !== "ready") return;
    if (!selectedAnalysisId) return;
    setState({ status: "submitting" });
    try {
      const diagnosis = await createDiagnosis({
        profileId: state.profile.profileId,
        githubAnalysisId: selectedAnalysisId
      });
      router.push(`/diagnoses/${diagnosis.diagnosisId}`);
    } catch (err) {
      if (isUnauthorizedError(err)) {
        setState({ status: "auth-required" });
        return;
      }
      setState({ status: "error", message: getErrorMessage(err) });
    }
  }

  if (state.status === "loading") {
    return <StatePanel message="프로필을 불러오는 중..." />;
  }

  if (state.status === "submitting") {
    return (
      <div className="diagnosis-submitting-container">
        <div className="diagnosis-submitting-content">
          <div className="diagnosis-spinner"></div>
          <h2>역량 진단 생성 중</h2>
          <p>AI가 GitHub 분석을 바탕으로 맞춤형 진단을 생성하고 있습니다.</p>
          <p className="diagnosis-submitting-note">이 과정은 약 1-2분이 소요될 수 있습니다.</p>
        </div>
      </div>
    );
  }

  if (state.status === "auth-required") {
    return (
      <AuthRequiredPanel
        redirectPath={`/diagnoses/new?githubAnalysisId=${encodeURIComponent(githubAnalysisId)}`}
      />
    );
  }

  if (state.status === "error") {
    return (
      <div className="screen-shell">
        <StatePanel message={state.message} tone="danger" />
        <div className="action-row">
          <a href="/github/analysis" className="action-link">
            분석 결과로 돌아가기
          </a>
        </div>
      </div>
    );
  }

  return (
    <div className="screen-shell">
      <section className="diagnosis-hero">
        <div className="diagnosis-hero-container">
          <div className="diagnosis-hero-visual">
            <svg width="120" height="120" viewBox="0 0 120 120" fill="none" xmlns="http://www.w3.org/2000/svg">
              <circle cx="60" cy="60" r="58" fill="url(#diagnosisGradient)" opacity="0.1" />
              <defs>
                <linearGradient id="diagnosisGradient" x1="0" y1="0" x2="120" y2="120">
                  <stop offset="0%" stopColor="#7c3aed" />
                  <stop offset="100%" stopColor="#5b21b6" />
                </linearGradient>
              </defs>
              <g transform="translate(30 30)">
                <circle cx="15" cy="15" r="12" fill="none" stroke="#7c3aed" strokeWidth="1.5" opacity="0.4" />
                <circle cx="15" cy="15" r="8" fill="none" stroke="#7c3aed" strokeWidth="1.5" opacity="0.6" />
                <circle cx="15" cy="15" r="4" fill="#7c3aed" opacity="0.8" />
                <path d="M15 5 L15 25" stroke="#7c3aed" strokeWidth="1.5" opacity="0.5" />
                <path d="M5 15 L25 15" stroke="#7c3aed" strokeWidth="1.5" opacity="0.5" />
              </g>
            </svg>
          </div>

          <div className="diagnosis-hero-content">
            <h1 className="diagnosis-hero-title">역량 진단 생성</h1>
            <p className="diagnosis-hero-subtitle">
              GitHub 분석 결과를 바탕으로 당신의 개발 역량을 AI가 종합 진단합니다.
            </p>

            <div className="diagnosis-info-grid">
              <div className="diagnosis-info-card">
                <div className="diagnosis-info-label">분석 데이터</div>
                {state.analyses.length === 0 ? (
                  <div className="diagnosis-info-value">선택 가능한 분석이 없습니다</div>
                ) : (
                  <select
                    className="diagnosis-analysis-select"
                    value={selectedAnalysisId}
                    onChange={(e) => setSelectedAnalysisId(e.target.value)}
                    aria-label="진단에 사용할 GitHub 분석 선택"
                  >
                    {state.analyses.map((analysis) => (
                      <option
                        key={analysis.githubAnalysisId}
                        value={analysis.githubAnalysisId}
                      >
                        GitHub 분석 #{analysis.githubAnalysisId} (v{analysis.version}) ·{" "}
                        {formatShortDate(analysis.createdAt)}
                      </option>
                    ))}
                  </select>
                )}
              </div>
              <div className="diagnosis-info-card">
                <div className="diagnosis-info-label">프로필</div>
                <div className="diagnosis-info-value">
                  {state.profile.targetRole}
                </div>
                <div className="diagnosis-info-meta">
                  현재 레벨: {currentLevelLabels[state.profile.currentLevel] ?? state.profile.currentLevel}
                  {state.profile.skills.length > 0 && (
                    <> · 등록 기술 {state.profile.skills.length}개</>
                  )}
                </div>
              </div>
            </div>

            <div className="diagnosis-benefits">
              <div className="benefit-item">
                <span className="benefit-icon">📊</span>
                <span className="benefit-text">코딩 패턴 분석</span>
              </div>
              <div className="benefit-item">
                <span className="benefit-icon">🎯</span>
                <span className="benefit-text">역량 평가</span>
              </div>
              <div className="benefit-item">
                <span className="benefit-icon">💡</span>
                <span className="benefit-text">맞춤형 피드백</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      <div className="action-row" aria-label="역량 진단">
        <form onSubmit={handleSubmit} className="diagnosis-form">
          <button
            type="submit"
            className="diagnosis-cta-button"
            disabled={!selectedAnalysisId || state.analyses.length === 0}
          >
            <span className="diagnosis-button-icon">✨</span>
            진단 생성 시작
          </button>
        </form>
        <a href="/github/analysis" className="action-link">
          돌아가기
        </a>
      </div>
    </div>
  );
}

function formatShortDate(iso: string) {
  try {
    return new Intl.DateTimeFormat("ko-KR", {
      month: "2-digit",
      day: "2-digit"
    }).format(new Date(iso));
  } catch {
    return iso;
  }
}
