import { apiClient } from "@/lib/api";
import type { Roadmap } from "@/features/roadmap/types";

export function getRoadmap(roadmapId: string) {
  return apiClient.get<Roadmap>(`/api/roadmaps/${roadmapId}`);
}
