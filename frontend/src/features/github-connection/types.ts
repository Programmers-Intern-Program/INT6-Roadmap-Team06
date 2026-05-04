export type GithubConnectionRequest = {
  authorizationCode: string;
};

export type GithubConnectionResponse = {
  connectedAt: string;
  githubConnectionId: string;
  githubLogin: string;
};

export type GithubRepository = {
  defaultBranch?: string | null;
  primaryLanguage?: string | null;
  repoFullName: string;
  repoUrl: string;
  repositoryId: string;
};

export type GithubRepositoryListResponse = {
  githubConnectionId: string;
  repositories: GithubRepository[];
};
