import type {
  GithubDepthLevel,
  GithubEvidenceType
} from "@/features/github-analysis/types";

export const githubDepthLevelLabels: Record<GithubDepthLevel, string> = {
  APPLIED: "적용",
  DEEP: "심화",
  INTRO: "입문",
  PRACTICAL: "실무"
};

export const githubDepthLevelClassNames: Record<GithubDepthLevel, string> = {
  APPLIED: "applied",
  DEEP: "deep",
  INTRO: "intro",
  PRACTICAL: "practical"
};

export const githubEvidenceTypeLabels: Record<GithubEvidenceType, string> = {
  CODE: "코드",
  COMMIT: "커밋",
  CONFIG: "설정",
  README: "README",
  REPO_METADATA: "저장소 메타"
};
