package com.back.coach.domain.coach.service;

import com.back.coach.domain.coach.entity.ChatSession;
import com.back.coach.domain.coach.entity.CoachConversation;
import com.back.coach.domain.coach.repository.ChatSessionRepository;
import com.back.coach.domain.coach.repository.CoachConversationRepository;
import com.back.coach.external.llm.LlmClient;
import com.back.coach.global.code.CoachRoute;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CoachMessageService {

    private static final String SIMPLE_GUIDE_PROMPT = """
            당신은 학습 코치입니다. 사용자의 질문에 짧고 실용적인 답변을 한국어로 제공합니다.
            아직 사용자의 프로필이나 로드맵 컨텍스트는 주입되지 않았습니다 (다음 슬라이스에서 추가).

            사용자 메시지: %s
            """;

    private final ChatSessionRepository chatSessionRepository;
    private final CoachConversationRepository coachConversationRepository;
    private final LlmClient llmClient;

    public CoachMessageService(
            ChatSessionRepository chatSessionRepository,
            CoachConversationRepository coachConversationRepository,
            LlmClient llmClient
    ) {
        this.chatSessionRepository = chatSessionRepository;
        this.coachConversationRepository = coachConversationRepository;
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

        // USER 메시지 저장 (LLM 실패해도 사용자 메시지는 남김)
        coachConversationRepository.save(CoachConversation.user(sessionId, userId, userMessage));

        String prompt = SIMPLE_GUIDE_PROMPT.formatted(userMessage);
        String responseText = llmClient.complete(prompt);

        // COACH 응답 저장 (route는 slice 4 전까지 SIMPLE_GUIDE 고정)
        CoachConversation coachMessage = CoachConversation.coach(
                sessionId, userId, responseText, CoachRoute.SIMPLE_GUIDE, null
        );
        return coachConversationRepository.save(coachMessage);
    }
}
