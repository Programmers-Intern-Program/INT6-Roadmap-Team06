"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";

import { StatePanel } from "@/components/state-panel";
import { getDashboard } from "@/features/dashboard/api";
import { ApiError, githubLoginUrl } from "@/lib/api";

type DiagnosesPageState =
  | { status: "loading" }
  | { status: "ready"; githubAnalysisId: string | null }
  | { status: "error"; message: string };

export default function DiagnosesPage() {
  const router = useRouter();
  const [state, setState] = useState<DiagnosesPageState>({
    status: "loading"
  });

  useEffect(() => {
    let ignore = false;

    getDashboard()
      .then((dashboard) => {
        if (ignore) return;
        if (dashboard.diagnosis?.diagnosisId) {
          router.replace(`/diagnoses/${dashboard.diagnosis.diagnosisId}`);
          return;
        }
        setState({
          githubAnalysisId: dashboard.githubAnalysis?.githubAnalysisId ?? null,
          status: "ready"
        });
      })
      .catch((error) => {
        if (ignore) return;
        if (error instanceof ApiError && error.status === 401) {
          window.location.href = githubLoginUrl("/diagnoses");
          return;
        }
        setState({ message: getErrorMessage(error), status: "error" });
      });

    return () => {
      ignore = true;
    };
  }, [router]);

  if (state.status === "error") {
    return (
      <StatePanel
        className="diagnosis-state-panel"
        message={state.message}
        tone="danger"
      />
    );
  }

  if (state.status === "ready") {
    const hasGithubAnalysis = Boolean(state.githubAnalysisId);

    return (
      <section className="screen-shell">
        <StatePanel
          className="diagnosis-state-panel"
          message={
            hasGithubAnalysis
              ? "아직 생성된 진단 결과가 없습니다."
              : "진단에 사용할 GitHub 분석 결과가 없습니다."
          }
        />
        <div className="action-row" aria-label="진단 다음 행동">
          {hasGithubAnalysis ? (
            <Link
              className="action-link primary"
              href={`/diagnoses/new?githubAnalysisId=${state.githubAnalysisId}`}
            >
              진단 생성
            </Link>
          ) : (
            <Link className="action-link primary" href="/github">
              GitHub 연동
            </Link>
          )}
          <Link className="action-link" href="/github/analysis">
            분석 보정
          </Link>
        </div>
      </section>
    );
  }

  return (
    <StatePanel
      message="최근 진단 결과를 불러오는 중입니다."
    />
  );
}

function getErrorMessage(error: unknown) {
  if (error instanceof ApiError) {
    return error.message;
  }

  return "최근 진단 결과를 불러오지 못했습니다.";
}
