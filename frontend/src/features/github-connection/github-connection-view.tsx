"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { ApiError, githubConnectionOAuthUrl } from "@/lib/api";
import { getRepositories, runAnalysis } from "@/features/github-connection/api";
import type { Repository } from "@/features/github-connection/types";
import { StatePanel } from "@/components/state-panel";

const CONNECTION_ID_KEY = "githubConnectionId";

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

export function GithubConnectionView() {
  const router = useRouter();
  const [state, setState] = useState<ViewState>({ status: "loading-repos" });
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [connectUrl, setConnectUrl] = useState("");

  useEffect(() => {
    setConnectUrl(githubConnectionOAuthUrl());
    const connectionId = localStorage.getItem(CONNECTION_ID_KEY);
    if (!connectionId) {
      setState({ status: "disconnected" });
      return;
    }
    let cancelled = false;
    getRepositories(connectionId)
      .then((repos) => {
        if (!cancelled) setState({ status: "ready", repos: repos.repositories });
      })
      .catch((err) => {
        if (cancelled) return;
        if (err instanceof ApiError && err.status === 404) {
          localStorage.removeItem(CONNECTION_ID_KEY);
          setState({ status: "disconnected" });
        } else {
          setState({ status: "error", message: getErrorMessage(err) });
        }
      });
    return () => { cancelled = true; };
  }, []);

  function toggleRepo(id: string) {
    setSelected((prev) => {
      const next = new Set(prev);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });
  }

  async function handleAnalyze() {
    const connectionId = localStorage.getItem(CONNECTION_ID_KEY);
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
          <button onClick={() => setState({ status: "disconnected" })}>
            다시 연결하기
          </button>
        </p>
      </div>
    );
  }

  if (state.status === "disconnected") {
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
            localStorage.removeItem(CONNECTION_ID_KEY);
            setState({ status: "disconnected" });
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
