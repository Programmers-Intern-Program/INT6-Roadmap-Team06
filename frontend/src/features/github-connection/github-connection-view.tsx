"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { ApiError, githubConnectionOAuthUrl } from "@/lib/api";
import { isUnauthorizedError } from "@/lib/auth";
import {
  getLatestGithubConnection,
  getRepositories
} from "@/features/github-connection/api";
import { useGithubAnalysisJob } from "@/features/github-connection/github-analysis-job-context";
import type { Repository } from "@/features/github-connection/types";
import { AuthRequiredPanel } from "@/components/auth-required-panel";
import { StatePanel } from "@/components/state-panel";

type ViewState =
  | { status: "loading-connection" }
  | { status: "disconnected" }
  | { status: "loading-repos" }
  | { status: "auth-required" }
  | { status: "ready"; repos: Repository[] }
  | { status: "error"; message: string };

function getErrorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  return "오류가 발생했습니다. 다시 시도해주세요.";
}

export function GithubConnectionView() {
  const router = useRouter();
  const { submitAnalysisJob } = useGithubAnalysisJob();
  const [connectionId, setConnectionId] = useState<string | null>(null);
  const [state, setState] = useState<ViewState>({ status: "loading-connection" });
  const [selected, setSelected] = useState<Set<string>>(() => new Set());
  const [connectUrl] = useState(() => githubConnectionOAuthUrl());

  useEffect(() => {
    let cancelled = false;

    getLatestGithubConnection()
      .then((connection) => {
        if (cancelled) return;
        setConnectionId(connection.githubConnectionId);
        setState({ status: "loading-repos" });
      })
      .catch((error) => {
        if (cancelled) return;
        if (isUnauthorizedError(error)) {
          setState({ status: "auth-required" });
          return;
        }
        if (error instanceof ApiError && error.status === 404) {
          setState({ status: "disconnected" });
          return;
        }
        setState({ status: "error", message: getErrorMessage(error) });
      });

    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (!connectionId) return;

    let cancelled = false;
    getRepositories(connectionId)
      .then((repos) => {
        if (!cancelled) setState({ status: "ready", repos: repos.repositories });
      })
      .catch((err) => {
        if (cancelled) return;
        if (err instanceof ApiError && err.status === 404) {
          setConnectionId(null);
          setState({ status: "disconnected" });
        } else if (isUnauthorizedError(err)) {
          setState({ status: "auth-required" });
        } else {
          setState({ status: "error", message: getErrorMessage(err) });
        }
      });
    return () => { cancelled = true; };
  }, [connectionId]);

  function toggleRepo(id: string) {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) { next.delete(id); } else { next.add(id); }
      return next;
    });
  }

  function reconnect() {
    setConnectionId(null);
    setSelected(new Set());
    setState({ status: "disconnected" });
  }

  function handleAnalyze() {
    if (!connectionId || selected.size === 0) return;
    const ids = Array.from(selected);
    // 즉시 페이지 이동 — Context가 백그라운드에서 submission 처리
    void submitAnalysisJob(connectionId, ids, ids);
    router.push('/github/analysis');
  }

  if (state.status === "loading-connection") {
    return <StatePanel message="연결 정보를 확인하는 중..." />;
  }

  if (!connectionId || state.status === "disconnected") {
    return (
      <div className="github-connection-hero-page">
        <div className="github-connection-hero-container">
          <div className="github-connection-hero-visual">
            <svg width="120" height="120" viewBox="0 0 120 120" fill="none" xmlns="http://www.w3.org/2000/svg">
              <circle cx="60" cy="60" r="58" fill="url(#heroGradient)" opacity="0.1" />
              <defs>
                <linearGradient id="heroGradient" x1="0" y1="0" x2="120" y2="120">
                  <stop offset="0%" stopColor="#2563eb" />
                  <stop offset="100%" stopColor="#1e40af" />
                </linearGradient>
              </defs>
              <g transform="translate(30 30)">
                <path d="M30 0C13.4 0 0 13.4 0 30c0 13.3 8.6 24.6 20.6 28.6 1.5.3 2.1-.7 2.1-1.5 0-.8 0-2.8 0-5.5-8.4 1.8-10.2-4-10.2-4-1.4-3.5-3.4-4.4-3.4-4.4-2.8-1.9.2-1.9.2-1.9 3.1.2 4.7 3.2 4.7 3.2 2.7 4.7 7.1 3.3 8.9 2.6.3-2 1.1-3.3 2-4.1-7-0.8-14.3-3.5-14.3-15.3 0-3.4 1.2-6.1 3.2-8.3-.3-0.8-1.4-4 .3-8.3 0 0 2.6-.8 8.5 3.1 2.5-.7 5.1-1 7.7-1 2.6 0 5.2.3 7.7 1 5.9-3.9 8.5-3.1 8.5-3.1 1.7 4.3.6 7.5.3 8.3 2 2.2 3.2 4.9 3.2 8.3 0 11.8-7.3 14.5-14.3 15.3 1.1 1 2.1 2.9 2.1 5.9 0 4.1 0 7.5 0 8.5 0 .8.6 1.8 2.1 1.5 12-4 20.6-15.3 20.6-28.6C60 13.4 46.6 0 30 0z" fill="#1e40af" />
              </g>
            </svg>
          </div>

          <div className="github-connection-hero-content">
            <h1 className="github-connection-hero-title">GitHub와 연결하기</h1>
            <p className="github-connection-hero-subtitle">
              당신의 GitHub 저장소를 분석하고 개발 성과를 한눈에 파악하세요.
            </p>

            <div className="github-connection-hero-benefits">
              <div className="benefit-item">
                <span className="benefit-icon">📊</span>
                <span className="benefit-text">저장소 분석</span>
              </div>
              <div className="benefit-item">
                <span className="benefit-icon">📈</span>
                <span className="benefit-text">성장 지표</span>
              </div>
              <div className="benefit-item">
                <span className="benefit-icon">🔍</span>
                <span className="benefit-text">깊이 있는 인사이트</span>
              </div>
            </div>

            <div className="github-connection-account-warning">
              <p className="github-connection-account-warning-title">
                ⚠️ 연결할 GitHub 계정을 먼저 확인하세요
              </p>
              <p className="github-connection-account-warning-body">
                아래 버튼을 누르면 <strong>현재 브라우저에 로그인된 GitHub 계정</strong>이 자동으로 연결됩니다.
                다른 계정을 연결하려면 먼저{" "}
                <a
                  href="https://github.com/login"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="github-connection-account-warning-link"
                >
                  GitHub에서 계정을 전환
                </a>
                한 후 아래 버튼을 눌러주세요.
              </p>
            </div>

            {connectUrl ? (
              <a href={connectUrl} className="github-connection-cta-button">
                <svg width="16" height="16" viewBox="0 0 16 16" fill="currentColor">
                  <path d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.012 8.012 0 0 0 16 8c0-4.42-3.58-8-8-8z" />
                </svg>
                GitHub 인증하기
              </a>
            ) : (
              <button
                className="github-connection-cta-button"
                disabled
                type="button"
              >
                GitHub 인증 설정 필요
              </button>
            )}

            <p className="github-connection-hero-note">
              GitHub OAuth를 통해 안전하게 연결됩니다. 인증 설정이 보이지 않으면 관리자에게 환경 변수 확인을 요청해 주세요.
            </p>
          </div>
        </div>
      </div>
    );
  }

  if (state.status === "loading-repos") {
    return <StatePanel message="저장소 목록을 불러오는 중..." />;
  }

  if (state.status === "auth-required") {
    return (
      <AuthRequiredPanel
        className="github-connection-state-panel"
        redirectPath="/github"
      />
    );
  }

  if (state.status === "error") {
    return (
      <div>
        <StatePanel message={state.message} tone="danger" />
        <p>
          <button onClick={reconnect}>
            다시 연결하기
          </button>
        </p>
      </div>
    );
  }

  const { repos } = state;

  const ownedRepos = repos.filter((r) => !r.ownerType || r.ownerType === "owner");
  const contributedRepos = repos.filter((r) => r.ownerType === "collaborator");

  const renderRepoGrid = (repoList: typeof repos) => (
    <div
      style={{
        display: "grid",
        gridTemplateColumns: "repeat(auto-fill, minmax(280px, 1fr))",
        gap: "1rem"
      }}
    >
      {repoList.map((repo) => (
        <div
          key={repo.repositoryId}
          onClick={() => toggleRepo(repo.repositoryId)}
          style={{
            padding: "1rem",
            border: "1px solid #e5e7eb",
            borderRadius: "0.5rem",
            cursor: "pointer",
            transition: "all 0.2s",
            backgroundColor: selected.has(repo.repositoryId)
              ? "#f0f9ff"
              : "#ffffff",
            borderColor: selected.has(repo.repositoryId)
              ? "#0ea5e9"
              : "#e5e7eb",
            boxShadow: selected.has(repo.repositoryId)
              ? "0 0 0 2px rgba(14, 165, 233, 0.1)"
              : "none"
          }}
        >
          <div style={{ display: "flex", alignItems: "flex-start", gap: "0.75rem" }}>
            <input
              type="checkbox"
              checked={selected.has(repo.repositoryId)}
              onChange={() => toggleRepo(repo.repositoryId)}
              onClick={(e) => e.stopPropagation()}
              style={{ marginTop: "0.25rem", cursor: "pointer" }}
            />
            <div style={{ flex: 1, minWidth: 0 }}>
              <h4
                style={{
                  margin: "0 0 0.5rem 0",
                  fontSize: "0.95rem",
                  fontWeight: "600",
                  wordBreak: "break-word"
                }}
              >
                {repo.repoFullName}
              </h4>
              {repo.primaryLanguage && (
                <span
                  style={{
                    display: "inline-block",
                    backgroundColor: "#f3f4f6",
                    padding: "0.25rem 0.5rem",
                    borderRadius: "0.25rem",
                    fontSize: "0.8rem",
                    color: "#374151"
                  }}
                >
                  {repo.primaryLanguage}
                </span>
              )}
            </div>
          </div>
        </div>
      ))}
    </div>
  );

  return (
    <div className="screen-shell">
      <div className="screen-hero">
        <p className="eyebrow">GitHub 저장소</p>
        <div className="screen-heading">
          <h1>저장소 선택</h1>
          <p>분석할 저장소를 선택하세요. ({selected.size}개 선택됨)</p>
        </div>
      </div>

      {repos.length === 0 ? (
        <StatePanel message="연결된 저장소가 없습니다." />
      ) : (
        <>
          {ownedRepos.length > 0 && (
            <div className="panel" style={{ marginBottom: "2rem" }}>
              <h2 style={{ marginTop: 0, marginBottom: "1.5rem", fontSize: "1.1rem", fontWeight: "600" }}>
                내 저장소 ({ownedRepos.length})
              </h2>
              {renderRepoGrid(ownedRepos)}
            </div>
          )}

          {contributedRepos.length > 0 && (
            <div className="panel" style={{ marginBottom: "2rem" }}>
              <h2 style={{ marginTop: 0, marginBottom: "1.5rem", fontSize: "1.1rem", fontWeight: "600" }}>
                기여한 저장소 ({contributedRepos.length})
              </h2>
              {renderRepoGrid(contributedRepos)}
            </div>
          )}
        </>
      )}

      <div className="action-row" aria-label="저장소 분석">
        <button
          className="action-link primary"
          disabled={selected.size === 0}
          onClick={handleAnalyze}
        >
          분석 실행 ({selected.size}개)
        </button>
        <button
          className="action-link"
          onClick={reconnect}
          title="현재 GitHub 계정을 변경하려면 클릭하세요"
        >
          다른 GitHub 계정으로 연결
        </button>
      </div>
    </div>
  );
}
