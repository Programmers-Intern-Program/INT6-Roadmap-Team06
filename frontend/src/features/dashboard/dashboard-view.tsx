"use client";

import Link from "next/link";
import { useEffect, useState } from "react";

import { AuthRequiredPanel } from "@/components/auth-required-panel";
import { StatePanel } from "@/components/state-panel";
import { TagList } from "@/components/tag-list";
import { getDashboard } from "@/features/dashboard/api";
import { currentLevelLabels } from "@/features/dashboard/labels";
import type {
  Dashboard,
  DashboardProgressSummary
} from "@/features/dashboard/types";
import { ApiError } from "@/lib/api";
import { isUnauthorizedError } from "@/lib/auth";

type DashboardState =
  | { status: "loading" }
  | { status: "auth-required" }
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
        if (ignore) return;
        if (isUnauthorizedError(error)) {
          setState({ status: "auth-required" });
          return;
        }
        setState({
          message: getErrorMessage(error),
          status: "error"
        });
      }
    }

    loadDashboard();

    return () => {
      ignore = true;
    };
  }, []);

  if (state.status === "loading") {
    return (
      <StatePanel
        className="roadmap-state-panel"
        message="대시보드를 불러오는 중입니다."
      />
    );
  }

  if (state.status === "auth-required") {
    return <AuthRequiredPanel className="roadmap-state-panel" redirectPath="/" />;
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
  const nextStep = getNextStep(dashboard);

  return (
    <section className="screen-shell">
      <div className="dashboard-hero">
        <div className="dashboard-hero-content">
          <p className="eyebrow">대시보드</p>
          <h1 className="dashboard-hero-title">현재 상태 한눈에 보기</h1>
          <p className="dashboard-hero-subtitle">
            프로필 · GitHub 분석 · 역량 진단 · 학습 로드맵의 진행 상황을 확인하세요.
          </p>
        </div>
        <div className="dashboard-hero-cta" aria-label="다음 추천 작업">
          <span className="dashboard-hero-cta-label">다음에 할 일</span>
          <Link className="dashboard-hero-cta-button" href={nextStep.href}>
            {nextStep.label} →
          </Link>
        </div>
      </div>

      <ol className="dashboard-workflow-steps" aria-label="진행 단계">
        <WorkflowStep
          index={1}
          label="프로필"
          done={Boolean(dashboard.profile)}
          active={!dashboard.profile}
        />
        <WorkflowStep
          index={2}
          label="GitHub 분석"
          done={Boolean(dashboard.githubAnalysis)}
          active={Boolean(dashboard.profile) && !dashboard.githubAnalysis}
        />
        <WorkflowStep
          index={3}
          label="역량 진단"
          done={Boolean(dashboard.diagnosis)}
          active={Boolean(dashboard.githubAnalysis) && !dashboard.diagnosis}
        />
        <WorkflowStep
          index={4}
          label="학습 로드맵"
          done={Boolean(dashboard.roadmap)}
          active={Boolean(dashboard.diagnosis) && !dashboard.roadmap}
        />
      </ol>

      <div className="dashboard-summary-grid">
        <section className="panel dashboard-summary-card" data-tone="profile">
          <div className="dashboard-card-header">
            <span className="dashboard-card-icon" aria-hidden="true">👤</span>
            <h2>프로필</h2>
          </div>
          {dashboard.profile ? (
            <>
              <dl className="dashboard-metric-list">
                <div>
                  <dt>현재 수준</dt>
                  <dd>{currentLevelLabels[dashboard.profile.currentLevel]}</dd>
                </div>
                <div>
                  <dt>주당 학습</dt>
                  <dd>
                    {dashboard.profile.weeklyStudyHours
                      ? `${dashboard.profile.weeklyStudyHours}시간`
                      : "미설정"}
                  </dd>
                </div>
                <div>
                  <dt>목표일</dt>
                  <dd>{dashboard.profile.targetDate ?? "미설정"}</dd>
                </div>
              </dl>
              <p className="dashboard-card-note">
                최근 수정 {formatDateTime(dashboard.profile.updatedAt)}
              </p>
              <Link className="dashboard-card-link" href="/profile">
                프로필 보기
              </Link>
            </>
          ) : (
            <EmptySummary
              actionHref="/profile"
              actionLabel="프로필 입력"
              message="저장된 프로필이 없습니다."
            />
          )}
        </section>

        <section className="panel dashboard-summary-card" data-tone="github">
          <div className="dashboard-card-header">
            <span className="dashboard-card-icon" aria-hidden="true">🐙</span>
            <h2>GitHub 분석</h2>
          </div>
          {dashboard.githubAnalysis ? (
            <>
              <p className="dashboard-card-summary">
                {dashboard.githubAnalysis.summary}
              </p>
              <TagList
                items={dashboard.githubAnalysis.finalTechProfile.confirmedSkills}
                label="확정 기술"
              />
              <TagList
                items={dashboard.githubAnalysis.finalTechProfile.focusAreas}
                label="관심 영역"
              />
              <p className="dashboard-card-note">
                사용자 보정 {dashboard.githubAnalysis.userCorrectionCount}개 ·{" "}
                {formatDateTime(dashboard.githubAnalysis.createdAt)}
              </p>
              <Link
                className="dashboard-card-link"
                href={`/github/analysis?githubAnalysisId=${dashboard.githubAnalysis.githubAnalysisId}`}
              >
                분석 보정 보기
              </Link>
            </>
          ) : (
            <EmptySummary
              actionHref="/github"
              actionLabel="GitHub 연동"
              message="저장된 GitHub 분석이 없습니다."
            />
          )}
        </section>

        <section className="panel dashboard-summary-card" data-tone="diagnosis">
          <div className="dashboard-card-header">
            <span className="dashboard-card-icon" aria-hidden="true">🎯</span>
            <h2>진단</h2>
          </div>
          {dashboard.diagnosis ? (
            <>
              <p className="dashboard-card-summary">
                {dashboard.diagnosis.summary}
              </p>
              <p className="dashboard-card-note">
                v{dashboard.diagnosis.version} ·{" "}
                {formatDateTime(dashboard.diagnosis.createdAt)}
              </p>
              <Link
                className="dashboard-card-link"
                href={`/diagnoses/${dashboard.diagnosis.diagnosisId}`}
              >
                진단 결과 보기
              </Link>
            </>
          ) : (
            <EmptySummary
              actionHref="/github/analysis"
              actionLabel="분석 보정"
              message="저장된 진단 결과가 없습니다."
            />
          )}
        </section>

        <section className="panel dashboard-summary-card" data-tone="roadmap">
          <div className="dashboard-card-header">
            <span className="dashboard-card-icon" aria-hidden="true">🗺️</span>
            <h2>로드맵</h2>
          </div>
          {dashboard.roadmap ? (
            <>
              <p className="dashboard-card-summary">{dashboard.roadmap.summary}</p>
              <ProgressSummary progress={dashboard.roadmap.progress} />
              <p className="dashboard-card-note">
                v{dashboard.roadmap.version} · {dashboard.roadmap.totalWeeks}주 ·{" "}
                {formatDateTime(dashboard.roadmap.createdAt)}
              </p>
              <Link
                className="dashboard-card-link"
                href={`/roadmaps/${dashboard.roadmap.roadmapId}`}
              >
                로드맵 보기
              </Link>
            </>
          ) : (
            <EmptySummary
              actionHref="/roadmaps/new"
              actionLabel="로드맵 생성"
              message="저장된 로드맵이 없습니다."
            />
          )}
        </section>
      </div>
    </section>
  );
}

