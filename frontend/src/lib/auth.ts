import { ApiError } from "@/lib/api";

const DEFAULT_REDIRECT_PATH = "/me";

export function isUnauthorizedError(error: unknown): error is ApiError {
  return error instanceof ApiError && error.status === 401;
}

export function getCurrentRedirectPath() {
  if (typeof window === "undefined") {
    return DEFAULT_REDIRECT_PATH;
  }

  const { hash, pathname, search } = window.location;
  return normalizeLocalRedirectPath(`${pathname}${search}${hash}`);
}

export function getLoginPath(redirectPath?: string | null) {
  const target = normalizeLocalRedirectPath(
    redirectPath ?? getCurrentRedirectPath()
  );

  return `/login?redirectUrl=${encodeURIComponent(target)}`;
}

export function normalizeLocalRedirectPath(value?: string | null) {
  const trimmed = value?.trim();

  if (
    trimmed &&
    trimmed.startsWith("/") &&
    !trimmed.startsWith("//") &&
    !trimmed.startsWith("/\\")
  ) {
    return trimmed;
  }

  return DEFAULT_REDIRECT_PATH;
}
