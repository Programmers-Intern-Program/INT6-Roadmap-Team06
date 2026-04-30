import { apiClient } from "@/lib/api";
import type {
  GithubAnalysis,
  GithubAnalysisCorrectionRequest,
  GithubAnalysisCorrectionResponse
} from "@/features/github-analysis/types";

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
