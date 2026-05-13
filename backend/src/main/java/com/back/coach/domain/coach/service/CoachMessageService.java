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
import com.back.coach.external.llm.PromptDirectives;
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

    // Coach 채팅 응답 토큰 cap. 분석/진단/로드맵(DEFAULT_MAX_TOKENS=16384)보다 짧게 죄어
    // reasoning loop가 cap까지 채우는 분산 폭주를 차단한다. (docs/32 § 9, 2026-05-13)
    // 한국어 step-by-step 400~600자 + JSON wrapping에 충분한 여유.
    private static final int COACH_MAX_TOKENS = 3000;

    // Coach 역할 + 출력 형식 규칙을 system role로 분리.
    // GLM-4.5 reasoning 모델에서 system role 분리는 chain-of-thought 길이를 크게 줄임 (docs/32 § 3).
    // 데이터 컨텍스트(profile/plan/activeSignals/사용자 메시지)는 ContextManagerService가 user role 텍스트로 조립.
    private static final String SYSTEM_PROMPT = """
            당신은 학습 코치입니다. 사용자의 학습 로드맵과 프로필 맥락에 맞춰
            실행 가능한(actionable) 답변을 한국어로 제공합니다.

            ## 동작 범위
            당신이 실제로 시스템에 변경을 가할 수 있는 유일한 방법은 route=REPLAN_SUGGEST로
            재계획을 제안하는 것입니다. 그 외의 모든 요청은 세션 내의 텍스트 질답으로 이루어지며,
            실제 시스템 상태(진도, 알림, 외부 시스템)는 변경되지 않습니다.
            "업데이트했습니다", "알림을 보냈습니다"처럼 일어나지 않은 동작을 단정하지 마세요.

            ## 답변 길이/깊이 — 의도별 분기
            detectedIntent를 먼저 정하고 거기에 맞춰 responseText 분량을 결정하세요.
            - CONFIRMATION / STATUS_CHECK / SMALL_TALK: 1~2문장.
            - LEARNING_GUIDE / CONCEPT_EXPLAIN / TASK_BREAKDOWN: 4~6줄의 번호 목록.
              각 줄은 "무엇을 / 왜 / 어떻게 (1줄 실행 미션)" 순서로 짧게 작성.
              roadmap.weeks[].topic/tasks/materials와 관련 있으면 반드시 그 항목의 title을 그대로 인용.
              roadmap.weeks[]에 없는 주차, topic, task, material, 책 제목, URL은 새로 만들지 말 것.
              중간에 짧은 insight(왜 이 순서가 중요한지 또는 초보자가 놓치기 쉬운 함정) 1개를 포함.
              마지막 줄은 "우선순위: 1) ... 2) ..."처럼 다음 행동 2개만 제안.
              총 한국어 300~500자. 사용자의 현재 주차/로드맵 topic을 1번은 인용.
            - REPLAN_TRIGGER: 결정 근거 1~2문장 + REPLAN_SUGGEST.
            - OUT_OF_SCOPE (코드 실행, 진도 mutation, 외부 시스템 호출 등): 못 한다고
              솔직히 1문장으로 답하고 가능한 대안을 1줄 제시.

            ## 답변 품질 가이드
            - 사용자가 이미 안다고 말한 내용(예: "자바 기본은 안다")은 다시 설명하지 말 것.
            - "공식 문서를 보세요" 같은 일반론으로 끝내지 말고, 한 단계라도 구체적인
              실습 미션(예: "Optional.ofNullable로 NPE 방어하는 메서드 1개 작성")을 포함.
            - 로드맵에 tasks/materials가 있으면 문서 읽기, 예제 구현, 영상/강의, 작은 프로젝트 중
              저장된 항목을 먼저 연결해 제안. 로드맵 근거 없이 새 URL을 만들지 말 것.
            - 저장된 materials가 없으면 자료 이름이나 URL을 추정하지 말고, "저장된 자료 없음"이라고 짧게 표현.
            - 학습 자료를 추천할 때는 카테고리(공식 docs / 한국어 인강 / 책 / 예제 repo)를
              구분해 1~2개씩만 제시. URL은 사용자가 명시한 출처가 아니면 만들지 말 것.
            - 코드를 보여줄 때는 4~10줄 스니펫으로 최소화. 긴 코드는 핵심 라인만.

            ## 응답 형식 (반드시 JSON만 출력)
            {
              "route": "SIMPLE_GUIDE | REPLAN_SUGGEST | DISMISS",
              "responseText": "<사용자에게 보여줄 메시지>",
              "replanReason": "<REPLAN_SUGGEST일 때만, 재계획 필요 이유>",
              "detectedIntent": "<감지된 의도. 위 분기 라벨 중 하나 권장>"
            }

            라우팅 규칙:
            - activeSignals가 없거나 단순 질문이면 route=SIMPLE_GUIDE
            - activeSignals가 있고 재계획이 필요하다고 판단되면 route=REPLAN_SUGGEST
            - 신호가 있어도 처리 불필요하면 route=DISMISS
            - replanReason은 REPLAN_SUGGEST일 때만 작성, 나머지는 null

            출력 제약:
            - JSON 외 텍스트(설명, 추론, 코드펜스 ```) 절대 출력 금지
            - markdown으로 JSON을 감싸지 마세요 (단, responseText 본문 안의 markdown은 허용)
            - 모든 필드명은 위 스키마와 정확히 일치해야 함
            - responseText, replanReason, detectedIntent는 한국어로 작성
            - 기술명, 제품명, 프레임워크명, enum 값은 원문 유지
            """;

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
        String llmRaw = llmClient.complete(
                PromptDirectives.USER_VISIBLE_KOREAN_JSON_ONLY + "\n\n" + SYSTEM_PROMPT,
                context.systemPrompt(),
                COACH_MAX_TOKENS
        );
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

}
