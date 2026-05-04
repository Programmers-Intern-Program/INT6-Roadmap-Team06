export type CurrentLevel =
  | "BEGINNER"
  | "BASIC"
  | "JUNIOR"
  | "INTERMEDIATE"
  | "ADVANCED";

export type DiagnosisSeverity = "LOW" | "MEDIUM" | "HIGH";

export type MissingSkill = {
  priorityOrder: number;
  reason: string;
  severity: DiagnosisSeverity;
  skillName: string;
};

export type Diagnosis = {
  createdAt: string;
  currentLevel: CurrentLevel;
  diagnosisId: string;
  githubAnalysisId?: string | null;
  missingSkills: MissingSkill[];
  profileId: string;
  recommendations: string[];
  strengths: string[];
  summary: string;
  targetRole: string;
  version: number;
};

export type DiagnosisRequest = {
  githubAnalysisId: string;
  profileId: string;
};
