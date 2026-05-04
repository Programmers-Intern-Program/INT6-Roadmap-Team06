"use client";

import Link from "next/link";
import { useState, type FormEvent } from "react";

import { StatePanel } from "@/components/state-panel";
import {
  connectGithub,
  getGithubRepositories
} from "@/features/github-connection/api";
import type {
  GithubConnectionResponse,
  GithubRepository
} from "@/features/github-connection/types";
import { ApiError } from "@/lib/api";

type RepositorySelection = {
  coreRepositoryIds: string[];
  selectedRepositoryIds: string[];
};

export function GithubConnectionView() {
  const [connection, setConnection] = useState<GithubConnectionResponse | null>(
    null
  );
  const [repositories, setRepositories] = useState<GithubRepository[]>([]);
  const [selection, setSelection] = useState<RepositorySelection>({
    coreRepositoryIds: [],
    selectedRepositoryIds: []
  });
  const [isConnecting, setIsConnecting] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [connectionMessage, setConnectionMessage] = useState<string | null>(null);

  async function handleConnectSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    const formData = new FormData(event.currentTarget);
    const authorizationCode = getStringFormValue(formData, "authorizationCode");

    if (!authorizationCode) {
      setLoadError("GitHub authorization code를 입력해 주세요.");
      return;
    }

    setIsConnecting(true);
    setLoadError(null);
    setConnectionMessage(null);

    try {
      const connected = await connectGithub({ authorizationCode });
      const repositoryList = await getGithubRepositories(
        connected.githubConnectionId
      );

      setConnection(connected);
      setRepositories(repositoryList.repositories);
      setSelection({ coreRepositoryIds: [], selectedRepositoryIds: [] });
      setConnectionMessage(
        `${connected.githubLogin} 계정의 저장소 ${repositoryList.repositories.length}개를 불러왔습니다.`
      );
    } catch (error) {
      setLoadError(getErrorMessage(error));
    } finally {
      setIsConnecting(false);
    }
  }

  function toggleSelectedRepository(repositoryId: string) {
    setSelection((current) => {
      const selected = new Set(current.selectedRepositoryIds);
      const core = new Set(current.coreRepositoryIds);

      if (selected.has(repositoryId)) {
        selected.delete(repositoryId);
        core.delete(repositoryId);
      } else {
        selected.add(repositoryId);
      }

      return {
        coreRepositoryIds: [...core],
        selectedRepositoryIds: [...selected]
      };
    });
  }

  function toggleCoreRepository(repositoryId: string) {
    setSelection((current) => {
      if (!current.selectedRepositoryIds.includes(repositoryId)) {
        return current;
      }

      const core = new Set(current.coreRepositoryIds);

      if (core.has(repositoryId)) {
        core.delete(repositoryId);
      } else {
        core.add(repositoryId);
      }

      return {
        ...current,
        coreRepositoryIds: [...core]
      };
    });
  }

  return (
    <section className="github-connection-page" aria-labelledby="github-title">
      <div className="screen-hero">
        <p className="eyebrow">v1 필수</p>
        <div className="screen-heading">
          <h1 id="github-title">GitHub 연동 / 저장소 선택</h1>
          <p>GitHub authorization code로 계정을 연결하고 분석할 저장소를 고릅니다.</p>
        </div>
        <div className="action-row" aria-label="GitHub 관련 화면 이동">
          <Link className="action-link" href="/profile">
            프로필로 돌아가기
          </Link>
          <Link className="action-link" href="/github/analysis">
            최근 분석 보기
          </Link>
        </div>
      </div>

      <form className="panel github-connection-form" onSubmit={handleConnectSubmit}>
        <div className="github-connection-heading">
          <h2>GitHub 연결</h2>
          <p>GitHub OAuth authorization code를 입력하면 저장소 목록을 불러옵니다.</p>
        </div>
        <label>
          <span>Authorization code</span>
          <input
            autoComplete="off"
            name="authorizationCode"
            placeholder="GitHub OAuth redirect URL의 code 값"
          />
        </label>
        <div className="github-connection-actions">
          <div>
            {loadError ? (
              <p className="github-connection-error">{loadError}</p>
            ) : null}
            {connectionMessage ? (
              <p className="github-connection-success">{connectionMessage}</p>
            ) : null}
          </div>
          <button disabled={isConnecting} type="submit">
            {isConnecting ? "연결 중" : "저장소 불러오기"}
          </button>
        </div>
      </form>

      {connection ? (
        <section className="panel github-connection-summary">
          <h2>연결 상태</h2>
          <dl>
            <div>
              <dt>계정</dt>
              <dd>{connection.githubLogin}</dd>
            </div>
            <div>
              <dt>연결 ID</dt>
              <dd>{connection.githubConnectionId}</dd>
            </div>
            <div>
              <dt>연결 시각</dt>
              <dd>{formatDateTime(connection.connectedAt)}</dd>
            </div>
          </dl>
        </section>
      ) : null}

      {connection && repositories.length === 0 ? (
        <StatePanel
          className="github-connection-state-panel"
          message="표시할 저장소가 없습니다."
        />
      ) : null}

      {repositories.length > 0 ? (
        <RepositorySelectionPanel
          onCoreToggle={toggleCoreRepository}
          onSelectedToggle={toggleSelectedRepository}
          repositories={repositories}
          selection={selection}
        />
      ) : null}
    </section>
  );
}

