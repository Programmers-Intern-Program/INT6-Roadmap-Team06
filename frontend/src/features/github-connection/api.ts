import { apiClient } from "@/lib/api";
import type {
  GithubAnalysisResult,
  GithubConnection,
  GithubRepositoryList,
} from "@/features/github-connection/types";

export function connectGithub(authorizationCode: string) {
  return apiClient.post<GithubConnection>("/api/github/connections", {
    authorizationCode,
  });
}

export function getLatestGithubConnection() {
  return apiClient.get<GithubConnection>("/api/github/connections/latest");
}

export function getRepositories(connectionId: string) {
  return apiClient.get<GithubRepositoryList>(
    `/api/github/repositories?githubConnectionId=${connectionId}`
  );
}

export function runAnalysis(
  connectionId: string,
  selectedIds: string[],
  coreIds: string[]
) {
  return apiClient.post<GithubAnalysisResult>("/api/github-analyses", {
    githubConnectionId: Number(connectionId),
    selectedRepositoryIds: selectedIds.map(Number),
    coreRepositoryIds: coreIds.map(Number),
  });
}

export type AnalysisJobSubmission = { jobId: string };

export type AnalysisJobStatusResponse = {
  jobId: string;
  status: "REQUESTED" | "RUNNING" | "SUCCEEDED" | "FAILED";
  currentStep: string | null;
  error: string | null;
  resultId: string | null;
};

export function submitAnalysisAsync(
  connectionId: string,
  selectedIds: string[],
  coreIds: string[]
) {
  return apiClient.post<AnalysisJobSubmission>("/api/github-analyses/async", {
    githubConnectionId: Number(connectionId),
    selectedRepositoryIds: selectedIds.map(Number),
    coreRepositoryIds: coreIds.map(Number),
  });
}

export function getJobStatus(jobId: string) {
  return apiClient.get<AnalysisJobStatusResponse>(`/api/jobs/${jobId}/status`);
}

export type JobHistoryItem = {
  jobId: string;
  jobType: string;
  status: "REQUESTED" | "RUNNING" | "SUCCEEDED" | "FAILED";
  currentStep: string | null;
  error: string | null;
  resultId: string | null;
  recordedAt: string;
};

export function listJobHistory(limit = 20) {
  return apiClient.get<JobHistoryItem[]>(`/api/jobs/history?limit=${limit}`);
}
