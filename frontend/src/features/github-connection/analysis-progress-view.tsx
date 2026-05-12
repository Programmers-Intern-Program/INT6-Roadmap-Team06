"use client";

import { useEffect, useMemo, useState } from "react";

type AnalysisProgressViewProps = {
  selectedCount: number;
  currentStep: string | null;
  startedAtMs: number;
};

type StageId = "REQUESTED" | "METADATA" | "ANALYSIS" | "DONE";

const STAGE_ORDER: StageId[] = ["REQUESTED", "METADATA", "ANALYSIS", "DONE"];

const STAGE_LABEL: Record<StageId, string> = {
  REQUESTED: "잡 시작",
  METADATA: "저장소 메타데이터 수집",
  ANALYSIS: "코드 분석 + 종합",
  DONE: "완료"
};

// 백엔드 `currentStep` 문자열 → 사용자용 스테이지 매핑
function mapStep(step: string | null): StageId {
  if (!step) return "REQUESTED";
  if (step === "REQUEST_ACCEPTED") return "REQUESTED";
  if (step === "RUN_ANALYSIS") return "ANALYSIS";
  if (step === "ANALYSIS_DONE") return "DONE";
  if (step === "FETCH_METADATA") return "METADATA";
  return "ANALYSIS";
}

// 선택 repo 수 기준 대략적 예상 시간 (실측: 2 repos ≈ 9분, 호출당 ~100s 평균)
// 호출 수 = N triage + N summary + 1 synthesis = 2N + 1
function estimateMinutes(repoCount: number) {
  const llmCalls = 2 * repoCount + 1;
  const avgSeconds = 100;
  return Math.round((llmCalls * avgSeconds) / 60);
}

function formatElapsed(ms: number) {
  const totalSec = Math.floor(ms / 1000);
  const min = Math.floor(totalSec / 60);
  const sec = totalSec % 60;
  return `${min}분 ${String(sec).padStart(2, "0")}초`;
}

export function AnalysisProgressView({
  selectedCount,
  currentStep,
  startedAtMs
}: AnalysisProgressViewProps) {
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    const id = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(id);
  }, []);

  const elapsedMs = now - startedAtMs;
  const stage = mapStep(currentStep);
  const stageIndex = STAGE_ORDER.indexOf(stage);
  const estimate = useMemo(() => estimateMinutes(selectedCount), [selectedCount]);

  return (
    <section className="analysis-progress" aria-live="polite">
      <header className="analysis-progress-header">
        <h2 className="analysis-progress-title">GitHub 분석 진행 중</h2>
        <p className="analysis-progress-subtitle">
          선택한 저장소 {selectedCount}개를 분석하고 있어요. 예상 소요 시간 약 {estimate}분.
        </p>
      </header>

      <ol className="analysis-progress-steps" aria-label="분석 단계">
        {STAGE_ORDER.map((id, index) => {
          const status =
            index < stageIndex ? "done" : index === stageIndex ? "active" : "pending";
          return (
            <li key={id} className="analysis-progress-step" data-status={status}>
              <span className="analysis-progress-step-dot" aria-hidden="true">
                {status === "done" ? "✓" : index + 1}
              </span>
              <span className="analysis-progress-step-label">{STAGE_LABEL[id]}</span>
            </li>
          );
        })}
      </ol>

      <dl className="analysis-progress-meta">
        <div>
          <dt>경과 시간</dt>
          <dd>{formatElapsed(elapsedMs)}</dd>
        </div>
        <div>
          <dt>현재 단계</dt>
          <dd>{STAGE_LABEL[stage]}</dd>
        </div>
      </dl>

      <p className="analysis-progress-hint">
        이 페이지를 닫아도 분석은 계속 진행됩니다. 결과는{" "}
        <strong>분석 목록</strong>에서 확인할 수 있어요.
      </p>
    </section>
  );
}
