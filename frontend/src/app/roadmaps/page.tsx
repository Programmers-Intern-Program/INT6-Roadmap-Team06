"use client";

import Link from "next/link";
import { useEffect, useState } from "react";

import { AuthRequiredPanel } from "@/components/auth-required-panel";
import { StatePanel } from "@/components/state-panel";
import { listRoadmaps } from "@/features/roadmap/api";
import type { RoadmapSummary } from "@/features/roadmap/types";
import { ApiError } from "@/lib/api";
import { isUnauthorizedError } from "@/lib/auth";

type State =
  | { status: "loading" }
  | { status: "auth-required" }
  | { status: "ready"; items: RoadmapSummary[] }
  | { status: "error"; message: string };

export default function RoadmapsPage() {
  const [state, setState] = useState<State>({ status: "loading" });

  useEffect(() => {
    let cancelled = false;

    listRoadmaps()
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
    return <StatePanel message="로드맵 목록을 불러오는 중입니다." />;
  }

  if (state.status === "auth-required") {
    return <AuthRequiredPanel redirectPath="/roadmaps" />;
  }

  if (state.status === "error") {
    return <StatePanel message={state.message} tone="danger" />;
  }

  if (state.items.length === 0) {
    return (
      <section className="screen-shell">
        <StatePanel message="아직 생성된 로드맵이 없습니다." />
        <div className="action-row" aria-label="로드맵 다음 행동">
          <Link className="action-link primary" href="/roadmaps/new">
            로드맵 생성
          </Link>
          <Link className="action-link" href="/diagnoses">
            진단 결과 보기
          </Link>
        </div>
      </section>
    );
  }

  return (
    <section className="screen-shell">
      <header className="screen-header">
        <h2 className="screen-title">학습 로드맵</h2>
        <p className="screen-description">
          시점별 로드맵 버전을 모아 봅니다. 재계획으로 생긴 버전은 별도 카드로 보여요.
        </p>
      </header>
      <ul className="card-grid">
        {state.items.map((item) => (
          <li key={item.roadmapId} className="result-card">
            <header className="result-card-header">
              <span className="result-card-meta">
                {formatDate(item.createdAt)} · {item.totalWeeks}주 분량
              </span>
              <span className="result-card-badge" data-tone="accent">
                v{item.version}
              </span>
            </header>
            <p className="result-card-body">{item.summary}</p>
            <footer className="result-card-footer">
              <Link
                className="result-card-link"
                href={`/diagnoses/${item.diagnosisId}`}
              >
                ↳ 진단 #{item.diagnosisId} 기반
              </Link>
              <div className="result-card-actions">
                <Link className="action-link" href={`/roadmaps/${item.roadmapId}`}>
                  상세 보기
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
  return "로드맵 목록을 불러오지 못했습니다.";
}
