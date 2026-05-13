"use client";

import Link from "next/link";
import { useEffect, useState } from "react";

import { AuthRequiredPanel } from "@/components/auth-required-panel";
import { StatePanel } from "@/components/state-panel";
import { listDiagnoses } from "@/features/diagnosis/api";
import { currentLevelLabels } from "@/features/diagnosis/labels";
import type { DiagnosisSummary } from "@/features/diagnosis/types";
import { ApiError } from "@/lib/api";
import { isUnauthorizedError } from "@/lib/auth";

type State =
  | { status: "loading" }
  | { status: "auth-required" }
  | { status: "ready"; items: DiagnosisSummary[] }
  | { status: "error"; message: string };

export default function DiagnosesPage() {
  const [state, setState] = useState<State>({ status: "loading" });

  useEffect(() => {
    let cancelled = false;

    listDiagnoses()
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
    return <StatePanel message="진단 결과를 불러오는 중입니다." />;
  }

  if (state.status === "auth-required") {
    return <AuthRequiredPanel redirectPath="/diagnoses" />;
  }

  if (state.status === "error") {
    return <StatePanel message={state.message} tone="danger" />;
  }

  if (state.items.length === 0) {
    return (
      <section className="screen-shell">
        <StatePanel message="아직 생성된 진단 결과가 없습니다." />
        <div className="action-row" aria-label="진단 다음 행동">
          <Link className="action-link primary" href="/diagnoses/new">
            진단 생성
          </Link>
          <Link className="action-link" href="/github/analysis">
            분석 보정
          </Link>
        </div>
      </section>
    );
  }

  return (
    <section className="screen-shell">
      <header className="screen-header">
        <h2 className="screen-title">역량 진단 결과</h2>
        <p className="screen-description">
          시점별 진단 결과를 모아 봅니다. 최신 분석을 기반으로 다시 진단할 수 있어요.
        </p>
      </header>
      <div className="action-row" aria-label="진단 다음 행동">
        <Link className="action-link primary" href="/github/analysis">
          + 새 진단 만들기
        </Link>
      </div>
      <p className="screen-helper-note">
        새 진단을 만들려면 먼저 GitHub 분석 결과 페이지에서 분석을 선택한 뒤 [진단 생성]을 눌러주세요.
      </p>
      <ul className="card-grid">
        {state.items.map((item) => (
          <li key={item.diagnosisId} className="result-card">
            <header className="result-card-header">
              <span className="result-card-meta">
                {formatDate(item.createdAt)} · v{item.version}
              </span>
              <span className="result-card-badge" data-tone="accent">
                {currentLevelLabels[item.currentLevel] ?? item.currentLevel}
              </span>
            </header>
            <p className="result-card-body">{item.summary}</p>
            <footer className="result-card-footer">
              <Link
                className="result-card-link"
                href={`/github/analysis?githubAnalysisId=${item.githubAnalysisId}`}
              >
                ↳ GitHub 분석 #{item.githubAnalysisId} 기반
              </Link>
              <div className="result-card-actions">
                <Link className="action-link" href={`/diagnoses/${item.diagnosisId}`}>
                  상세 보기
                </Link>
                <Link
                  className="action-link primary"
                  href={`/roadmaps/new?diagnosisId=${item.diagnosisId}`}
                >
                  로드맵 만들기
                </Link>
                <Link
                  className="action-link"
                  href={`/diagnoses/new?githubAnalysisId=${item.githubAnalysisId}`}
                >
                  재진단
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
  return "진단 목록을 불러오지 못했습니다.";
}
