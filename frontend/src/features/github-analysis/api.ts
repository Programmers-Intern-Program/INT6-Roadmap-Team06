import { apiClient } from "@/lib/api";
import type {
  GithubAnalysis,
  GithubAnalysisCorrectionRequest,
  GithubAnalysisCorrectionResponse,
  GithubAnalysisRequest
} from "@/features/github-analysis/types";

export function createGithubAnalysis(payload: GithubAnalysisRequest) {
  return apiClient.post<GithubAnalysis>("/api/github-analyses", payload);
}

export function getGithubAnalysis(githubAnalysisId: string) {
  return apiClient.get<GithubAnalysis>(
    `/api/github-analyses/${githubAnalysisId}`
  );
}

export function saveGithubAnalysisCorrections(
  githubAnalysisId: string,
  payload: GithubAnalysisCorrectionRequest
) {
  return apiClient.patch<GithubAnalysisCorrectionResponse>(
    `/api/github-analyses/${githubAnalysisId}/corrections`,
    payload
  );
}
