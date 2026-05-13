"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";

import { AuthRequiredPanel } from "@/components/auth-required-panel";
import { ApiError, apiClient } from "@/lib/api";
import { isUnauthorizedError } from "@/lib/auth";
import { getMyProfile } from "@/features/profile/api";
import { getDashboard } from "@/features/dashboard/api";
import { getRoadmapConstraints } from "@/features/roadmap/api";
import { StatePanel } from "@/components/state-panel";

type CreateState =
  | { status: "idle" }
  | { status: "submitting" }
  | { status: "auth-required" }
  | { status: "error"; message: string };

function getErrorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  return "오류가 발생했습니다. 다시 시도해주세요.";
}

const FALLBACK_MAX_WEEKS = 8;

function getTomorrowDateValue(baseTimeMs: number = Date.now()) {
  return new Date(baseTimeMs + 86400000).toISOString().split("T")[0];
}

function getMaxDateValue(maxWeeks: number, baseTimeMs: number = Date.now()) {
  return new Date(baseTimeMs + maxWeeks * 7 * 86400000)
    .toISOString()
    .split("T")[0];
}

type Props = {
  initialDiagnosisId?: string;
};

export function RoadmapCreateView({ initialDiagnosisId }: Props) {
  const router = useRouter();
  const [state, setState] = useState<CreateState>({ status: "idle" });
  const [diagnosisId, setDiagnosisId] = useState(initialDiagnosisId ?? "");
  const [diagnosisSummary, setDiagnosisSummary] = useState<string | null>(null);
  const [weeklyStudyHours, setWeeklyStudyHours] = useState("");
  const [targetDate, setTargetDate] = useState("");
  const [maxWeeks, setMaxWeeks] = useState<number>(FALLBACK_MAX_WEEKS);
  const [baseTimeMs] = useState(() => Date.now());
  const loaded = useRef(false);
  const targetDateInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (loaded.current) return;
    loaded.current = true;
    targetDateInputRef.current?.setAttribute("min", getTomorrowDateValue(baseTimeMs));

    getRoadmapConstraints()
      .then((constraints) => setMaxWeeks(constraints.maxWeeks))
      .catch(() => {
        // fallback 값을 그대로 사용
      });

    // 프로필에서 학습 시간/목표일 기본값 로드
    getMyProfile()
      .then((profile) => {
        if (profile.weeklyStudyHours)
          setWeeklyStudyHours(String(profile.weeklyStudyHours));
        if (profile.targetDate) setTargetDate(profile.targetDate);
      })
      .catch((error) => {
        if (isUnauthorizedError(error)) {
          setState({ status: "auth-required" });
        }
      });

    // initialDiagnosisId가 없으면 대시보드에서 최신 진단 자동 로드
    if (!initialDiagnosisId) {
      getDashboard()
        .then((dashboard) => {
          if (dashboard.diagnosis) {
            setDiagnosisId(String(dashboard.diagnosis.diagnosisId));
            setDiagnosisSummary(dashboard.diagnosis.summary);
          }
        })
        .catch((error) => {
          if (isUnauthorizedError(error)) {
            setState({ status: "auth-required" });
          }
        });
    } else {
      // initialDiagnosisId가 있으면 대시보드에서 summary만 가져옴
      getDashboard()
        .then((dashboard) => {
          if (
            dashboard.diagnosis &&
            String(dashboard.diagnosis.diagnosisId) === initialDiagnosisId
          ) {
            setDiagnosisSummary(dashboard.diagnosis.summary);
          }
        })
        .catch((error) => {
          if (isUnauthorizedError(error)) {
            setState({ status: "auth-required" });
          }
        });
    }
  }, [baseTimeMs, initialDiagnosisId]);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    const hours = Number(weeklyStudyHours);
    if (!diagnosisId.trim()) {
      setState({ status: "error", message: "진단 ID를 입력해주세요." });
      return;
    }
    if (!hours || hours < 1 || hours > 40) {
      setState({
        status: "error",
        message: "주당 학습 시간은 1~40 사이로 입력해주세요."
      });
      return;
    }
    if (!targetDate) {
      setState({ status: "error", message: "목표 날짜를 입력해주세요." });
      return;
    }
    if (new Date(targetDate) <= new Date()) {
      setState({ status: "error", message: "목표 날짜는 오늘 이후여야 합니다." });
      return;
    }
    if (new Date(targetDate) > new Date(getMaxDateValue(maxWeeks))) {
      setState({
        status: "error",
        message: `목표 날짜는 최대 ${maxWeeks}주 이내여야 합니다.`
      });
      return;
    }

    setState({ status: "submitting" });
    try {
      const result = await apiClient.post<{ roadmapId: string }>("/api/roadmaps", {
        diagnosisId: Number(diagnosisId),
        weeklyStudyHours: hours,
        targetDate
      });
      router.push(`/roadmaps/${result.roadmapId}`);
    } catch (err) {
      if (isUnauthorizedError(err)) {
        setState({ status: "auth-required" });
        return;
      }
      setState({ status: "error", message: getErrorMessage(err) });
    }
  }

  const isSubmitting = state.status === "submitting";

  if (state.status === "auth-required") {
    return (
      <AuthRequiredPanel
        className="roadmap-state-panel"
        redirectPath={
          initialDiagnosisId
            ? `/roadmaps/new?diagnosisId=${encodeURIComponent(initialDiagnosisId)}`
            : "/roadmaps/new"
        }
      />
    );
  }

  if (isSubmitting) {
    return (
      <div className="roadmap-submitting-container">
        <div className="roadmap-submitting-content">
          <div className="roadmap-spinner" />
          <h2>학습 로드맵 생성 중</h2>
          <p>AI가 진단 결과를 바탕으로 주별 학습 계획을 구성하고 있습니다.</p>
          <p className="roadmap-submitting-note">이 과정은 약 30초~1분이 소요될 수 있습니다.</p>
        </div>
      </div>
    );
  }

  // 목표 날짜 → 주 수 계산 (사용자 미리보기용)
  const weeksUntilTarget = targetDate
    ? Math.max(
        1,
        Math.ceil(
          (new Date(targetDate).getTime() - baseTimeMs) / (7 * 86400000)
        )
      )
    : null;

  return (
    <div className="screen-shell">
      <section className="roadmap-create-hero">
        <div className="roadmap-create-hero-container">
          <div className="roadmap-create-hero-visual">
            <svg width="120" height="120" viewBox="0 0 120 120" fill="none" xmlns="http://www.w3.org/2000/svg">
              <circle cx="60" cy="60" r="58" fill="url(#roadmapGradient)" opacity="0.1" />
              <defs>
                <linearGradient id="roadmapGradient" x1="0" y1="0" x2="120" y2="120">
                  <stop offset="0%" stopColor="#10b981" />
                  <stop offset="100%" stopColor="#047857" />
                </linearGradient>
              </defs>
              <g transform="translate(30 30)" stroke="#047857" strokeWidth="1.6" strokeLinecap="round" fill="none">
                {/* 굽이치는 길 */}
                <path d="M5 50 Q15 35 25 40 T45 30 T55 15" opacity="0.7" />
                {/* 지점 마커 */}
                <circle cx="5" cy="50" r="3.5" fill="#10b981" stroke="none" />
                <circle cx="25" cy="40" r="3" fill="#10b981" stroke="none" opacity="0.85" />
                <circle cx="45" cy="30" r="3" fill="#10b981" stroke="none" opacity="0.85" />
                <circle cx="55" cy="15" r="4.5" fill="#047857" stroke="none" />
                {/* 도착 깃발 */}
                <line x1="55" y1="15" x2="55" y2="0" stroke="#047857" strokeWidth="1.6" />
                <path d="M55 0 L62 4 L55 8 Z" fill="#10b981" stroke="none" />
              </g>
            </svg>
          </div>

          <div className="roadmap-create-hero-content">
            <h1 className="roadmap-create-hero-title">학습 로드맵 생성</h1>
            <p className="roadmap-create-hero-subtitle">
              진단 결과와 학습 시간을 바탕으로 AI가 주별 맞춤 학습 계획을 만들어 줍니다.
            </p>

            <div className="roadmap-create-benefits">
              <div className="benefit-item">
                <span className="benefit-icon">🎯</span>
                <span className="benefit-text">주별 학습 목표</span>
              </div>
              <div className="benefit-item">
                <span className="benefit-icon">📚</span>
                <span className="benefit-text">맞춤형 콘텐츠 추천</span>
              </div>
              <div className="benefit-item">
                <span className="benefit-icon">⏱️</span>
                <span className="benefit-text">학습 시간 기반 계획</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      {state.status === "error" && (
        <StatePanel message={state.message} tone="danger" />
      )}

      <form className="roadmap-create-form" onSubmit={handleSubmit}>
        {/* 진단 컨텍스트 카드 */}
        <div className="roadmap-context-card">
          <div className="roadmap-context-card-label">기반이 되는 진단</div>
          {diagnosisId ? (
            <>
              <div className="roadmap-context-card-value">
                진단 #{diagnosisId}
              </div>
              {diagnosisSummary && (
                <p className="roadmap-context-card-summary">{diagnosisSummary}</p>
              )}
              <Link href="/diagnoses" className="roadmap-context-card-link">
                다른 진단 선택 →
              </Link>
            </>
          ) : (
            <>
              <div className="roadmap-context-card-empty">
                기반이 될 진단 결과가 없습니다.
              </div>
              <Link href="/diagnoses/new" className="roadmap-context-card-link">
                먼저 진단 만들기 →
              </Link>
            </>
          )}
        </div>

        {!diagnosisId && (
          <label className="roadmap-create-field">
            <span className="roadmap-create-field-label">진단 ID (직접 입력)</span>
            <input
              type="text"
              value={diagnosisId}
              onChange={(e) => setDiagnosisId(e.target.value)}
              placeholder="진단 결과 ID를 입력하세요"
              disabled={isSubmitting}
              className="roadmap-create-input"
            />
          </label>
        )}

        <div className="roadmap-create-field-grid">
          <label className="roadmap-create-field">
            <span className="roadmap-create-field-label">주당 학습 시간</span>
            <div className="roadmap-create-input-with-suffix">
              <input
                type="number"
                min={1}
                max={40}
                value={weeklyStudyHours}
                onChange={(e) => setWeeklyStudyHours(e.target.value)}
                placeholder="10"
                disabled={isSubmitting}
                required
                className="roadmap-create-input"
              />
              <span className="roadmap-create-input-suffix">시간 / 주</span>
            </div>
            <span className="roadmap-create-field-hint">1~40 시간 사이로 입력</span>
          </label>

          <label className="roadmap-create-field">
            <span className="roadmap-create-field-label">목표 완료 날짜</span>
            <input
              type="date"
              ref={targetDateInputRef}
              max={getMaxDateValue(maxWeeks, baseTimeMs)}
              value={targetDate}
              onChange={(e) => setTargetDate(e.target.value)}
              disabled={isSubmitting}
              required
              className="roadmap-create-input"
            />
            <span className="roadmap-create-field-hint">
              최대 {maxWeeks}주 이내
              {weeksUntilTarget !== null && ` · 약 ${weeksUntilTarget}주 후 완료`}
            </span>
          </label>
        </div>

        {weeklyStudyHours && weeksUntilTarget && (
          <div className="roadmap-create-preview">
            <span className="roadmap-create-preview-icon">📋</span>
            <span>
              총 약 <strong>{weeksUntilTarget * Number(weeklyStudyHours)}시간</strong>
              {" "}분량의 학습 계획이 생성됩니다 ({weeksUntilTarget}주 × 주 {weeklyStudyHours}시간)
            </span>
          </div>
        )}

        <div className="action-row" aria-label="로드맵 생성">
          <button
            type="submit"
            disabled={isSubmitting || !diagnosisId}
            className="roadmap-create-cta-button"
          >
            <span className="roadmap-create-cta-icon">✨</span>
            로드맵 생성 시작
          </button>
          <Link href="/diagnoses" className="action-link">
            돌아가기
          </Link>
        </div>
      </form>
    </div>
  );
}
