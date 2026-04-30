import type { MaterialType, ProgressStatus, RoadmapTaskType } from "@/features/roadmap/types";

export const progressStatusOptions: ProgressStatus[] = [
  "TODO",
  "IN_PROGRESS",
  "DONE",
  "SKIPPED"
];

export const progressStatusLabels: Record<ProgressStatus, string> = {
  DONE: "완료",
  IN_PROGRESS: "진행 중",
  SKIPPED: "건너뜀",
  TODO: "예정"
};

export const progressStatusClassNames: Record<ProgressStatus, string> = {
  DONE: "done",
  IN_PROGRESS: "in-progress",
  SKIPPED: "skipped",
  TODO: "todo"
};

export const taskTypeLabels: Record<RoadmapTaskType, string> = {
  APPLY_PROJECT: "프로젝트 적용",
  BUILD_EXAMPLE: "예제 구현",
  READ_DOCS: "문서 읽기",
  REVIEW: "복습",
  WRITE_NOTE: "노트 작성"
};

export const materialTypeLabels: Record<MaterialType, string> = {
  ARTICLE: "아티클",
  DOCS: "문서",
  REPOSITORY: "저장소",
  TEMPLATE: "템플릿",
  VIDEO: "영상"
};
