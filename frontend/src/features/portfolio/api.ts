import { apiClient } from "@/lib/api";

import type {
  PortfolioDraftDetail,
  PortfolioDraftSummary,
  PortfolioDraftUpdateRequest
} from "./types";

export function createPortfolioDraft() {
  return apiClient.post<PortfolioDraftDetail>("/api/portfolio/drafts");
}

export function generatePortfolioDraftVariant(draftId: string, variantKey: string) {
  return apiClient.post<PortfolioDraftDetail>(
    `/api/portfolio/drafts/${encodeURIComponent(draftId)}/variants/${encodeURIComponent(variantKey)}/generate`
  );
}

export function listPortfolioDrafts() {
  return apiClient.get<PortfolioDraftSummary[]>("/api/portfolio/drafts");
}

export function getPortfolioDraft(draftId: string) {
  return apiClient.get<PortfolioDraftDetail>(
    `/api/portfolio/drafts/${encodeURIComponent(draftId)}`
  );
}

export function updatePortfolioDraft(
  draftId: string,
  payload: PortfolioDraftUpdateRequest
) {
  return apiClient.patch<PortfolioDraftDetail>(
    `/api/portfolio/drafts/${encodeURIComponent(draftId)}`,
    payload
  );
}
