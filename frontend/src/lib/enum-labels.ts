export type CurrentLevelCode =
  | "BEGINNER"
  | "BASIC"
  | "JUNIOR"
  | "INTERMEDIATE"
  | "ADVANCED";

export type ProficiencyLevelCode = "NONE" | "BASIC" | "WORKING" | "STRONG";

export type SkillSourceTypeCode =
  | "USER_INPUT"
  | "GITHUB_ESTIMATED"
  | "SYSTEM_DERIVED";

export type DiagnosisSeverityCode = "LOW" | "MEDIUM" | "HIGH";

export type GithubDepthLevelCode = "INTRO" | "APPLIED" | "PRACTICAL" | "DEEP";

export type GithubEvidenceTypeCode =
  | "README"
  | "CODE"
  | "CONFIG"
  | "REPO_METADATA"
  | "COMMIT";

export type ProgressStatusCode = "TODO" | "IN_PROGRESS" | "DONE" | "SKIPPED";

export type RoadmapTaskTypeCode =
  | "READ_DOCS"
  | "BUILD_EXAMPLE"
  | "WRITE_NOTE"
  | "APPLY_PROJECT"
  | "REVIEW";

export type MaterialTypeCode =
  | "DOCS"
  | "ARTICLE"
  | "REPOSITORY"
  | "VIDEO"
  | "TEMPLATE";

export const currentLevelOptions: CurrentLevelCode[] = [
  "BEGINNER",
  "BASIC",
  "JUNIOR",
  "INTERMEDIATE",
  "ADVANCED"
];

export const proficiencyLevelOptions: ProficiencyLevelCode[] = [
  "NONE",
  "BASIC",
  "WORKING",
  "STRONG"
];

export const progressStatusOptions: ProgressStatusCode[] = [
  "TODO",
  "IN_PROGRESS",
  "DONE",
  "SKIPPED"
];

export const currentLevelLabels: Record<CurrentLevelCode, string> = {
  ADVANCED: "고급",
  BASIC: "기초",
  BEGINNER: "입문",
  INTERMEDIATE: "중급",
  JUNIOR: "주니어"
};

export const proficiencyLevelLabels: Record<ProficiencyLevelCode, string> = {
  BASIC: "기초",
  NONE: "없음",
  STRONG: "강함",
  WORKING: "실무 가능"
};

export const skillSourceTypeLabels: Record<SkillSourceTypeCode, string> = {
  GITHUB_ESTIMATED: "GitHub 추정",
  SYSTEM_DERIVED: "시스템 추론",
  USER_INPUT: "사용자 입력"
};

export const diagnosisSeverityLabels: Record<DiagnosisSeverityCode, string> = {
  HIGH: "높음",
  LOW: "낮음",
  MEDIUM: "중간"
};

export const diagnosisSeverityClassNames: Record<DiagnosisSeverityCode, string> = {
  HIGH: "high",
  LOW: "low",
  MEDIUM: "medium"
};

export const githubDepthLevelLabels: Record<GithubDepthLevelCode, string> = {
  APPLIED: "적용",
  DEEP: "심화",
  INTRO: "입문",
  PRACTICAL: "실무"
};

export const githubDepthLevelClassNames: Record<GithubDepthLevelCode, string> = {
  APPLIED: "applied",
  DEEP: "deep",
  INTRO: "intro",
  PRACTICAL: "practical"
};

export const githubEvidenceTypeLabels: Record<GithubEvidenceTypeCode, string> = {
  CODE: "코드",
  COMMIT: "커밋",
  CONFIG: "설정",
  README: "README",
  REPO_METADATA: "저장소 메타"
};

export const progressStatusLabels: Record<ProgressStatusCode, string> = {
  DONE: "완료",
  IN_PROGRESS: "진행 중",
  SKIPPED: "건너뜀",
  TODO: "예정"
};

export const progressStatusClassNames: Record<ProgressStatusCode, string> = {
  DONE: "done",
  IN_PROGRESS: "in-progress",
  SKIPPED: "skipped",
  TODO: "todo"
};

export const roadmapTaskTypeLabels: Record<RoadmapTaskTypeCode, string> = {
  APPLY_PROJECT: "프로젝트 적용",
  BUILD_EXAMPLE: "예제 구현",
  READ_DOCS: "문서 읽기",
  REVIEW: "복습",
  WRITE_NOTE: "노트 작성"
};

export const materialTypeLabels: Record<MaterialTypeCode, string> = {
  ARTICLE: "아티클",
  DOCS: "문서",
  REPOSITORY: "저장소",
  TEMPLATE: "템플릿",
  VIDEO: "영상"
};
