export type GithubConnection = {
  githubConnectionId: string;
  githubLogin: string;
  connectedAt: string;
};

export type Repository = {
  repositoryId: string;
  repoFullName: string;
  repoUrl: string;
  primaryLanguage: string | null;
  defaultBranch: string;
};

export type GithubRepositoryList = {
  githubConnectionId: string;
  repositories: Repository[];
};

export type GithubAnalysisResult = {
  githubAnalysisId: string;
};
