import { apiClient } from "@/lib/api";
import type {
  ProfileDetail,
  ProfileSaveRequest,
  ProfileSaveResponse
} from "@/features/profile/types";

export function getMyProfile() {
  return apiClient.get<ProfileDetail>("/api/profiles/me");
}

export function saveProfile(payload: ProfileSaveRequest) {
  return apiClient.post<ProfileSaveResponse>("/api/profiles", payload);
}
