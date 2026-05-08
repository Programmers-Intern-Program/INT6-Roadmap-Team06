export type JobRoleOption = {
  roleCode: string;
  roleName: string;
  description: string | null;
};

export type CurrentLevel =
  | "BEGINNER"
  | "BASIC"
  | "JUNIOR"
  | "INTERMEDIATE"
  | "ADVANCED";

export type ProficiencyLevel = "NONE" | "BASIC" | "WORKING" | "STRONG";

export type SkillSourceType =
  | "USER_INPUT"
  | "GITHUB_ESTIMATED"
  | "SYSTEM_DERIVED";

export type ProfileSkillInput = {
  proficiencyLevel?: ProficiencyLevel | null;
  skillName: string;
};

export type ProfileSkill = ProfileSkillInput & {
  sourceType: SkillSourceType;
};

export type ProfileSaveRequest = {
  currentLevel: CurrentLevel;
  interestAreas?: string[];
  portfolioAssetId?: string | null;
  resumeAssetId?: string | null;
  skills: ProfileSkillInput[];
  targetDate?: string | null;
  targetRole: string;
  weeklyStudyHours?: number | null;
};

export type ProfileSaveResponse = {
  message: string;
  profileId: string;
  savedAt: string;
};

export type ProfileDetail = {
  currentLevel: CurrentLevel;
  interestAreas: string[];
  portfolioAssetId?: string | null;
  profileId: string;
  resumeAssetId?: string | null;
  skills: ProfileSkill[];
  targetDate?: string | null;
  targetRole: string;
  weeklyStudyHours?: number | null;
};
