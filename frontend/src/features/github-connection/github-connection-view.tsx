"use client";

import { useEffect, useState, useSyncExternalStore } from "react";
import { useRouter } from "next/navigation";
import { ApiError, githubConnectionOAuthUrl } from "@/lib/api";
import { getRepositories, runAnalysis } from "@/features/github-connection/api";
import type { Repository } from "@/features/github-connection/types";
import { StatePanel } from "@/components/state-panel";

const CONNECTION_ID_KEY = "githubConnectionId";
const CONNECTION_ID_CHANGE_EVENT = "githubConnectionIdChange";
const SELECTED_REPOS_KEY = "githubSelectedRepos";

type ViewState =
  | { status: "disconnected" }
  | { status: "loading-repos" }
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

  return (
    <div>
      <h1>저장소 선택</h1>
      <p>분석할 저장소를 선택하세요. ({selected.size}개 선택됨)</p>

      {repos.length === 0 ? (
        <StatePanel message="연결된 저장소가 없습니다." />
      ) : (
        <ul style={{ listStyle: "none", padding: 0 }}>
          {repos.map((repo) => (
            <li key={repo.repositoryId} style={{ marginBottom: "0.5rem" }}>
              <label style={{ display: "flex", alignItems: "center", gap: "0.5rem", cursor: "pointer" }}>
                <input
                  type="checkbox"
                  checked={selected.has(repo.repositoryId)}
                  onChange={() => toggleRepo(repo.repositoryId)}
                />
                <span>{repo.repoFullName}</span>
                {repo.primaryLanguage && (
                  <span style={{ fontSize: "0.75rem", color: "#888" }}>
                    {repo.primaryLanguage}
                  </span>
                )}
              </label>
            </li>
          ))}
        </ul>
      )}

      <button
        className="btn-primary"
        disabled={selected.size === 0}
        onClick={handleAnalyze}
      >
        분석 실행
      </button>

      <p style={{ marginTop: "1rem" }}>
        <button
          onClick={() => {
            clearConnectionId();
            setSelected(new Set());
          }}
          style={{ fontSize: "0.875rem", color: "#888" }}
        >
          다른 계정으로 연결
        </button>
      </p>
    </div>
  );
}
