"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { ApiError } from "@/lib/api";
import { getMyProfile } from "@/features/profile/api";
import { apiClient } from "@/lib/api";
import { StatePanel } from "@/components/state-panel";

type CreateState =
  | { status: "idle" }
  | { status: "submitting" }
  | { status: "error"; message: string };

function getErrorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  return "오류가 발생했습니다. 다시 시도해주세요.";
}

type Props = {
  initialDiagnosisId?: string;
};

export function RoadmapCreateView({ initialDiagnosisId }: Props) {
  const router = useRouter();
  const [state, setState] = useState<CreateState>({ status: "idle" });
  const [diagnosisId, setDiagnosisId] = useState(initialDiagnosisId ?? "");
  const [weeklyStudyHours, setWeeklyStudyHours] = useState("");
  const [targetDate, setTargetDate] = useState("");
  const loadedProfile = useRef(false);
  const minDate = useMemo(
    () => new Date(Date.now() + 86400000).toISOString().split("T")[0],
    []
  );

  useEffect(() => {
    if (loadedProfile.current) return;
    loadedProfile.current = true;
    getMyProfile()
      .then((profile) => {
        if (profile.weeklyStudyHours) {
          setWeeklyStudyHours(String(profile.weeklyStudyHours));
        }
        if (profile.targetDate) {
          setTargetDate(profile.targetDate);
        }
      })
      .catch(() => {});
  }, []);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    const hours = Number(weeklyStudyHours);
    if (!diagnosisId.trim()) {
      setState({ status: "error", message: "진단 ID를 입력해주세요." });
      return;
    }
    if (!hours || hours < 1 || hours > 40) {
      setState({ status: "error", message: "주당 학습 시간은 1~40 사이로 입력해주세요." });
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

    setState({ status: "submitting" });
    try {
      const result = await apiClient.post<{ roadmapId: string }>("/api/roadmaps", {
        diagnosisId: Number(diagnosisId),
        weeklyStudyHours: hours,
        targetDate,
      });
      router.push(`/roadmaps/${result.roadmapId}`);
    } catch (err) {
      setState({ status: "error", message: getErrorMessage(err) });
    }
  }

  const isSubmitting = state.status === "submitting";

  return (
    <div>
      <h1>로드맵 생성</h1>
      <p>진단 결과를 기반으로 맞춤 학습 로드맵을 생성합니다.</p>

      {state.status === "error" && (
        <StatePanel message={state.message} tone="danger" />
      )}

      <form onSubmit={handleSubmit}>
        <div>
          <label htmlFor="diagnosisId">진단 ID</label>
          <input
            id="diagnosisId"
            type="text"
            value={diagnosisId}
            onChange={(e) => setDiagnosisId(e.target.value)}
            placeholder="진단 결과 ID를 입력하세요"
            disabled={isSubmitting}
            required
          />
        </div>

        <div>
          <label htmlFor="weeklyStudyHours">주당 학습 시간 (1~40)</label>
          <input
            id="weeklyStudyHours"
            type="number"
            min={1}
            max={40}
            value={weeklyStudyHours}
            onChange={(e) => setWeeklyStudyHours(e.target.value)}
            placeholder="예: 10"
            disabled={isSubmitting}
            required
          />
        </div>

        <div>
          <label htmlFor="targetDate">목표 날짜</label>
          <input
            id="targetDate"
            type="date"
            value={targetDate}
            onChange={(e) => setTargetDate(e.target.value)}
            min={minDate}
            disabled={isSubmitting}
            required
          />
        </div>

        <button type="submit" className="btn-primary" disabled={isSubmitting}>
          {isSubmitting ? "생성 중..." : "로드맵 생성"}
        </button>
      </form>
    </div>
  );
}
