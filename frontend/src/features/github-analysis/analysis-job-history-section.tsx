"use client";

import Link from "next/link";
import { useEffect, useState } from "react";

import {
  listJobHistory,
  type JobHistoryItem
} from "@/features/github-connection/api";
import { ApiError } from "@/lib/api";
import { isUnauthorizedError } from "@/lib/auth";

type State =
  | { status: "loading" }
  | { status: "hidden" }
  | { status: "ready"; items: JobHistoryItem[] }
  | { status: "error"; message: string };

export function AnalysisJobHistorySection() {
  const [state, setState] = useState<State>({ status: "loading" });

  useEffect(() => {
    let cancelled = false;

    listJobHistory(20)
      .then((items) => {
        if (cancelled) return;
        const analysisItems = items.filter(
          (item) => item.jobType === "GITHUB_ANALYSIS"
        );
        if (analysisItems.length === 0) {
          setState({ status: "hidden" });
          return;
        }
        setState({ status: "ready", items: analysisItems });
      })
      .catch((error) => {
        if (cancelled) return;
        if (isUnauthorizedError(error)) {
          setState({ status: "hidden" });
          return;
        }
        setState({ status: "error", message: getErrorMessage(error) });
      });

    return () => {
      cancelled = true;
    };
  }, []);

  if (state.status === "loading" || state.status === "hidden") {
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

  return (
    <section className="job-history-section" aria-label="최근 분석 작업">
      <header className="job-history-header">
        <h3 className="job-history-title">최근 분석 작업</h3>
        <p className="job-history-description">
          최근 7일 안의 비동기 분석 잡 결과를 보여줘요. (최대 20건)
        </p>
      </header>
      <ul className="job-history-list">
        {state.items.map((item) => (
          <li
            key={item.jobId}
            className="job-history-item"
            data-status={item.status.toLowerCase()}
          >
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
