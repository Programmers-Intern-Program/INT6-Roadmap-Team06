import { apiClient } from "@/lib/api";
import type { ProfileDetail } from "@/features/profile/types";

export function getMyProfile() {
  return apiClient.get<ProfileDetail>("/api/profiles/me");
}
