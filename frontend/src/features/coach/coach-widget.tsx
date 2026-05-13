"use client";

import { useCallback, useEffect, useState } from "react";
import { usePathname } from "next/navigation";

import { CoachChatView } from "@/features/coach/coach-chat-view";
import {
  createSession,
  getActiveSession
} from "@/features/coach/api";
import { classifyCoachError } from "@/features/coach/coach-errors";
import { ApiError } from "@/lib/api";
import { isUnauthorizedError } from "@/lib/auth";

const STORAGE_KEY = "coach-widget-open";

function getInitialOpen() {
  if (typeof window === "undefined") return false;
  try {
    return window.localStorage.getItem(STORAGE_KEY) === "1";
  } catch {
    return false;
  }
}

type SessionState =
  | { status: "idle" }
  | { status: "loading" }
  | { status: "auth-required" }
  | { status: "ready"; sessionId: string }
  | { status: "error"; message: string };

export function CoachWidget() {
  const [open, setOpen] = useState(getInitialOpen);
  const [session, setSession] = useState<SessionState>(() =>
    getInitialOpen() ? { status: "loading" } : { status: "idle" }
  );
  const pathname = usePathname();

  // /coach 경로에서는 위젯 숨김 (full-page 코치와 중복 방지)
  const onCoachPage = pathname?.startsWith("/coach") ?? false;

  // open=true가 되면 세션 자동 확보.
  // ⚠ session.status를 deps에 넣으면 setSession이 effect를 재실행시켜
  //   매번 cleanup의 controller.abort()가 진행 중인 fetch를 취소하는
  //   무한 abort 루프가 발생한다. open/onCoachPage 변경에만 반응한다.
  useEffect(() => {
    if (!open || onCoachPage) return;

    let cancelled = false;
    const controller = new AbortController();

    (async () => {
      try {
        const active = await getActiveSession(controller.signal);
        if (cancelled) return;
        setSession({ status: "ready", sessionId: active.sessionId });
      } catch (error) {
        if (cancelled || controller.signal.aborted) return;
        if (isUnauthorizedError(error)) {
          setSession({ status: "auth-required" });
          return;
        }
        if (error instanceof ApiError && error.code === "SESSION_NOT_FOUND") {
          try {
            const created = await createSession(controller.signal);
            if (cancelled) return;
            setSession({ status: "ready", sessionId: created.sessionId });
            return;
          } catch (createError) {
            if (cancelled || controller.signal.aborted) return;
            if (isUnauthorizedError(createError)) {
              setSession({ status: "auth-required" });
              return;
            }
            const info = classifyCoachError(createError);
            setSession({ status: "error", message: info.title });
            return;
          }
        }
        const info = classifyCoachError(error);
        setSession({ status: "error", message: info.title });
      }
    })();

    return () => {
      cancelled = true;
      controller.abort();
    };
  }, [open, onCoachPage]);

  const persistOpen = useCallback((next: boolean) => {
    setOpen(next);
    setSession(next ? { status: "loading" } : { status: "idle" });
    try {
      window.localStorage.setItem(STORAGE_KEY, next ? "1" : "0");
    } catch {
      // ignore
    }
  }, []);

  const handleOpen = useCallback(() => persistOpen(true), [persistOpen]);
  const handleClose = useCallback(() => persistOpen(false), [persistOpen]);

  // /coach 페이지에서는 위젯 자체를 렌더하지 않음
  if (onCoachPage) return null;

  if (!open) {
    return (
      <button
        type="button"
        className="coach-widget-launcher"
        onClick={handleOpen}
        aria-label="코치 위젯 열기"
        title="코치에게 물어보기"
      >
        <span className="coach-widget-launcher-icon" aria-hidden="true">💬</span>
        <span className="coach-widget-launcher-label">코치</span>
      </button>
    );
  }

  return (
    <aside className="coach-widget-panel" aria-label="코치 위젯">
      {session.status === "loading" || session.status === "idle" ? (
        <WidgetLoadingPanel onClose={handleClose} message="코치 세션을 준비하는 중…" />
      ) : session.status === "auth-required" ? (
        <WidgetLoadingPanel
          onClose={handleClose}
          message="로그인이 필요합니다. /coach 페이지에서 로그인 후 다시 열어주세요."
        />
      ) : session.status === "error" ? (
        <WidgetLoadingPanel onClose={handleClose} message={session.message} tone="danger" />
      ) : (
        <CoachChatView
          sessionId={session.sessionId}
          mode="widget"
          onClose={handleClose}
        />
      )}
    </aside>
  );
}

function WidgetLoadingPanel({
  message,
  onClose,
  tone
}: {
  message: string;
  onClose: () => void;
  tone?: "danger";
}) {
  return (
    <div className="coach-widget-empty" data-tone={tone}>
      <div className="coach-widget-empty-header">
        <strong>코치</strong>
        <button
          type="button"
          className="coach-widget-close"
          onClick={onClose}
          aria-label="위젯 닫기"
        >
          ✕
        </button>
      </div>
      <p>{message}</p>
    </div>
  );
}
