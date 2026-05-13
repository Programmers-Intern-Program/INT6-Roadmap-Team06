import { apiClient } from "@/lib/api";
import type { ApiRequestOptions } from "@/lib/api";

export type CurrentUser = {
  authProvider: string;
  email: string;
  userId: number;
};

export function getCurrentUser(options?: ApiRequestOptions) {
  return apiClient.get<CurrentUser>("/api/v1/auth/me", options);
}

export function refreshSession() {
  return apiClient.post<void>("/api/v1/auth/refresh", undefined, {
    skipAuthRefresh: true
  });
}

export function logoutSession() {
  return apiClient.post<void>("/api/v1/auth/logout", undefined, {
    skipAuthRefresh: true
  });
}