function getNextStep(dashboard: Dashboard): { href: string; label: string } {
  if (!dashboard.profile) return { href: "/profile", label: "프로필 입력하기" };
  if (!dashboard.githubAnalysis) return { href: "/github", label: "GitHub 연동하기" };
  if (!dashboard.diagnosis) {
    return {
      href: `/diagnoses/new?githubAnalysisId=${dashboard.githubAnalysis.githubAnalysisId}`,
      label: "역량 진단 만들기"
    };
  }
  if (!dashboard.roadmap) {
    return {
      href: `/roadmaps/new?diagnosisId=${dashboard.diagnosis.diagnosisId}`,
      label: "학습 로드맵 만들기"
    };
  }
  return {
    href: `/roadmaps/${dashboard.roadmap.roadmapId}`,
    label: "내 로드맵 이어가기"
  };
}

function WorkflowStep({
  index,
  label,
  done,
  active
}: {
  index: number;
  label: string;
  done: boolean;
  active: boolean;
}) {
  const tone = done ? "done" : active ? "active" : "todo";
  return (
    <li className="dashboard-workflow-step" data-tone={tone}>
      <span className="dashboard-workflow-step-marker" aria-hidden="true">
        {done ? "✓" : index}
      </span>
      <span className="dashboard-workflow-step-label">{label}</span>
    </li>
  );
}

function EmptySummary({
  actionHref,
  actionLabel,
  message
}: {
  actionHref: string;
  actionLabel: string;
  message: string;
}) {
  return (
    <div className="dashboard-empty-summary">
      <p>{message}</p>
      <Link className="dashboard-card-link" href={actionHref}>
        {actionLabel}
      </Link>
    </div>
  );
}

function ProgressSummary({ progress }: { progress: DashboardProgressSummary }) {
  const doneRate =
    progress.totalWeeks > 0
      ? Math.round((progress.doneWeeks / progress.totalWeeks) * 100)
      : 0;

  return (
    <div className="dashboard-progress-summary">
      <div>
        <strong>{doneRate}%</strong>
        <span>
          완료 {progress.doneWeeks}주 / 전체 {progress.totalWeeks}주
        </span>
      </div>
      <dl>
        <div>
          <dt>예정</dt>
          <dd>{progress.todoWeeks}</dd>
        </div>
        <div>
          <dt>진행</dt>
          <dd>{progress.inProgressWeeks}</dd>
        </div>
        <div>
          <dt>완료</dt>
          <dd>{progress.doneWeeks}</dd>
        </div>
        <div>
          <dt>건너뜀</dt>
          <dd>{progress.skippedWeeks}</dd>
        </div>
      </dl>
    </div>
  );
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
