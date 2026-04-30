import type {
  CurrentLevel,
  ProficiencyLevel,
  SkillSourceType
} from "@/features/profile/types";

export const currentLevelOptions: CurrentLevel[] = [
  "BEGINNER",
  "BASIC",
  "JUNIOR",
  "INTERMEDIATE",
  "ADVANCED"
];

export const proficiencyLevelOptions: ProficiencyLevel[] = [
  "NONE",
  "BASIC",
  "WORKING",
  "STRONG"
];

export const currentLevelLabels: Record<CurrentLevel, string> = {
  ADVANCED: "고급",
  BASIC: "기초",
  BEGINNER: "입문",
  INTERMEDIATE: "중급",
  JUNIOR: "주니어"
};

export const proficiencyLevelLabels: Record<ProficiencyLevel, string> = {
  BASIC: "기초",
  NONE: "없음",
  STRONG: "강함",
  WORKING: "실무 가능"
};

export const skillSourceTypeLabels: Record<SkillSourceType, string> = {
  GITHUB_ESTIMATED: "GitHub 추정",
  SYSTEM_DERIVED: "시스템 추론",
  USER_INPUT: "사용자 입력"
};
