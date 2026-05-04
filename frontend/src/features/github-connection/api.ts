import { apiClient } from "@/lib/api";
import type {
  GithubConnectionRequest,
  GithubConnectionResponse,
  GithubRepositoryListResponse
} from "@/features/github-connection/types";

export function connectGithub(payload: GithubConnectionRequest) {
  return apiClient.post<GithubConnectionResponse>(
    "/api/github/connections",
    payload
  );
}

export function getGithubRepositories(githubConnectionId: string) {
  return apiClient.get<GithubRepositoryListResponse>(
    `/api/github/repositories?githubConnectionId=${encodeURIComponent(
      githubConnectionId
    )}`
  );
}
