"use client";

import Link from "next/link";
import { useEffect, useState } from "react";

import { AuthRequiredPanel } from "@/components/auth-required-panel";
import { StatePanel } from "@/components/state-panel";
import { listGithubAnalyses } from "@/features/github-analysis/api";
import { AnalysisJobHistorySection } from "@/features/github-analysis/analysis-job-history-section";
import { useGithubAnalysisJob } from "@/features/github-connection/github-analysis-job-context";
import type { GithubAnalysisSummary } from "@/features/github-analysis/types";
import { ApiError } from "@/lib/api";
import { isUnauthorizedError } from "@/lib/auth";

type State =
  | { status: "loading" }
  | { status: "auth-required" }
  | { status: "ready"; items: GithubAnalysisSummary[] }
  | { status: "error"; message: string };

export function GithubAnalysisListView() {
  const { cacheInvalidateKey } = useGithubAnalysisJob();
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
  }, [cacheInvalidateKey]);

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
        {state.items.map((item, index) => {
          const reanalysis = isReanalysis(item, state.items, index);
          return (
            <li key={item.githubAnalysisId} className="result-card">
              <header className="result-card-header">
                <span className="result-card-meta">
                  {formatDate(item.createdAt)}
                </span>
                <div className="result-card-badges">
                  {reanalysis && (
                    <span className="result-card-badge" data-tone="warning">
                      재분석
                    </span>
                  )}
                  <span className="result-card-badge" data-tone="accent">
                    v{item.version}
                  </span>
                </div>
              </header>
              {item.repoNames.length > 0 && (
                <div className="result-card-repos" aria-label="분석에 사용된 저장소">
                  {item.repoNames.slice(0, 5).map((name) => (
                    <span key={name} className="result-card-repo-chip">
                      {name}
                    </span>
                  ))}
                  {item.repoNames.length > 5 && (
                    <span className="result-card-repo-chip" data-tone="muted">
                      +{item.repoNames.length - 5}
                    </span>
                  )}
                </div>
              )}
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
          );
        })}
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

/**
 * 동일한 repo 조합을 사용한 이전 분석이 list에 있으면 '재분석'으로 판정.
 * items는 createdAt DESC로 정렬되어 있다고 가정 — index보다 뒤(=과거)에
 * 같은 repo 조합이 이미 존재하면 현재 항목은 그 분석의 재분석.
 */
function isReanalysis(
  item: GithubAnalysisSummary,
  items: GithubAnalysisSummary[],
  index: number
): boolean {
  if (item.repoNames.length === 0) return false;
  const key = [...item.repoNames].sort().join("|");
  for (let i = index + 1; i < items.length; i++) {
    const other = items[i];
    if (other.repoNames.length !== item.repoNames.length) continue;
    const otherKey = [...other.repoNames].sort().join("|");
    if (otherKey === key) return true;
  }
  return false;
}
