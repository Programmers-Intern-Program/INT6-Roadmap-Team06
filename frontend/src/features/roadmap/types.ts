export type ProgressStatus = "TODO" | "IN_PROGRESS" | "DONE" | "SKIPPED";

export type RoadmapTaskType =
  | "READ_DOCS"
  | "BUILD_EXAMPLE"
  | "WRITE_NOTE"
  | "APPLY_PROJECT"
  | "REVIEW";

export type MaterialType =
  | "DOCS"
  | "ARTICLE"
  | "REPOSITORY"
  | "VIDEO"
  | "TEMPLATE";

export type RoadmapTask = {
  type: RoadmapTaskType;
  title: string;
};

export type RoadmapMaterial = {
  type: MaterialType;
  title: string;
  url?: string | null;
};

export type RoadmapWeek = {
  estimatedHours: number;
  materials: RoadmapMaterial[];
  progressNote?: string | null;
  progressStatus?: ProgressStatus | null;
  progressUpdatedAt?: string | null;
  reason: string;
  roadmapWeekId: string;
  tasks: RoadmapTask[];
  topic: string;
  weekNumber: number;
};

export type Roadmap = {
  createdAt: string;
  diagnosisId?: string | null;
  roadmapId: string;
  summary: string;
  totalWeeks: number;
  version: number;
  weeks: RoadmapWeek[];
};

export type RoadmapRequest = {
  codingTestAnalysisId?: string | null;
  diagnosisId: string;
  githubAnalysisId?: string | null;
  targetDate?: string | null;
  weeklyStudyHours?: number | null;
};

export type RoadmapProgressRequest = {
  note?: string | null;
  roadmapWeekId: string;
  status: ProgressStatus;
};

export type RoadmapProgressResponse = {
  progressLogId: string;
  roadmapWeekId: string;
  savedAt: string;
  status: ProgressStatus;
};
