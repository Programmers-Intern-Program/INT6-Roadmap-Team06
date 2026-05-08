import { RoadmapCreateView } from "@/features/roadmap/roadmap-create-view";

type Props = {
  searchParams: Promise<{ diagnosisId?: string }>;
};

export default async function RoadmapCreatePage({ searchParams }: Props) {
  const { diagnosisId } = await searchParams;
  return <RoadmapCreateView initialDiagnosisId={diagnosisId} />;
}
