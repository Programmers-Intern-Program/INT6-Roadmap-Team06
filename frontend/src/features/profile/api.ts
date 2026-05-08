import { apiClient } from "@/lib/api";
import type {
  JobRoleOption,
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

export function getJobRoles() {
  return apiClient.get<JobRoleOption[]>("/api/job-roles");
}
