package com.back.coach.domain.coach.service;

import com.back.coach.domain.coach.entity.ChatSession;
import com.back.coach.domain.coach.entity.CoachConversation;
import com.back.coach.domain.coach.repository.ChatSessionRepository;
import com.back.coach.domain.coach.repository.CoachConversationRepository;
import com.back.coach.domain.context.service.AssembledContext;
import com.back.coach.domain.context.service.ContextManagerService;
import com.back.coach.external.llm.LlmClient;
import com.back.coach.global.code.CoachRoute;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CoachMessageService {

    private final ChatSessionRepository chatSessionRepository;
    private final CoachConversationRepository coachConversationRepository;
    private final ContextManagerService contextManagerService;
    private final LlmClient llmClient;

    public CoachMessageService(
            ChatSessionRepository chatSessionRepository,
            CoachConversationRepository coachConversationRepository,
            ContextManagerService contextManagerService,
            LlmClient llmClient
    ) {
        this.chatSessionRepository = chatSessionRepository;
        this.coachConversationRepository = coachConversationRepository;
        this.contextManagerService = contextManagerService;
        this.llmClient = llmClient;
    }

    @Transactional
    public CoachConversation sendMessage(Long userId, Long sessionId, String userMessage) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ServiceException(ErrorCode.SESSION_NOT_FOUND));

        if (!session.isOwnedBy(userId)) {
            throw new ServiceException(ErrorCode.FORBIDDEN);
        }

        if (session.isClosed()) {
            throw new ServiceException(ErrorCode.SESSION_CLOSED);
        }

        // USER 메시지 저장 (LLM 실패하면 트랜잭션 롤백)
        coachConversationRepository.save(CoachConversation.user(sessionId, userId, userMessage));

        // 3-Tier Context 자동 조립 (활성 신호 있으면 Tier 3, 없으면 Tier 1)
        AssembledContext context = contextManagerService.assembleAuto(session, userMessage);
        String responseText = llmClient.complete(context.systemPrompt());

        // COACH 응답 저장 (route는 slice 4 전까지 SIMPLE_GUIDE 고정)
        CoachConversation coachMessage = CoachConversation.coach(
                sessionId, userId, responseText, CoachRoute.SIMPLE_GUIDE, null
        );
        return coachConversationRepository.save(coachMessage);
    }
}
