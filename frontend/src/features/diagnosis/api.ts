import { apiClient } from "@/lib/api";
import type { Diagnosis } from "@/features/diagnosis/types";

export function getDiagnosis(diagnosisId: string) {
  return apiClient.get<Diagnosis>(`/api/diagnoses/${diagnosisId}`);
}
