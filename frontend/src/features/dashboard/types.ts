export type CurrentLevel =
  | "BEGINNER"
  | "BASIC"
  | "JUNIOR"
  | "INTERMEDIATE"
  | "ADVANCED";

export type FinalTechProfile = {
  confirmedSkills: string[];
  focusAreas: string[];
};

export type DashboardProfileSummary = {
  currentLevel: CurrentLevel;
  jobRoleId: string;
  profileId: string;
  targetDate?: string | null;
  updatedAt: string;
  weeklyStudyHours?: number | null;
};

export type DashboardGithubAnalysisSummary = {
  createdAt: string;
  finalTechProfile: FinalTechProfile;
  githubAnalysisId: string;
  summary: string;
  userCorrectionCount: number;
  version: number;
};

export type DashboardDiagnosisSummary = {
  createdAt: string;
  diagnosisId: string;
  summary: string;
  version: number;
};

export type DashboardProgressSummary = {
  doneWeeks: number;
  inProgressWeeks: number;
  skippedWeeks: number;
  todoWeeks: number;
  totalWeeks: number;
};

export type DashboardRoadmapSummary = {
  createdAt: string;
  progress: DashboardProgressSummary;
  roadmapId: string;
  summary: string;
  totalWeeks: number;
  version: number;
};

export type Dashboard = {
  diagnosis: DashboardDiagnosisSummary | null;
  githubAnalysis: DashboardGithubAnalysisSummary | null;
  profile: DashboardProfileSummary | null;
  roadmap: DashboardRoadmapSummary | null;
  userId: string;
};
