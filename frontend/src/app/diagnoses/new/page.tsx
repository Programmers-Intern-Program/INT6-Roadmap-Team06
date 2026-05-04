import { DiagnosisCreateView } from "@/features/diagnosis/diagnosis-create-view";
import { redirect } from "next/navigation";

type Props = {
  searchParams: Promise<{ githubAnalysisId?: string }>;
};

export default async function DiagnosisCreatePage({ searchParams }: Props) {
  const { githubAnalysisId } = await searchParams;
  if (!githubAnalysisId) redirect("/github/analysis");
  return <DiagnosisCreateView githubAnalysisId={githubAnalysisId} />;
}
