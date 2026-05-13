"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";

import {
  listJobHistory,
  type JobHistoryItem
} from "@/features/github-connection/api";
import { useGithubAnalysisJob } from "@/features/github-connection/github-analysis-job-context";
import { AnalysisJobProgressPanel } from "@/features/github-analysis/analysis-job-progress-panel";
import { ApiError } from "@/lib/api";
import { isUnauthorizedError } from "@/lib/auth";

type State =
  | { status: "loading" }
  | { status: "hidden" }
  | { status: "ready"; items: JobHistoryItem[] }
  | { status: "error"; message: string };

export function AnalysisJobHistorySection() {
  const { activeJob, dismissJob, submitting, submitError } = useGithubAnalysisJob();
  const [state, setState] = useState<State>({ status: "loading" });
  const pollTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  // 초기 로드 + activeJob 변경 시 자동 폴링
  useEffect(() => {
    let cancelled = false;

    const loadHistory = async () => {
      try {
        const items = await listJobHistory(20);
        if (cancelled) return;
        const analysisItems = items.filter(
          (item) => item.jobType === "GITHUB_ANALYSIS"
        );
        if (analysisItems.length === 0) {
          setState({ status: "hidden" });
          return;
        }
        setState({ status: "ready", items: analysisItems });
      } catch (error) {
        if (cancelled) return;
        if (isUnauthorizedError(error)) {
          setState({ status: "hidden" });
          return;
        }
        setState({ status: "error", message: getErrorMessage(error) });
      }
    };

    void loadHistory();

    // 진행 중인 job이 있으면 3초마다 폴링
    if (activeJob) {
      if (pollTimerRef.current) {
        clearInterval(pollTimerRef.current);
      }
      pollTimerRef.current = setInterval(() => {
        void loadHistory();
      }, 3000);
    }

    return () => {
      cancelled = true;
      if (pollTimerRef.current) {
        clearInterval(pollTimerRef.current);
        pollTimerRef.current = null;
      }
    };
  }, [activeJob]);

  // 분석 제출 중일 때는 빈 history여도 submission 진행 UI를 보여준다
  if ((state.status === "loading" || state.status === "hidden") && !submitting && !submitError) {
    return null;
  }

  if (state.status === "error") {
    return (
      <section className="job-history-section" aria-label="최근 분석 작업">
        <header className="job-history-header">
          <h3 className="job-history-title">최근 분석 작업</h3>
        </header>
        <p className="job-history-empty">{state.message}</p>
      </section>
    );
  }

  const items = state.status === "ready" ? state.items : [];

  return (
    <section className="job-history-section" aria-label="최근 분석 작업">
      <header className="job-history-header">
        <h3 className="job-history-title">최근 분석 작업</h3>
        <p className="job-history-description">
          최근 7일 안의 비동기 분석 잡 결과를 보여줘요. (최대 20건)
        </p>
      </header>

      {submitting ? (
        <div className="analysis-job-progress-panel" role="status" aria-live="polite">
          <div className="analysis-job-progress-icon">
            <span className="analysis-job-spinner" aria-hidden="true"></span>
          </div>
          <div className="analysis-job-progress-content">
            <p className="analysis-job-progress-label">REQUESTING</p>
            <p className="analysis-job-progress-title">분석 작업을 시작하고 있습니다</p>
            <p className="analysis-job-progress-description">
              저장소 정보를 검증하고 분석 잡을 등록하는 중입니다. 보통 10~30초 정도 걸려요.
            </p>
            <p className="analysis-job-progress-detail">
              이 페이지를 벗어나거나 새로고침해도 분석은 계속 진행됩니다.
            </p>
          </div>
        </div>
      ) : null}

      {submitError ? (
        <div className="analysis-job-progress-panel" data-tone="danger" role="alert">
          <div className="analysis-job-progress-content">
            <p className="analysis-job-progress-label">FAILED</p>
            <p className="analysis-job-progress-title">분석 작업을 시작하지 못했습니다</p>
            <p className="analysis-job-progress-description">{submitError}</p>
            <div className="analysis-job-progress-actions">
              <button
                type="button"
                className="analysis-job-progress-dismiss-btn"
                onClick={dismissJob}
              >
                닫기
              </button>
            </div>
          </div>
        </div>
      ) : null}

      <ul className="job-history-list">
        {items.map((item) => (
          <li
            key={item.jobId}
            className="job-history-item"
            data-status={item.status.toLowerCase()}
          >
            {item.status === "RUNNING" || item.status === "REQUESTED" ? (
              <AnalysisJobProgressPanel
                status={item.status}
                currentStep={item.currentStep}
                onDismiss={dismissJob}
              />
            ) : (
              <>
                <div className="job-history-item-main">
                  <span
                    className="job-history-status"
                    data-status={item.status.toLowerCase()}
                  >
                    {statusLabel(item.status)}
                  </span>
                  <span className="job-history-time">
                    {formatDate(item.recordedAt)}
                  </span>
                </div>
                <div className="job-history-item-detail">
                  {item.status === "SUCCEEDED" && item.resultId ? (
                    <Link
                      className="job-history-link"
                      href={`/github/analysis?githubAnalysisId=${item.resultId}`}
                    >
                      분석 #{item.resultId} 보기 →
                    </Link>
                  ) : null}
                  {item.status === "FAILED" && item.error ? (
                    <span className="job-history-error" title={item.error}>
                      {item.error}
                    </span>
                  ) : null}
                  {item.currentStep ? (
                    <span className="job-history-step">단계: {item.currentStep}</span>
                  ) : null}
                </div>
              </>
            )}
          </li>
        ))}
      </ul>
    </section>
  );
}

function statusLabel(status: JobHistoryItem["status"]) {
  switch (status) {
    case "SUCCEEDED":
      return "성공";
    case "FAILED":
      return "실패";
    case "RUNNING":
      return "진행 중";
    case "REQUESTED":
      return "대기";
  }
}

function formatDate(iso: string) {
  try {
    const d = new Date(iso);
    return new Intl.DateTimeFormat("ko-KR", {
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit"
    }).format(d);
  } catch {
    return iso;
  }
}

function getErrorMessage(error: unknown) {
  if (error instanceof ApiError) return error.message;
  return "최근 작업 내역을 불러오지 못했습니다.";
}
