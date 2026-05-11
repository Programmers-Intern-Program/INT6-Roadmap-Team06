import { CoachChatView } from "@/features/coach/coach-chat-view";

type CoachSessionPageProps = {
  params: Promise<{ sessionId: string }>;
};

export default async function CoachSessionPage({ params }: CoachSessionPageProps) {
  const { sessionId } = await params;
  return <CoachChatView sessionId={sessionId} />;
}
