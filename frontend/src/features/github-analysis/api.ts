import { apiClient } from "@/lib/api";
import type {
  GithubAnalysis,
  GithubAnalysisCorrectionRequest,
  GithubAnalysisCorrectionResponse,
  GithubAnalysisRequest,
  GithubAnalysisSummary
} from "@/features/github-analysis/types";

export function createGithubAnalysis(payload: GithubAnalysisRequest) {
  return apiClient.post<GithubAnalysis>("/api/github-analyses", payload);
}

export function getGithubAnalysis(githubAnalysisId: string) {
  return apiClient.get<GithubAnalysis>(
    `/api/github-analyses/${githubAnalysisId}`
  );
}

export function listGithubAnalyses() {
  return apiClient.get<GithubAnalysisSummary[]>("/api/github-analyses");
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
