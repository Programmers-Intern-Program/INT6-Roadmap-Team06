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
