import { apiClient } from "@/lib/api";
import type {
  Diagnosis,
  DiagnosisRequest
} from "@/features/diagnosis/types";

export function createDiagnosis(payload: DiagnosisRequest) {
  return apiClient.post<Diagnosis>("/api/diagnoses", {
    profileId: Number(payload.profileId),
    githubAnalysisId: Number(payload.githubAnalysisId),
  });
}

export function getDiagnosis(diagnosisId: string) {
  return apiClient.get<Diagnosis>(`/api/diagnoses/${diagnosisId}`);
}
