"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";

import { AuthRequiredPanel } from "@/components/auth-required-panel";
import { StatePanel } from "@/components/state-panel";
import { createSession, getActiveSession } from "@/features/coach/api";
import {
  classifyCoachError,
  type CoachErrorInfo
} from "@/features/coach/coach-errors";
import { ApiError } from "@/lib/api";
import { isUnauthorizedError } from "@/lib/auth";

type EntryState =
  | { status: "loading" }
  | { status: "auth-required" }
  | { status: "snapshot-missing"; info: CoachErrorInfo }
  | { status: "error"; info: CoachErrorInfo };

export function CoachEntryView() {
  const router = useRouter();
  const [state, setState] = useState<EntryState>({ status: "loading" });

  useEffect(() => {
    let cancelled = false;
    const controller = new AbortController();

    async function run() {
      try {
        const active = await getActiveSession(controller.signal);
        if (cancelled) return;
        router.replace(`/coach/sessions/${active.sessionId}`);
      } catch (error) {
        if (cancelled || controller.signal.aborted) return;

        if (isUnauthorizedError(error)) {
          setState({ status: "auth-required" });
          return;
        }

        if (error instanceof ApiError && error.code === "SESSION_NOT_FOUND") {
          try {
            const created = await createSession(controller.signal);
            if (cancelled) return;
            router.replace(`/coach/sessions/${created.sessionId}`);
            return;
          } catch (createError) {
            if (cancelled || controller.signal.aborted) return;

            if (isUnauthorizedError(createError)) {
              setState({ status: "auth-required" });
              return;
            }

            const info = classifyCoachError(createError);
            if (info.kind === "SNAPSHOT_NOT_FOUND") {
              setState({ status: "snapshot-missing", info });
              return;
            }
            setState({ status: "error", info });
            return;
          }
        }

        const info = classifyCoachError(error);
        setState({ status: "error", info });
      }
    }

    run();

    return () => {
      cancelled = true;
      controller.abort();
    };
  }, [router]);

  if (state.status === "auth-required") {
    return <AuthRequiredPanel redirectPath="/coach" />;
  }

  if (state.status === "snapshot-missing") {
    return (
      <section className="screen-shell">
        <StatePanel
          message={`${state.info.title}${state.info.detail ? ` ${state.info.detail}` : ""}`}
          tone="neutral"
        />
        <div className="action-row" aria-label="코치 사용 준비">
          <Link className="action-link primary" href="/profile">
            프로필 작성
          </Link>
          <Link className="action-link" href="/roadmaps/new">
            로드맵 생성
          </Link>
        </div>
      </section>
    );
  }

  if (state.status === "error") {
    return (
      <section className="screen-shell">
        <StatePanel
          message={`${state.info.title}${state.info.detail ? ` ${state.info.detail}` : ""}`}
          tone="danger"
        />
        {state.info.traceId ? (
          <p className="coach-trace-id">traceId: {state.info.traceId}</p>
        ) : null}
      </section>
    );
  }

  return <StatePanel message="코치 세션을 준비하는 중입니다." />;
}
