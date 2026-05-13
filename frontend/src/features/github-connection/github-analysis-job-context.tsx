"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
} from "react";
import { getJobStatus, submitAnalysisAsync } from "@/features/github-connection/api";
import type { AnalysisJobStatusResponse } from "@/features/github-connection/api";

// ── 타입 ──────────────────────────────────────────────────────────────

interface ActiveJob {
  jobId: string;
  startedAt: number;
}

interface GithubAnalysisJobContextValue {
  activeJob: ActiveJob | null;
  jobStatus: AnalysisJobStatusResponse | null;
  cacheInvalidateKey: number;
  /** 분석 제출 진행 중 (백엔드가 jobId 반환 대기 중) */
  submitting: boolean;
  submitError: string | null;
  startJob: (jobId: string) => void;
  /** 비동기 분석 제출 — 페이지 이동 후에도 호출이 계속된다 */
  submitAnalysisJob: (
    connectionId: string,
    selectedIds: string[],
    coreIds: string[]
  ) => Promise<void>;
  dismissJob: () => void;
}

// ── 상수 ─────────────────────────────────────────────────────────────

const STORAGE_KEY = "github_analysis_job";
const POLL_INTERVAL_MS = 3000;

// ── Context ───────────────────────────────────────────────────────────

const GithubAnalysisJobContext = createContext<GithubAnalysisJobContextValue | null>(null);

// ── Provider ─────────────────────────────────────────────────────────

export function GithubAnalysisJobProvider({ children }: { children: React.ReactNode }) {
  const [activeJob, setActiveJob] = useState<ActiveJob | null>(null);
  const [jobStatus, setJobStatus] = useState<AnalysisJobStatusResponse | null>(null);
  const [cacheInvalidateKey, setCacheInvalidateKey] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);

  // ── sessionStorage 직렬화/복원 ──

  function saveToStorage(job: ActiveJob | null) {
    if (job) {
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(job));
    } else {
      sessionStorage.removeItem(STORAGE_KEY);
    }
  }

  function loadFromStorage(): ActiveJob | null {
    try {
      const raw = sessionStorage.getItem(STORAGE_KEY);
      if (!raw) return null;
      return JSON.parse(raw) as ActiveJob;
    } catch {
      return null;
    }
  }

  // ── 폴링 ──

  const poll = useCallback(async (job: ActiveJob) => {
    try {
      const status = await getJobStatus(job.jobId);
      setJobStatus(status);

      // 완료 상태 감지
      if (status.status === "SUCCEEDED" || status.status === "FAILED") {
        setCacheInvalidateKey((k) => k + 1);
        setTimeout(() => {
          stopPoll();
          setActiveJob(null);
          saveToStorage(null);
        }, 3000);
      }
    } catch {
      // 개별 폴링 실패는 무시 (네트워크 일시적 오류)
    }
  }, []);

  function startPoll(job: ActiveJob) {
    stopPoll();
    pollRef.current = setInterval(() => poll(job), POLL_INTERVAL_MS);
    // 즉시 1회 폴링
    void poll(job);
  }

  function stopPoll() {
    if (pollRef.current) {
      clearInterval(pollRef.current);
      pollRef.current = null;
    }
  }

  // 폴링 설정: activeJob 변경 시
  useEffect(() => {
    if (!activeJob) return;
    startPoll(activeJob);
    return stopPoll;
  }, [activeJob, poll]);

  // ── 새로고침 복구 ──

  useEffect(() => {
    const stored = loadFromStorage();
    if (!stored) return;
    setActiveJob(stored);
  }, []);

  // ── cleanup ──

  useEffect(() => {
    return stopPoll;
  }, []);

  // ── 공개 API ──

  const startJob = useCallback((jobId: string) => {
    const job: ActiveJob = { jobId, startedAt: Date.now() };
    setActiveJob(job);
    saveToStorage(job);
  }, []);

  const submitAnalysisJob = useCallback(
    async (connectionId: string, selectedIds: string[], coreIds: string[]) => {
      setSubmitting(true);
      setSubmitError(null);
      try {
        const { jobId } = await submitAnalysisAsync(connectionId, selectedIds, coreIds);
        const job: ActiveJob = { jobId, startedAt: Date.now() };
        setActiveJob(job);
        saveToStorage(job);
      } catch (err) {
        const message = err instanceof Error ? err.message : "분석 작업을 시작하지 못했습니다.";
        setSubmitError(message);
      } finally {
        setSubmitting(false);
      }
    },
    []
  );

  const dismissJob = useCallback(() => {
    stopPoll();
    setActiveJob(null);
    setJobStatus(null);
    setSubmitError(null);
    saveToStorage(null);
  }, []);

  return (
    <GithubAnalysisJobContext.Provider
      value={{
        activeJob,
        jobStatus,
        cacheInvalidateKey,
        submitting,
        submitError,
        startJob,
        submitAnalysisJob,
        dismissJob,
      }}
    >
      {children}
    </GithubAnalysisJobContext.Provider>
  );
}

// ── Hook ──────────────────────────────────────────────────────────────

export function useGithubAnalysisJob(): GithubAnalysisJobContextValue {
  const ctx = useContext(GithubAnalysisJobContext);
  if (!ctx) {
    throw new Error("useGithubAnalysisJob must be used within GithubAnalysisJobProvider");
  }
  return ctx;
}
