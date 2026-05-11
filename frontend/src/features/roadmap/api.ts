import { apiClient } from "@/lib/api";
import type {
  Roadmap,
  RoadmapRequest,
  RoadmapProgressRequest,
  RoadmapProgressResponse,
  RoadmapSummary
} from "@/features/roadmap/types";

export function createRoadmap(payload: RoadmapRequest) {
  return apiClient.post<Roadmap>("/api/roadmaps", payload);
}

export function getRoadmap(roadmapId: string) {
  return apiClient.get<Roadmap>(`/api/roadmaps/${roadmapId}`);
}

export function listRoadmaps() {
  return apiClient.get<RoadmapSummary[]>("/api/roadmaps");
}

export function saveRoadmapProgress(
  roadmapId: string,
  payload: RoadmapProgressRequest
) {
  return apiClient.post<RoadmapProgressResponse>(
    `/api/roadmaps/${roadmapId}/progress`,
    payload
  );
}
