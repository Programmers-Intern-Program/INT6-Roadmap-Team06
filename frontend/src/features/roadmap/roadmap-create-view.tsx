"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState, type FormEvent } from "react";

import { StatePanel } from "@/components/state-panel";
import { getDashboard } from "@/features/dashboard/api";
import type { Dashboard } from "@/features/dashboard/types";
import { createRoadmap } from "@/features/roadmap/api";
import { ApiError } from "@/lib/api";

type RoadmapCreateState =
  | { status: "loading" }
  | { status: "error"; message: string }
  | { status: "success"; dashboard: Dashboard };

export function RoadmapCreateView() {
  const router = useRouter();
  const [state, setState] = useState<RoadmapCreateState>({ status: "loading" });
  const [isCreating, setIsCreating] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);

  useEffect(() => {
    let ignore = false;

    async function loadDashboard() {
      setState({ status: "loading" });

      try {
        const dashboard = await getDashboard();

        if (!ignore) {
          setState({ dashboard, status: "success" });
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

    loadDashboard();

    return () => {
      ignore = true;
    };
  }, []);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (state.status !== "success" || !state.dashboard.diagnosis) {
      setCreateError("로드맵 생성 전에 진단 결과를 먼저 생성해 주세요.");
      return;
    }

    const formData = new FormData(event.currentTarget);
    const diagnosisId = getStringFormValue(formData, "diagnosisId");

    if (!diagnosisId) {
      setCreateError("진단 결과 ID가 필요합니다.");
      return;
    }

    setIsCreating(true);
    setCreateError(null);

    try {
      const roadmap = await createRoadmap({
        diagnosisId,
        githubAnalysisId: getOptionalStringFormValue(formData, "githubAnalysisId"),
        targetDate: getOptionalStringFormValue(formData, "targetDate"),
        weeklyStudyHours: getOptionalNumberFormValue(formData, "weeklyStudyHours")
      });

      router.push(`/roadmaps/${roadmap.roadmapId}`);
    } catch (error) {
      setCreateError(getCreateErrorMessage(error));
    } finally {
      setIsCreating(false);
    }
  }

  if (state.status === "loading") {
    return (
      <StatePanel
        className="roadmap-state-panel"
        message="로드맵 생성에 필요한 최신 결과를 불러오는 중입니다."
      />
    );
  }

  if (state.status === "error") {
    return (
      <StatePanel
        className="roadmap-state-panel"
        message={state.message}
        tone="danger"
      />
    );
  }

  const { dashboard } = state;

  return (
    <section className="roadmap-create-page" aria-labelledby="roadmap-create-title">
      <div className="screen-hero">
        <p className="eyebrow">v1 필수</p>
        <div className="screen-heading">
          <h1 id="roadmap-create-title">학습 로드맵 생성</h1>
          <p>최신 진단 결과를 기준으로 학습 로드맵을 생성합니다.</p>
        </div>
        <div className="action-row" aria-label="로드맵 생성 관련 화면 이동">
          {dashboard.diagnosis ? (
            <Link
              className="action-link"
              href={`/diagnoses/${dashboard.diagnosis.diagnosisId}`}
            >
              진단 결과 보기
            </Link>
          ) : (
            <Link className="action-link primary" href="/github/analysis">
              분석 보정으로 이동
            </Link>
          )}
          {dashboard.roadmap ? (
            <Link
              className="action-link"
              href={`/roadmaps/${dashboard.roadmap.roadmapId}`}
            >
              최근 로드맵 보기
            </Link>
          ) : null}
        </div>
      </div>

      {dashboard.diagnosis ? (
        <form className="panel roadmap-create-form" onSubmit={handleSubmit}>
          <div className="roadmap-create-heading">
            <h2>생성 기준</h2>
            <p>{dashboard.diagnosis.summary}</p>
          </div>

          <div className="roadmap-create-fields">
            <label>
              <span>진단 결과 ID</span>
              <input
                defaultValue={dashboard.diagnosis.diagnosisId}
                name="diagnosisId"
                required
              />
            </label>
            <label>
              <span>GitHub 분석 ID</span>
              <input
                defaultValue={dashboard.githubAnalysis?.githubAnalysisId ?? ""}
                name="githubAnalysisId"
              />
            </label>
            <label>
              <span>주당 학습 시간</span>
              <input
                defaultValue={dashboard.profile?.weeklyStudyHours ?? ""}
                max={40}
                min={1}
                name="weeklyStudyHours"
                type="number"
              />
            </label>
            <label>
              <span>목표 날짜</span>
              <input
                defaultValue={dashboard.profile?.targetDate ?? ""}
                name="targetDate"
                type="date"
              />
            </label>
          </div>

          <div className="roadmap-create-actions">
            <div>
              {createError ? (
                <p className="roadmap-create-error">{createError}</p>
              ) : null}
            </div>
            <button disabled={isCreating} type="submit">
              {isCreating ? "로드맵 생성 중" : "로드맵 생성"}
            </button>
          </div>
        </form>
      ) : (
        <StatePanel
          className="roadmap-state-panel"
          message="저장된 진단 결과가 없습니다. GitHub 분석 보정 후 진단을 먼저 생성해 주세요."
        />
      )}
    </section>
  );
}

function getStringFormValue(formData: FormData, key: string) {
  const value = formData.get(key);

  return typeof value === "string" ? value.trim() : "";
}

function getOptionalStringFormValue(formData: FormData, key: string) {
  const value = getStringFormValue(formData, key);

  return value.length > 0 ? value : undefined;
}

function getOptionalNumberFormValue(formData: FormData, key: string) {
  const value = getStringFormValue(formData, key);

  return value.length > 0 ? Number(value) : undefined;
}

function getErrorMessage(error: unknown) {
  if (error instanceof ApiError) {
    return error.message;
  }

  return "로드맵 생성 기준을 불러오지 못했습니다.";
}

function getCreateErrorMessage(error: unknown) {
  if (error instanceof ApiError) {
    return error.message;
  }

  return "로드맵을 생성하지 못했습니다.";
}
