package com.back.coach.domain.coach.service;

import com.back.coach.domain.coach.entity.ChatSession;
import com.back.coach.domain.coach.entity.CoachConversation;
import com.back.coach.domain.coach.entity.ReplanProposal;
import com.back.coach.domain.coach.repository.ChatSessionRepository;
import com.back.coach.domain.coach.repository.CoachConversationRepository;
import com.back.coach.domain.coach.repository.ReplanProposalRepository;
import com.back.coach.domain.context.service.AssembledContext;
import com.back.coach.domain.context.service.ContextManagerService;
import com.back.coach.external.llm.LlmClient;
import com.back.coach.global.code.CoachRoute;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class CoachMessageService {

    private static final int PROPOSAL_TTL_HOURS = 24;

    private final ChatSessionRepository chatSessionRepository;
    private final CoachConversationRepository coachConversationRepository;
    private final ReplanProposalRepository replanProposalRepository;
    private final ContextManagerService contextManagerService;
    private final CoachResponseParser responseParser;
    private final LlmClient llmClient;

    public CoachMessageService(
            ChatSessionRepository chatSessionRepository,
            CoachConversationRepository coachConversationRepository,
            ReplanProposalRepository replanProposalRepository,
            ContextManagerService contextManagerService,
            CoachResponseParser responseParser,
            LlmClient llmClient
    ) {
        this.chatSessionRepository = chatSessionRepository;
        this.coachConversationRepository = coachConversationRepository;
        this.replanProposalRepository = replanProposalRepository;
        this.contextManagerService = contextManagerService;
        this.responseParser = responseParser;
        this.llmClient = llmClient;
    }

    public record MessageResult(CoachConversation coachMessage, ReplanProposal proposal) {}

    @Transactional(readOnly = true)
    public List<CoachConversation> getMessages(Long userId, Long sessionId) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ServiceException(ErrorCode.SESSION_NOT_FOUND));

        if (!session.isOwnedBy(userId)) {
            throw new ServiceException(ErrorCode.FORBIDDEN);
        }

        return coachConversationRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    @Transactional
    public MessageResult sendMessage(Long userId, Long sessionId, String userMessage) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ServiceException(ErrorCode.SESSION_NOT_FOUND));

        if (!session.isOwnedBy(userId)) {
            throw new ServiceException(ErrorCode.FORBIDDEN);
        }

        if (session.isClosed()) {
            throw new ServiceException(ErrorCode.SESSION_CLOSED);
        }

        coachConversationRepository.save(CoachConversation.user(sessionId, userId, userMessage));

        AssembledContext context = contextManagerService.assembleAuto(session, userMessage);
        String llmRaw = llmClient.complete(buildPrompt(context, userMessage));
        CoachResponseParser.ParsedCoachResponse parsed = responseParser.parse(llmRaw);

        CoachConversation coachMessage = CoachConversation.coach(
                sessionId, userId, parsed.responseText(), parsed.route(), parsed.detectedIntent()
        );
        coachMessage = coachConversationRepository.save(coachMessage);

        ReplanProposal proposal = null;
        if (parsed.route() == CoachRoute.REPLAN_SUGGEST && parsed.replanReason() != null) {
            proposal = replanProposalRepository.save(ReplanProposal.create(
                    sessionId, userId, coachMessage.getId(),
                    parsed.replanReason(),
                    Instant.now().plus(PROPOSAL_TTL_HOURS, ChronoUnit.HOURS)
            ));
        }

        return new MessageResult(coachMessage, proposal);
    }

    private String buildPrompt(AssembledContext context, String userMessage) {
        return context.systemPrompt() + """

                ## 응답 형식 (반드시 JSON만 출력)
                {
                  "route": "SIMPLE_GUIDE | REPLAN_SUGGEST | DISMISS",
                  "responseText": "<사용자에게 보여줄 메시지>",
                  "replanReason": "<REPLAN_SUGGEST일 때만, 재계획 필요 이유>",
                  "detectedIntent": "<감지된 의도 (선택)>"
                }

                규칙:
                - activeSignals가 없거나 단순 질문이면 route=SIMPLE_GUIDE
                - activeSignals가 있고 재계획이 필요하다고 판단되면 route=REPLAN_SUGGEST
                - 신호가 있어도 처리 불필요하면 route=DISMISS
                - replanReason은 REPLAN_SUGGEST일 때만 작성, 나머지는 null
                """;
    }
}
