export type CoachSession = {
  sessionId: string;
  profileVersion: number;
  roadmapVersion: number;
  startedAt: string;
};

export type CoachRoute =
  | "SIMPLE_GUIDE"
  | "REPLAN_PROPOSAL"
  | "PATTERN_INSIGHT"
  | "DEEP_DIVE";

export type ReplanProposal = {
  proposalId: string;
  reason: string;
  expiresAt: string;
};

export type CoachMessageResponse = {
  messageId: string;
  responseText: string;
  route: CoachRoute;
  replanProposal: ReplanProposal | null;
};

export type ReplanResult = {
  newRoadmapId: string | null;
  newRoadmapVersion: number | null;
  message: string;
  dismissed: boolean;
};

export type ChatBubble = {
  id: string;
  role: "USER" | "COACH";
  text: string;
  route?: CoachRoute;
  replanProposal?: ReplanProposal | null;
  pending?: boolean;
};
