import type {
  CurrentLevel,
  DiagnosisSeverity
} from "@/features/diagnosis/types";

export const currentLevelLabels: Record<CurrentLevel, string> = {
  ADVANCED: "고급",
  BASIC: "기초",
  BEGINNER: "입문",
  INTERMEDIATE: "중급",
  JUNIOR: "주니어"
};

export const diagnosisSeverityLabels: Record<DiagnosisSeverity, string> = {
  HIGH: "높음",
  LOW: "낮음",
  MEDIUM: "중간"
};

export const diagnosisSeverityClassNames: Record<DiagnosisSeverity, string> = {
  HIGH: "high",
  LOW: "low",
  MEDIUM: "medium"
};
