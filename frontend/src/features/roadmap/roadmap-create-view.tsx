"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";

import { AuthRequiredPanel } from "@/components/auth-required-panel";
import { ApiError, apiClient } from "@/lib/api";
import { isUnauthorizedError } from "@/lib/auth";
import { getMyProfile } from "@/features/profile/api";
import { getDashboard } from "@/features/dashboard/api";
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

const MAX_WEEKS = 8;

function getTomorrowDateValue() {
  return new Date(Date.now() + 86400000).toISOString().split("T")[0];
}

function getMaxDateValue() {
  return new Date(Date.now() + MAX_WEEKS * 7 * 86400000)
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
  const loaded = useRef(false);
  const targetDateInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (loaded.current) return;
    loaded.current = true;
    targetDateInputRef.current?.setAttribute("min", getTomorrowDateValue());

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
  }, [initialDiagnosisId]);

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
    if (new Date(targetDate) > new Date(getMaxDateValue())) {
      setState({
        status: "error",
        message: `목표 날짜는 최대 ${MAX_WEEKS}주 이내여야 합니다.`
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

  return (
    <section className="screen-shell">
      <div className="screen-hero">
        <p className="eyebrow">v1 필수</p>
        <div className="screen-heading">
          <h1>학습 로드맵 생성</h1>
          <p>진단 결과를 기반으로 맞춤 학습 로드맵을 생성합니다.</p>
        </div>
      </div>

      {state.status === "error" && (
        <StatePanel message={state.message} tone="danger" />
      )}

      <form className="panel profile-form-section" onSubmit={handleSubmit}>
        <div className="profile-section-heading">
          <h2>진단 결과</h2>
          {diagnosisId ? (
            <p className="diagnosis-auto-filled">
              진단 ID <strong>{diagnosisId}</strong>
              {diagnosisSummary ? ` — ${diagnosisSummary}` : ""}
              {" "}
              <Link href="/diagnoses" style={{ fontSize: "0.875rem" }}>
                변경
              </Link>
            </p>
          ) : (
            <p>
              진단 결과가 없습니다.{" "}
              <Link href="/diagnoses/new">진단 생성하기</Link>
            </p>
          )}
        </div>

        {!diagnosisId && (
          <label>
            <span>진단 ID (직접 입력)</span>
            <input
              type="text"
              value={diagnosisId}
              onChange={(e) => setDiagnosisId(e.target.value)}
              placeholder="진단 결과 ID를 입력하세요"
              disabled={isSubmitting}
            />
          </label>
        )}

        <label>
          <span>주당 학습 시간 (1~40)</span>
          <input
            type="number"
            min={1}
            max={40}
            value={weeklyStudyHours}
            onChange={(e) => setWeeklyStudyHours(e.target.value)}
            placeholder="예: 10"
            disabled={isSubmitting}
            required
          />
        </label>

        <label>
          <span>목표 날짜 (최대 {MAX_WEEKS}주)</span>
          <input
            type="date"
            ref={targetDateInputRef}
            max={getMaxDateValue()}
            value={targetDate}
            onChange={(e) => setTargetDate(e.target.value)}
            disabled={isSubmitting}
            required
          />
        </label>

        <div className="profile-form-actions">
          <div />
          <button
            type="submit"
            disabled={isSubmitting || !diagnosisId}
          >
            {isSubmitting ? "생성 중..." : "로드맵 생성"}
          </button>
        </div>
      </form>
    </section>
  );
}
