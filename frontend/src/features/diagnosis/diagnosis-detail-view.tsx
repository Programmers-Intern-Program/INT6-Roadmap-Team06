"use client";

import Link from "next/link";
import { useEffect, useState } from "react";

import { AuthRequiredPanel } from "@/components/auth-required-panel";
import { StatePanel } from "@/components/state-panel";
import {
  StatusBadge,
  type StatusBadgeTone
} from "@/components/status-badge";
import { getDiagnosis } from "@/features/diagnosis/api";
import {
  currentLevelLabels,
  diagnosisSeverityLabels
} from "@/features/diagnosis/labels";
import type { Diagnosis, MissingSkill } from "@/features/diagnosis/types";
import { ApiError } from "@/lib/api";
import { isUnauthorizedError } from "@/lib/auth";

type DiagnosisDetailViewProps = {
  diagnosisId: string;
};

type DiagnosisState =
  | { status: "loading" }
  | { status: "auth-required" }
  | { status: "error"; message: string }
  | { status: "success"; diagnosis: Diagnosis };

export function DiagnosisDetailView({ diagnosisId }: DiagnosisDetailViewProps) {
  const [state, setState] = useState<DiagnosisState>({ status: "loading" });

  useEffect(() => {
    let ignore = false;

    async function loadDiagnosis() {
      setState({ status: "loading" });

      try {
        const diagnosis = await getDiagnosis(diagnosisId);

        if (!ignore) {
          setState({ diagnosis, status: "success" });
        }
      } catch (error) {
        if (!ignore) {
          if (isUnauthorizedError(error)) {
            setState({ status: "auth-required" });
            return;
          }
          setState({
            message: getErrorMessage(error),
            status: "error"
          });
        }
      }
    }

    loadDiagnosis();

    return () => {
      ignore = true;
    };
  }, [diagnosisId]);

  if (state.status === "loading") {
    return (
      <StatePanel
        className="diagnosis-state-panel"
        message="진단 결과를 불러오는 중입니다."
      />
    );
  }

  if (state.status === "auth-required") {
    return (
      <AuthRequiredPanel
        className="diagnosis-state-panel"
        redirectPath={`/diagnoses/${encodeURIComponent(diagnosisId)}`}
      />
    );
  }

  if (state.status === "error") {
    return (
      <section className="screen-shell">
        <StatePanel
          className="diagnosis-state-panel"
          message={state.message}
          tone="danger"
        />
        <div className="action-row" aria-label="진단 오류 다음 행동">
          <Link className="action-link primary" href="/diagnoses">
            최근 진단 보기
          </Link>
          <Link className="action-link" href="/github/analysis">
            분석 보정
          </Link>
        </div>
      </section>
    );
  }

  const { diagnosis } = state;
  const missingSkills = [...diagnosis.missingSkills].sort(comparePriorityOrder);

  return (
    <section className="diagnosis-detail" aria-labelledby="diagnosis-title">
      <div className="screen-hero">
        <p className="eyebrow">v{diagnosis.version} 진단</p>
        <div className="screen-heading">
          <h1 id="diagnosis-title">역량 진단 결과</h1>
          <p>{diagnosis.summary}</p>
        </div>
        <div className="action-row" aria-label="진단 관련 화면 이동">
          <Link
            className="action-link primary"
            href={`/roadmaps/new?diagnosisId=${diagnosis.diagnosisId}`}
          >
            로드맵 생성
          </Link>
          <Link className="action-link" href="/github/analysis">
            분석 보정
          </Link>
        </div>
        <dl className="diagnosis-summary-list" aria-label="진단 요약">
          <div>
            <dt>진단 ID</dt>
            <dd>{diagnosis.diagnosisId}</dd>
          </div>
          <div>
            <dt>목표 직무</dt>
            <dd>{diagnosis.targetRole}</dd>
          </div>
          <div>
            <dt>현재 수준</dt>
            <dd>{currentLevelLabels[diagnosis.currentLevel]}</dd>
          </div>
          <div>
            <dt>생성일</dt>
            <dd>{formatDateTime(diagnosis.createdAt)}</dd>
          </div>
        </dl>
      </div>

      <section className="panel diagnosis-section" aria-labelledby="missing-title">
        <div className="diagnosis-section-heading">
          <h2 id="missing-title">부족 기술</h2>
          <p>우선순위가 높은 기술부터 확인합니다.</p>
        </div>
        {missingSkills.length === 0 ? (
          <p className="diagnosis-empty-text">표시할 부족 기술이 없습니다.</p>
        ) : (
          <ol className="diagnosis-missing-list">
            {missingSkills.map((skill) => (
              <li
                className="diagnosis-missing-item"
                key={`${skill.priorityOrder}-${skill.skillName}`}
              >
                <div className="diagnosis-missing-header">
                  <div>
                    <span>우선순위 {skill.priorityOrder}</span>
                    <h3>{skill.skillName}</h3>
                  </div>
                  <SeverityBadge skill={skill} />
                </div>
                <p>{skill.reason}</p>
              </li>
            ))}
          </ol>
        )}
      </section>

      <div className="diagnosis-result-grid">
        <DiagnosisListSection
          emptyMessage="표시할 강점이 없습니다."
          items={diagnosis.strengths}
          title="강점"
        />
        <DiagnosisListSection
          emptyMessage="표시할 추천이 없습니다."
          items={diagnosis.recommendations}
          title="추천"
        />
      </div>
    </section>
  );
}

function DiagnosisListSection({
  emptyMessage,
  items,
  title
}: {
  emptyMessage: string;
  items: string[];
  title: string;
}) {
  return (
    <section className="panel diagnosis-section">
      <h2>{title}</h2>
      {items.length === 0 ? (
        <p className="diagnosis-empty-text">{emptyMessage}</p>
      ) : (
        <ul className="diagnosis-bullet-list">
          {items.map((item) => (
            <li key={item}>{item}</li>
          ))}
        </ul>
      )}
    </section>
  );
}

function SeverityBadge({ skill }: { skill: MissingSkill }) {
  return (
    <StatusBadge tone={diagnosisSeverityTones[skill.severity]}>
      {diagnosisSeverityLabels[skill.severity]}
    </StatusBadge>
  );
}

const diagnosisSeverityTones: Record<MissingSkill["severity"], StatusBadgeTone> = {
  HIGH: "danger",
  LOW: "neutral",
  MEDIUM: "warning"
};

function comparePriorityOrder(a: MissingSkill, b: MissingSkill) {
  return a.priorityOrder - b.priorityOrder;
}

function getErrorMessage(error: unknown) {
  if (error instanceof ApiError) {
    return error.message;
  }

  return "진단 결과를 불러오지 못했습니다.";
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    dateStyle: "medium",
    timeStyle: "short"
  }).format(new Date(value));
}
