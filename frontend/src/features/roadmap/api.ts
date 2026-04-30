import { apiClient } from "@/lib/api";
import type {
  Roadmap,
  RoadmapProgressRequest,
  RoadmapProgressResponse
} from "@/features/roadmap/types";

export function getRoadmap(roadmapId: string) {
  return apiClient.get<Roadmap>(`/api/roadmaps/${roadmapId}`);
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
