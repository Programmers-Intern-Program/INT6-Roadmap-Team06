import { ApiError } from "@/lib/api";

export type CoachErrorKind =
  | "SESSION_NOT_FOUND"
  | "SESSION_CLOSED"
  | "SNAPSHOT_NOT_FOUND"
  | "FORBIDDEN"
  | "RATE_LIMITED"
  | "SERVER"
  | "NETWORK"
  | "ABORTED"
  | "UNKNOWN";

export type CoachErrorInfo = {
  kind: CoachErrorKind;
  title: string;
  detail?: string;
  traceId?: string;
};

const CODE_TO_KIND: Record<string, CoachErrorKind> = {
  SESSION_NOT_FOUND: "SESSION_NOT_FOUND",
  SESSION_CLOSED: "SESSION_CLOSED",
  SNAPSHOT_NOT_FOUND: "SNAPSHOT_NOT_FOUND",
  FORBIDDEN: "FORBIDDEN"
};

const MESSAGES: Record<CoachErrorKind, { title: string; detail?: string }> = {
  SESSION_NOT_FOUND: {
    title: "세션을 찾을 수 없습니다.",
    detail: "새 세션을 시작해 주세요."
  },
  SESSION_CLOSED: {
    title: "이미 종료된 세션입니다.",
    detail: "새 세션을 시작해 주세요."
  },
  SNAPSHOT_NOT_FOUND: {
    title: "프로필과 로드맵을 먼저 만들어 주세요.",
    detail: "프로필 작성과 로드맵 생성이 모두 완료되어야 코치를 사용할 수 있어요."
  },
  FORBIDDEN: {
    title: "이 세션에 접근할 권한이 없습니다."
  },
  RATE_LIMITED: {
    title: "잠시 후 다시 시도해 주세요.",
    detail: "요청이 잠시 제한되었습니다."
  },
  SERVER: {
    title: "일시적인 오류가 발생했어요.",
    detail: "잠시 후 다시 시도해 주세요."
  },
  NETWORK: {
    title: "네트워크 연결을 확인해 주세요.",
    detail: "입력하신 메시지는 그대로 남겨 두었어요."
  },
  ABORTED: {
    title: "요청이 중단되었습니다."
  },
  UNKNOWN: {
    title: "알 수 없는 오류가 발생했어요."
  }
};

export function isAbortError(error: unknown): boolean {
  return (
    error instanceof DOMException && error.name === "AbortError"
  ) || (error instanceof Error && error.name === "AbortError");
}

export function classifyCoachError(error: unknown): CoachErrorInfo {
  if (isAbortError(error)) {
    return { kind: "ABORTED", ...MESSAGES.ABORTED };
  }

  if (error instanceof ApiError) {
    const codeKind = error.code ? CODE_TO_KIND[error.code] : undefined;
    if (codeKind) {
      return { kind: codeKind, ...MESSAGES[codeKind], traceId: error.traceId };
    }
    if (error.status === 429) {
      return { kind: "RATE_LIMITED", ...MESSAGES.RATE_LIMITED, traceId: error.traceId };
    }
    if (error.status >= 500) {
      return { kind: "SERVER", ...MESSAGES.SERVER, traceId: error.traceId };
    }
    return { kind: "UNKNOWN", title: error.message, traceId: error.traceId };
  }

  if (error instanceof TypeError) {
    return { kind: "NETWORK", ...MESSAGES.NETWORK };
  }

  return { kind: "UNKNOWN", ...MESSAGES.UNKNOWN };
}
