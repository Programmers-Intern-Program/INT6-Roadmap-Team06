import { RoadmapDetailView } from "@/features/roadmap/roadmap-detail-view";

type RoadmapDetailPageProps = {
  params: Promise<{
    roadmapId: string;
  }>;
};

export default async function RoadmapDetailPage({
  params
}: RoadmapDetailPageProps) {
  const { roadmapId } = await params;

  return <RoadmapDetailView roadmapId={roadmapId} />;
}
