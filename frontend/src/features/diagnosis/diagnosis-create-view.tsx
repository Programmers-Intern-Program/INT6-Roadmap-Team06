"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { AuthRequiredPanel } from "@/components/auth-required-panel";
import { ApiError } from "@/lib/api";
import { isUnauthorizedError } from "@/lib/auth";
import { createDiagnosis } from "@/features/diagnosis/api";
import { getMyProfile } from "@/features/profile/api";
import { StatePanel } from "@/components/state-panel";

type CreateState =
  | { status: "loading" }
  | { status: "auth-required" }
  | { status: "ready"; profileId: string }
  | { status: "submitting" }
  | { status: "error"; message: string };

function getErrorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  return "오류가 발생했습니다. 다시 시도해주세요.";
}

type Props = {
  githubAnalysisId: string;
};

export function DiagnosisCreateView({ githubAnalysisId }: Props) {
  const router = useRouter();
  const [state, setState] = useState<CreateState>({ status: "loading" });
  const loaded = useRef(false);

  useEffect(() => {
    if (loaded.current) return;
    loaded.current = true;
    getMyProfile()
      .then((profile) => setState({ status: "ready", profileId: profile.profileId }))
      .catch((err) => {
        if (isUnauthorizedError(err)) {
          setState({ status: "auth-required" });
          return;
        }
        setState({ status: "error", message: getErrorMessage(err) });
      });
  }, []);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (state.status !== "ready") return;
    setState({ status: "submitting" });
    try {
      const diagnosis = await createDiagnosis({ profileId: state.profileId, githubAnalysisId });
      router.push(`/diagnoses/${diagnosis.diagnosisId}`);
    } catch (err) {
      if (isUnauthorizedError(err)) {
        setState({ status: "auth-required" });
        return;
      }
      setState({ status: "error", message: getErrorMessage(err) });
    }
  }

  if (state.status === "loading") {
    return <StatePanel message="프로필을 불러오는 중..." />;
  }

  if (state.status === "submitting") {
    return <StatePanel message="진단을 생성하는 중입니다. 잠시 기다려주세요..." />;
  }

  if (state.status === "auth-required") {
    return (
      <AuthRequiredPanel
        redirectPath={`/diagnoses/new?githubAnalysisId=${encodeURIComponent(githubAnalysisId)}`}
      />
    );
  }

  if (state.status === "error") {
    return (
      <div>
        <StatePanel message={state.message} tone="danger" />
        <p>
          <a href="/github/analysis">분석 결과로 돌아가기</a>
        </p>
      </div>
    );
  }

  return (
    <div>
      <h1>역량 진단 생성</h1>
      <p>GitHub 분석 결과를 기반으로 역량 진단을 생성합니다.</p>

      <dl>
        <div>
          <dt>GitHub 분석 ID</dt>
          <dd>{githubAnalysisId}</dd>
        </div>
        <div>
          <dt>프로필 ID</dt>
          <dd>{state.profileId}</dd>
        </div>
      </dl>

      <form onSubmit={handleSubmit}>
        <button type="submit" className="btn-primary">
          진단 생성
        </button>
      </form>
    </div>
  );
}