function RepositorySelectionPanel({
  onCoreToggle,
  onSelectedToggle,
  repositories,
  selection
}: {
  onCoreToggle: (repositoryId: string) => void;
  onSelectedToggle: (repositoryId: string) => void;
  repositories: GithubRepository[];
  selection: RepositorySelection;
}) {
  return (
    <section className="panel github-repository-section">
      <div className="github-connection-heading">
        <h2>저장소 선택</h2>
        <p>분석 대상 저장소와 LLM 요약에 사용할 핵심 저장소를 선택합니다.</p>
      </div>
      <div className="github-repository-list">
        {repositories.map((repository) => {
          const isSelected = selection.selectedRepositoryIds.includes(
            repository.repositoryId
          );
          const isCore = selection.coreRepositoryIds.includes(
            repository.repositoryId
          );

          return (
            <article className="github-repository-card" key={repository.repositoryId}>
              <div>
                <h3>{repository.repoFullName}</h3>
                <a href={repository.repoUrl} rel="noreferrer" target="_blank">
                  저장소 열기
                </a>
              </div>
              <dl>
                <div>
                  <dt>언어</dt>
                  <dd>{repository.primaryLanguage ?? "미확인"}</dd>
                </div>
                <div>
                  <dt>기본 브랜치</dt>
                  <dd>{repository.defaultBranch ?? "미확인"}</dd>
                </div>
              </dl>
              <div className="github-repository-options">
                <label>
                  <input
                    checked={isSelected}
                    onChange={() => onSelectedToggle(repository.repositoryId)}
                    type="checkbox"
                  />
                  <span>분석 대상</span>
                </label>
                <label>
                  <input
                    checked={isCore}
                    disabled={!isSelected}
                    onChange={() => onCoreToggle(repository.repositoryId)}
                    type="checkbox"
                  />
                  <span>핵심 repo</span>
                </label>
              </div>
            </article>
          );
        })}
      </div>
    </section>
  );
}

function getStringFormValue(formData: FormData, key: string) {
  const value = formData.get(key);

  return typeof value === "string" ? value.trim() : "";
}

function getErrorMessage(error: unknown) {
  if (error instanceof ApiError) {
    return error.message;
  }

  return "GitHub 저장소를 불러오지 못했습니다.";
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    dateStyle: "medium",
    timeStyle: "short"
  }).format(new Date(value));
}
