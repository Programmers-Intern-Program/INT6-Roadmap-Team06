"use client";

import Link from "next/link";
import { useEffect, useState } from "react";

import { AuthRequiredPanel } from "@/components/auth-required-panel";
import { StatePanel } from "@/components/state-panel";
import { listGithubAnalyses } from "@/features/github-analysis/api";
import { AnalysisJobHistorySection } from "@/features/github-analysis/analysis-job-history-section";
import type { GithubAnalysisSummary } from "@/features/github-analysis/types";
import { ApiError } from "@/lib/api";
import { isUnauthorizedError } from "@/lib/auth";

type State =
  | { status: "loading" }
  | { status: "auth-required" }
  | { status: "ready"; items: GithubAnalysisSummary[] }
  | { status: "error"; message: string };

export function GithubAnalysisListView() {
  const [state, setState] = useState<State>({ status: "loading" });

  useEffect(() => {
    let cancelled = false;

    listGithubAnalyses()
      .then((items) => {
        if (cancelled) return;
        setState({ status: "ready", items });
      })
      .catch((error) => {
        if (cancelled) return;
        if (isUnauthorizedError(error)) {
          setState({ status: "auth-required" });
          return;
        }
        setState({ status: "error", message: getErrorMessage(error) });
      });

    return () => {
      cancelled = true;
    };
  }, []);

  if (state.status === "loading") {
    return <StatePanel message="GitHub 분석 목록을 불러오는 중입니다." />;
  }

  if (state.status === "auth-required") {
    return <AuthRequiredPanel redirectPath="/github/analysis" />;
  }

  if (state.status === "error") {
    return <StatePanel message={state.message} tone="danger" />;
  }

  if (state.items.length === 0) {
    return (
      <section className="screen-shell">
        <AnalysisJobHistorySection />
        <StatePanel message="아직 생성된 GitHub 분석 결과가 없습니다." />
        <div className="action-row" aria-label="GitHub 분석 다음 행동">
          <Link className="action-link primary" href="/github">
            GitHub 연동
          </Link>
        </div>
      </section>
    );
  }

  return (
    <section className="screen-shell">
      <header className="screen-header">
        <h2 className="screen-title">GitHub 분석</h2>
        <p className="screen-description">
          저장소 분석 결과를 시점별로 모아 봅니다. 보정 작업은 카드 상세에서 진행해요.
        </p>
      </header>
      <AnalysisJobHistorySection />
      <ul className="card-grid">
        {state.items.map((item) => (
          <li key={item.githubAnalysisId} className="result-card">
            <header className="result-card-header">
              <span className="result-card-meta">
                {formatDate(item.createdAt)}
              </span>
              <span className="result-card-badge" data-tone="accent">
                v{item.version}
              </span>
            </header>
            <p className="result-card-body">{item.summary}</p>
            <footer className="result-card-footer">
              <div className="result-card-actions">
                <Link
                  className="action-link primary"
                  href={`/github/analysis?githubAnalysisId=${item.githubAnalysisId}`}
                >
                  상세 보기
                </Link>
                <Link
                  className="action-link"
                  href={`/diagnoses/new?githubAnalysisId=${item.githubAnalysisId}`}
                >
                  진단 생성
                </Link>
              </div>
            </footer>
          </li>
        ))}
      </ul>
    </section>
  );
}

function formatDate(iso: string) {
  try {
    const d = new Date(iso);
    return new Intl.DateTimeFormat("ko-KR", {
      year: "numeric",
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
  return "GitHub 분석 목록을 불러오지 못했습니다.";
}
