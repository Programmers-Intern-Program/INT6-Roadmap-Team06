import { apiClient } from "@/lib/api";
import type {
  CoachMessageHistory,
  CoachMessageResponse,
  CoachSession,
  CoachSessionSummary,
  ReplanResult
} from "@/features/coach/types";

export function getActiveSession(signal?: AbortSignal) {
  return apiClient.get<CoachSession>("/api/coach/sessions/active", { signal });
}

export function getSessions(signal?: AbortSignal) {
  return apiClient.get<CoachSessionSummary[]>("/api/coach/sessions", { signal });
}

export function getSessionMessages(sessionId: string, signal?: AbortSignal) {
  return apiClient.get<CoachMessageHistory[]>(
    `/api/coach/sessions/${sessionId}/messages`,
    { signal }
  );
}

export function createSession(signal?: AbortSignal) {
  return apiClient.post<CoachSession>("/api/coach/sessions", undefined, {
    signal
  });
}

export function closeSession(sessionId: string, signal?: AbortSignal) {
  return apiClient.delete<null>(`/api/coach/sessions/${sessionId}`, { signal });
}

type SendMessageOptions = {
  signal?: AbortSignal;
  idempotencyKey?: string;
};

export function sendMessage(
  sessionId: string,
  message: string,
  { signal, idempotencyKey }: SendMessageOptions = {}
) {
  const headers = new Headers();
  if (idempotencyKey) {
    headers.set("Idempotency-Key", idempotencyKey);
  }

  return apiClient.post<CoachMessageResponse>(
    `/api/coach/sessions/${sessionId}/messages`,
    { message },
    { signal, headers }
  );
}

export function confirmReplan(
  sessionId: string,
  proposalId: string,
  confirmed: boolean,
  signal?: AbortSignal
) {
  return apiClient.post<ReplanResult>(
    `/api/coach/sessions/${sessionId}/replan`,
    {
      proposalId: Number(proposalId),
      confirmed
    },
    { signal }
  );
}
