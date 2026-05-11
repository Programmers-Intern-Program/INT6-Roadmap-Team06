export type GithubEvidenceType =
  | "README"
  | "CODE"
  | "CONFIG"
  | "REPO_METADATA"
  | "COMMIT";

export type GithubDepthLevel = "INTRO" | "APPLIED" | "PRACTICAL" | "DEEP";

export type PrimaryLanguage = {
  lang: string;
  ratio: number;
};

export type StaticSignals = {
  activeRepos: number;
  commitFrequency?: string | null;
  contributionPattern?: string | null;
  primaryLanguages: PrimaryLanguage[];
};

export type Highlight = {
  text: string;
  status: "ADOPTED" | "EVOLVED" | "REVERSED";
};

export type RepoSummary = {
  highlights: Highlight[];
  repoId: string;
  repoName: string;
  summary: string;
};

export type TechTag = {
  skillName: string;
  tagReason: string;
};

export type DepthEstimate = {
  level: GithubDepthLevel;
  reason: string;
  skillName: string;
};

export type GithubEvidence = {
  repoName: string;
  source: string;
  summary: string;
  type: GithubEvidenceType;
};

export type GithubUserCorrection = {
  correction: string;
  skillName: string;
};

export type FinalTechProfile = {
  confirmedSkills: string[];
  focusAreas: string[];
};

export type GithubAnalysis = {
  createdAt: string;
  depthEstimates: DepthEstimate[];
  evidences: GithubEvidence[];
  finalTechProfile: FinalTechProfile;
  githubAnalysisId: string;
  repoSummaries: RepoSummary[];
  staticSignals: StaticSignals;
  techTags: TechTag[];
  userCorrections: GithubUserCorrection[];
  version: number;
};

export type GithubAnalysisRequest = {
  coreRepositoryIds: string[];
  githubConnectionId: string;
  selectedRepositoryIds: string[];
};

export type GithubAnalysisCorrectionRequest = {
  finalTechProfile: FinalTechProfile;
  userCorrections: GithubUserCorrection[];
};

export type GithubAnalysisCorrectionResponse = {
  finalTechProfile: FinalTechProfile;
  githubAnalysisId: string;
  savedAt: string;
};

export type GithubAnalysisSummary = {
  githubAnalysisId: string;
  version: number;
  summary: string;
  createdAt: string;
};
