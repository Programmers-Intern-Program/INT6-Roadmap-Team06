export const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

export async function api<T = unknown>(
  path: string,
  init: RequestInit = {}
): Promise<{ ok: boolean; status: number; data: T | null }> {
  const res = await fetch(`${API_BASE}${path}`, {
    ...init,
    credentials: "include",
    headers: {
      Accept: "application/json",
      ...(init.headers ?? {}),
    },
  });

  let data: T | null = null;
  if (res.status !== 204) {
    try {
      data = (await res.json()) as T;
    } catch {
      data = null;
    }
  }
  return { ok: res.ok, status: res.status, data };
}

export function githubLoginUrl(redirectUrl?: string): string {
  const target = redirectUrl ?? (typeof window !== "undefined" ? window.location.origin + "/me" : "");
  const qs = target ? `?redirectUrl=${encodeURIComponent(target)}` : "";
  return `${API_BASE}/oauth2/authorization/github${qs}`;
}
