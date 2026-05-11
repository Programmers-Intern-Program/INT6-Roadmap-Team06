import { GithubAnalysisListView } from "@/features/github-analysis/github-analysis-list-view";
import { GithubAnalysisView } from "@/features/github-analysis/github-analysis-view";

type GithubAnalysisPageProps = {
  searchParams: Promise<{
    githubAnalysisId?: string | string[];
  }>;
};

export default async function GithubAnalysisPage({
  searchParams
}: GithubAnalysisPageProps) {
  const { githubAnalysisId } = await searchParams;
  const id = getFirstQueryValue(githubAnalysisId);

  if (!id) {
    return <GithubAnalysisListView />;
  }

  return <GithubAnalysisView initialGithubAnalysisId={id} />;
}

function getFirstQueryValue(value?: string | string[]) {
  if (Array.isArray(value)) {
    return value[0] ?? null;
  }

  return value ?? null;
}
