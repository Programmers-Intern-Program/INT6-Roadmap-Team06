"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";

import { createSession, getSessions } from "@/features/coach/api";
import { classifyCoachError } from "@/features/coach/coach-errors";
import type { CoachSessionSummary } from "@/features/coach/types";
import { isUnauthorizedError } from "@/lib/auth";

type SidebarProps = {
  activeSessionId: string;
};

export function CoachSessionsSidebar({ activeSessionId }: SidebarProps) {
  const router = useRouter();
  const [sessions, setSessions] = useState<CoachSessionSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    let cancelled = false;
    getSessions(controller.signal)
      .then((data) => {
        if (!cancelled) setSessions(data);
      })
      .catch((err) => {
        if (cancelled || (err instanceof DOMException && err.name === "AbortError")) return;
        if (isUnauthorizedError(err)) {
          setError("로그인이 필요합니다.");
          return;
        }
        const info = classifyCoachError(err);
        setError(info.title);
      });
    return () => {
      cancelled = true;
      controller.abort();
    };
  }, [activeSessionId]);

  return (
    <aside className="coach-sidebar" aria-label="Coach 세션 목록">
      <header className="coach-sidebar-header">
        <h3>세션</h3>
        <button
          type="button"
          className="coach-sidebar-new"
          disabled={creating}
          onClick={async () => {
            setCreating(true);
            try {
              const created = await createSession();
              router.push(`/coach/sessions/${created.sessionId}`);
            } catch (err) {
              setError(classifyCoachError(err).title);
              setCreating(false);
            }
          }}
        >
          {creating ? "생성 중…" : "+ 새 세션"}
        </button>
      </header>

      {error ? (
        <p className="coach-sidebar-empty" data-tone="danger">{error}</p>
      ) : sessions === null ? (
        <p className="coach-sidebar-empty">불러오는 중…</p>
      ) : sessions.length === 0 ? (
        <p className="coach-sidebar-empty">아직 세션이 없습니다.</p>
      ) : (
        <ul className="coach-sidebar-list">
          {sessions.map((session) => {
            const isActive = session.sessionId === activeSessionId;
            const isClosed = session.status === "CLOSED";
            return (
              <li
                key={session.sessionId}
                className="coach-sidebar-item"
                data-active={isActive ? "true" : undefined}
                data-status={session.status.toLowerCase()}
              >
                <Link
                  href={`/coach/sessions/${session.sessionId}`}
                  className="coach-sidebar-link"
                >
                  <div className="coach-sidebar-row">
                    <span className="coach-sidebar-label">
                      {isClosed ? "종료됨" : "활성"}
                    </span>
                    <span className="coach-sidebar-version">
                      v{session.profileVersion}/{session.roadmapVersion}
                    </span>
                  </div>
                  <div className="coach-sidebar-date">
                    {formatStartedAt(session.startedAt)}
                  </div>
                </Link>
              </li>
            );
          })}
        </ul>
      )}
    </aside>
  );
}

function formatStartedAt(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;
  return date.toLocaleString("ko-KR", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false
  });
}
