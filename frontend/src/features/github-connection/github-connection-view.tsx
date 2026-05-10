"use client";

import { useEffect, useState, useSyncExternalStore } from "react";
import { useRouter } from "next/navigation";
import { ApiError, githubConnectionOAuthUrl } from "@/lib/api";
import { isUnauthorizedError } from "@/lib/auth";
import { getRepositories, runAnalysis } from "@/features/github-connection/api";
import type { Repository } from "@/features/github-connection/types";
import { AuthRequiredPanel } from "@/components/auth-required-panel";
import { StatePanel } from "@/components/state-panel";

const CONNECTION_ID_KEY = "githubConnectionId";
const CONNECTION_ID_CHANGE_EVENT = "githubConnectionIdChange";
const SELECTED_REPOS_KEY = "githubSelectedRepos";

type ViewState =
  | { status: "disconnected" }
  | { status: "loading-repos" }
  | { status: "auth-required" }
  | { status: "ready"; repos: Repository[] }
  | { status: "analyzing" }
  | { status: "error"; message: string };

function getErrorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  return "오류가 발생했습니다. 다시 시도해주세요.";
}

function getConnectionIdSnapshot() {
  if (typeof window === "undefined") return null;
  return localStorage.getItem(CONNECTION_ID_KEY);
}

function subscribeToConnectionId(onChange: () => void) {
  if (typeof window === "undefined") return () => {};

  window.addEventListener("storage", onChange);
  window.addEventListener(CONNECTION_ID_CHANGE_EVENT, onChange);

  return () => {
    window.removeEventListener("storage", onChange);
    window.removeEventListener(CONNECTION_ID_CHANGE_EVENT, onChange);
  };
}

function clearConnectionId() {
  localStorage.removeItem(CONNECTION_ID_KEY);
  localStorage.removeItem(SELECTED_REPOS_KEY);
  window.dispatchEvent(new Event(CONNECTION_ID_CHANGE_EVENT));
}

export function GithubConnectionView() {
  const router = useRouter();
  const connectionId = useSyncExternalStore(
    subscribeToConnectionId,
    getConnectionIdSnapshot,
    () => null
  );
  const [state, setState] = useState<ViewState>({ status: "loading-repos" });
  const [selected, setSelected] = useState<Set<string>>(() => {
    if (typeof window === "undefined") return new Set();
    try {
      const saved = localStorage.getItem(SELECTED_REPOS_KEY);
      return saved ? new Set(JSON.parse(saved)) : new Set();
    } catch { return new Set(); }
  });
  const [connectUrl] = useState(() => githubConnectionOAuthUrl());

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
          clearConnectionId();
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
      localStorage.setItem(SELECTED_REPOS_KEY, JSON.stringify(Array.from(next)));
      return next;
    });
  }

  async function handleAnalyze() {
    if (!connectionId || selected.size === 0) return;
    setState({ status: "analyzing" });
    try {
      const ids = Array.from(selected);
      const result = await runAnalysis(connectionId, ids, ids);
      router.push(`/github/analysis?githubAnalysisId=${result.githubAnalysisId}`);
    } catch (err) {
      if (isUnauthorizedError(err)) {
        setState({ status: "auth-required" });
        return;
      }
      setState({ status: "error", message: getErrorMessage(err) });
    }
  }

  if (!connectionId || state.status === "disconnected") {
    return (
      <div>
        <h1>GitHub 연동</h1>
        <p>GitHub 저장소를 연결하고 분석을 시작하세요.</p>
        <a href={connectUrl}>
          <button className="btn-primary" disabled={!connectUrl}>
            GitHub 연결하기
          </button>
        </a>
      </div>
    );
  }

  if (state.status === "loading-repos") {
    return <StatePanel message="저장소 목록을 불러오는 중..." />;
  }

  if (state.status === "analyzing") {
    return <StatePanel message="GitHub 분석 중입니다. 잠시 기다려주세요..." />;
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
          <button onClick={clearConnectionId}>
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
          onClick={() => {
            clearConnectionId();
            setSelected(new Set());
          }}
        >
          다른 계정으로 연결
        </button>
      </div>
    </div>
  );
}
