"use client";

import { useEffect, useState, type FormEvent } from "react";

import { StatePanel } from "@/components/state-panel";
import { getRoadmap, saveRoadmapProgress } from "@/features/roadmap/api";
import {
  materialTypeLabels,
  progressStatusClassNames,
  progressStatusLabels,
  progressStatusOptions,
  taskTypeLabels
} from "@/features/roadmap/labels";
import type { ProgressStatus, Roadmap } from "@/features/roadmap/types";
import { ApiError } from "@/lib/api";

type RoadmapDetailViewProps = {
  roadmapId: string;
};

type RoadmapState =
  | { status: "loading" }
  | { status: "error"; message: string }
  | { status: "success"; roadmap: Roadmap };

export function RoadmapDetailView({ roadmapId }: RoadmapDetailViewProps) {
  const [state, setState] = useState<RoadmapState>({ status: "loading" });
  const [savingWeekId, setSavingWeekId] = useState<string | null>(null);
  const [saveErrors, setSaveErrors] = useState<Record<string, string>>({});

  useEffect(() => {
    let ignore = false;

    async function loadRoadmap() {
      setState({ status: "loading" });

      try {
        const roadmap = await getRoadmap(roadmapId);

        if (!ignore) {
          setState({ roadmap, status: "success" });
        }
      } catch (error) {
        if (!ignore) {
          setState({
            message: getErrorMessage(error, "로드맵을 불러오지 못했습니다."),
            status: "error"
          });
        }
      }
    }

    loadRoadmap();

    return () => {
      ignore = true;
    };
  }, [roadmapId]);

  async function handleProgressSubmit(
    event: FormEvent<HTMLFormElement>,
    roadmapWeekId: string
  ) {
    event.preventDefault();

    const formData = new FormData(event.currentTarget);
    const status = formData.get("status");
    const note = formData.get("note");

    if (!isProgressStatus(status)) {
      setSaveErrors((current) => ({
        ...current,
        [roadmapWeekId]: "진도 상태를 선택해 주세요."
      }));
      return;
    }

    const normalizedNote =
      typeof note === "string" && note.trim().length > 0 ? note.trim() : null;

    setSavingWeekId(roadmapWeekId);
    setSaveErrors((current) => {
      const next = { ...current };
      delete next[roadmapWeekId];
      return next;
    });

    try {
      const saved = await saveRoadmapProgress(roadmapId, {
        note: normalizedNote,
        roadmapWeekId,
        status
      });

      setState((current) => {
        if (current.status !== "success") {
          return current;
        }

        return {
          roadmap: {
            ...current.roadmap,
            weeks: current.roadmap.weeks.map((week) =>
              week.roadmapWeekId === saved.roadmapWeekId
                ? {
                    ...week,
                    progressNote: normalizedNote,
                    progressStatus: saved.status,
                    progressUpdatedAt: saved.savedAt
                  }
                : week
            )
          },
          status: "success"
        };
      });
    } catch (error) {
      setSaveErrors((current) => ({
        ...current,
        [roadmapWeekId]: getErrorMessage(error, "진도를 저장하지 못했습니다.")
      }));
    } finally {
      setSavingWeekId(null);
    }
  }

  if (state.status === "loading") {
    return (
      <StatePanel
        className="roadmap-state-panel"
        message="로드맵을 불러오는 중입니다."
      />
    );
  }

  if (state.status === "error") {
    return (
      <StatePanel
        className="roadmap-state-panel"
        message={state.message}
        tone="danger"
      />
    );
  }

  const { roadmap } = state;

  return (
    <section className="roadmap-detail" aria-labelledby="roadmap-title">
      <div className="screen-hero">
        <p className="eyebrow">v{roadmap.version} 로드맵</p>
        <div className="screen-heading">
          <h1 id="roadmap-title">학습 로드맵 / 진도 관리</h1>
          <p>{roadmap.summary}</p>
        </div>
        <dl className="roadmap-summary-list" aria-label="로드맵 요약">
          <div>
            <dt>로드맵 ID</dt>
            <dd>{roadmap.roadmapId}</dd>
          </div>
          <div>
            <dt>전체 기간</dt>
            <dd>{roadmap.totalWeeks}주</dd>
          </div>
          <div>
            <dt>생성일</dt>
            <dd>{formatDateTime(roadmap.createdAt)}</dd>
          </div>
        </dl>
      </div>

      {roadmap.weeks.length === 0 ? (
        <StatePanel
          className="roadmap-state-panel"
          message="표시할 주차 계획이 없습니다."
        />
      ) : (
        <div className="roadmap-week-list">
          {roadmap.weeks.map((week) => {
            const progressStatus = week.progressStatus ?? "TODO";

            return (
              <article className="roadmap-week-card" key={week.roadmapWeekId}>
                <div className="roadmap-week-header">
                  <div>
                    <p className="roadmap-week-kicker">{week.weekNumber}주차</p>
                    <h2>{week.topic}</h2>
                  </div>
                  <span
                    className="progress-badge"
                    data-status={progressStatusClassNames[progressStatus]}
                  >
                    {progressStatusLabels[progressStatus]}
                  </span>
                </div>

                <p className="roadmap-week-reason">{week.reason}</p>

                <dl className="roadmap-week-meta">
                  <div>
                    <dt>예상 시간</dt>
                    <dd>{formatHours(week.estimatedHours)}</dd>
                  </div>
                  <div>
                    <dt>저장 ID</dt>
                    <dd>{week.roadmapWeekId}</dd>
                  </div>
                  <div>
                    <dt>최근 변경</dt>
                    <dd>
                      {week.progressUpdatedAt
                        ? formatDateTime(week.progressUpdatedAt)
                        : "아직 없음"}
                    </dd>
                  </div>
                </dl>

                {week.progressNote ? (
                  <div className="roadmap-progress-note">
                    <strong>진도 메모</strong>
                    <p>{week.progressNote}</p>
                  </div>
                ) : null}

                <form
                  className="roadmap-progress-form"
                  key={`${week.roadmapWeekId}-${progressStatus}-${week.progressUpdatedAt ?? ""}`}
                  onSubmit={(event) =>
                    handleProgressSubmit(event, week.roadmapWeekId)
                  }
                >
                  <label>
                    <span>진도 상태</span>
                    <select defaultValue={progressStatus} name="status">
                      {progressStatusOptions.map((status) => (
                        <option key={status} value={status}>
                          {progressStatusLabels[status]}
                        </option>
                      ))}
                    </select>
                  </label>

                  <label>
                    <span>메모</span>
                    <textarea
                      defaultValue={week.progressNote ?? ""}
                      maxLength={1000}
                      name="note"
                      placeholder="학습 결과나 다음에 볼 내용을 적어두세요."
                      rows={3}
                    />
                  </label>

                  <div className="roadmap-progress-actions">
                    {saveErrors[week.roadmapWeekId] ? (
                      <p role="alert">{saveErrors[week.roadmapWeekId]}</p>
                    ) : null}
                    <button
                      disabled={savingWeekId === week.roadmapWeekId}
                      type="submit"
                    >
                      {savingWeekId === week.roadmapWeekId
                        ? "저장 중"
                        : "진도 저장"}
                    </button>
                  </div>
                </form>

                <div className="roadmap-week-columns">
                  <section aria-label={`${week.weekNumber}주차 작업`}>
                    <h3>작업</h3>
                    <ul className="roadmap-detail-list">
                      {week.tasks.map((task) => (
                        <li key={`${task.type}-${task.title}`}>
                          <span>{taskTypeLabels[task.type]}</span>
                          {task.title}
                        </li>
                      ))}
                    </ul>
                  </section>

                  <section aria-label={`${week.weekNumber}주차 자료`}>
                    <h3>자료</h3>
                    <ul className="roadmap-detail-list">
                      {week.materials.map((material) => (
                        <li key={`${material.type}-${material.title}`}>
                          <span>{materialTypeLabels[material.type]}</span>
                          {material.url ? (
                            <a href={material.url} rel="noreferrer" target="_blank">
                              {material.title}
                            </a>
                          ) : (
                            material.title
                          )}
                        </li>
                      ))}
                    </ul>
                  </section>
                </div>
              </article>
            );
          })}
        </div>
      )}
    </section>
  );
}

function getErrorMessage(error: unknown, fallbackMessage: string) {
  if (error instanceof ApiError) {
    return error.message;
  }

  return fallbackMessage;
}

function isProgressStatus(
  value: FormDataEntryValue | null
): value is ProgressStatus {
  return (
    typeof value === "string" &&
    progressStatusOptions.includes(value as ProgressStatus)
  );
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    dateStyle: "medium",
    timeStyle: "short"
  }).format(new Date(value));
}

function formatHours(value: number) {
  return `${value.toLocaleString("ko-KR", {
    maximumFractionDigits: 1
  })}시간`;
}
