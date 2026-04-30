import { DiagnosisDetailView } from "@/features/diagnosis/diagnosis-detail-view";

type DiagnosisDetailPageProps = {
  params: Promise<{
    diagnosisId: string;
  }>;
};

export default async function DiagnosisDetailPage({
  params
}: DiagnosisDetailPageProps) {
  const { diagnosisId } = await params;

  return <DiagnosisDetailView diagnosisId={diagnosisId} />;
}
