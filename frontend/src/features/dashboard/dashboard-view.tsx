"use client";

import { useEffect, useState } from "react";

import { getDashboard } from "@/features/dashboard/api";
import { currentLevelLabels } from "@/features/dashboard/labels";
import type { Dashboard } from "@/features/dashboard/types";
import { ApiError } from "@/lib/api";

type DashboardState =
  | { status: "loading" }
  | { status: "error"; message: string }
  | { status: "success"; dashboard: Dashboard };

export function DashboardView() {
  const [state, setState] = useState<DashboardState>({ status: "loading" });

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

  if (state.status === "loading") {
    return <DashboardStatePanel message="대시보드를 불러오는 중입니다." />;
  }

  if (state.status === "error") {
    return <DashboardStatePanel message={state.message} tone="danger" />;
  }

  const { dashboard } = state;

  return (
    <section className="screen-shell">
      <div className="screen-hero">
        <p className="eyebrow">사용자 {dashboard.userId}</p>
        <div className="screen-heading">
          <h1>대시보드</h1>
          <p>프로필, GitHub 분석, 진단, 로드맵의 최신 상태를 확인합니다.</p>
        </div>
      </div>

      <div className="content-grid">
        <section className="panel">
          <h2>프로필</h2>
          {dashboard.profile ? (
            <ul className="item-list">
              <li>직무 ID: {dashboard.profile.jobRoleId}</li>
              <li>현재 수준: {currentLevelLabels[dashboard.profile.currentLevel]}</li>
              <li>
                주당 학습 시간:{" "}
                {dashboard.profile.weeklyStudyHours
                  ? `${dashboard.profile.weeklyStudyHours}시간`
                  : "미설정"}
              </li>
              <li>수정일: {formatDateTime(dashboard.profile.updatedAt)}</li>
            </ul>
          ) : (
            <EmptySummary message="저장된 프로필이 없습니다." />
          )}
        </section>

        <section className="panel">
          <h2>GitHub 분석</h2>
          {dashboard.githubAnalysis ? (
            <ul className="item-list">
              <li>{dashboard.githubAnalysis.summary}</li>
              <li>
                확정 기술:{" "}
                {formatList(
                  dashboard.githubAnalysis.finalTechProfile.confirmedSkills
                )}
              </li>
              <li>
                보정 개수: {dashboard.githubAnalysis.userCorrectionCount}개
              </li>
              <li>생성일: {formatDateTime(dashboard.githubAnalysis.createdAt)}</li>
            </ul>
          ) : (
            <EmptySummary message="저장된 GitHub 분석이 없습니다." />
          )}
        </section>

        <section className="panel">
          <h2>진단</h2>
          {dashboard.diagnosis ? (
            <ul className="item-list">
              <li>{dashboard.diagnosis.summary}</li>
              <li>버전: v{dashboard.diagnosis.version}</li>
              <li>생성일: {formatDateTime(dashboard.diagnosis.createdAt)}</li>
            </ul>
          ) : (
            <EmptySummary message="저장된 진단 결과가 없습니다." />
          )}
        </section>

        <section className="panel">
          <h2>로드맵</h2>
          {dashboard.roadmap ? (
            <ul className="item-list">
              <li>{dashboard.roadmap.summary}</li>
              <li>전체 기간: {dashboard.roadmap.totalWeeks}주</li>
              <li>
                진행: 완료 {dashboard.roadmap.progress.doneWeeks}주 / 전체{" "}
                {dashboard.roadmap.progress.totalWeeks}주
              </li>
              <li>생성일: {formatDateTime(dashboard.roadmap.createdAt)}</li>
            </ul>
          ) : (
            <EmptySummary message="저장된 로드맵이 없습니다." />
          )}
        </section>
      </div>
    </section>
  );
}

function DashboardStatePanel({
  message,
  tone = "neutral"
}: {
  message: string;
  tone?: "danger" | "neutral";
}) {
  return (
    <section className="panel roadmap-state-panel" data-tone={tone}>
      <p>{message}</p>
    </section>
  );
}

function EmptySummary({ message }: { message: string }) {
  return <p className="dashboard-empty-summary">{message}</p>;
}

function getErrorMessage(error: unknown) {
  if (error instanceof ApiError) {
    return error.message;
  }

  return "대시보드를 불러오지 못했습니다.";
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    dateStyle: "medium",
    timeStyle: "short"
  }).format(new Date(value));
}

function formatList(values: string[]) {
  return values.length > 0 ? values.join(", ") : "없음";
}
