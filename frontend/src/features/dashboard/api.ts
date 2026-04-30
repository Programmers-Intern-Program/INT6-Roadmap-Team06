import { apiClient } from "@/lib/api";
import type { Dashboard } from "@/features/dashboard/types";

export function getDashboard() {
  return apiClient.get<Dashboard>("/api/dashboard");
}
