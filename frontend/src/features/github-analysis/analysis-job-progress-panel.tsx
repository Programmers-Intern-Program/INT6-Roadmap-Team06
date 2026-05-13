"use client";

interface AnalysisJobProgressPanelProps {
  status: "REQUESTED" | "RUNNING";
  currentStep: string | null;
  onDismiss: () => void;
}

export function AnalysisJobProgressPanel({
  status,
  currentStep,
  onDismiss,
}: AnalysisJobProgressPanelProps) {
  return (
    <div className="analysis-job-progress-panel">
      <div className="analysis-job-progress-icon">
        <span className="analysis-job-spinner" />
      </div>

      <div className="analysis-job-progress-content">
        <p className="analysis-job-progress-label">진행 중</p>
        <p className="analysis-job-progress-title">GitHub 분석이 백그라운드에서 진행 중입니다</p>

        <div className="analysis-job-progress-status">
          <span className="analysis-job-status-chip">
            {status === "RUNNING" ? "분석 중" : "대기 중"}
          </span>
        </div>

        {currentStep && (
          <p className="analysis-job-progress-step">단계: {currentStep}</p>
        )}

        <p className="analysis-job-progress-description">
          세션 종료는 정상적으로 진행되었습니다. 분석 리포트는 잠시 후 준비될 수 있습니다.
        </p>
        <p className="analysis-job-progress-detail">
          이 페이지를 벗어나도 분석은 백그라운드에서 계속 진행됩니다. 나중에 저장소 목록으로 돌아와 최신 상태를 확인할 수 있습니다.
        </p>
      </div>

      <div className="analysis-job-progress-actions">
        <button
          type="button"
          onClick={onDismiss}
          className="analysis-job-progress-dismiss-btn"
        >
          닫기
        </button>
      </div>
    </div>
  );
}
