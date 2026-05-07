-- ============================================================
-- V7__coach_conversation_summary_role.sql
-- coach_conversations.role에 SUMMARY 값 추가
-- 세션 내 오래된 대화를 LLM으로 압축한 요약 행을 저장하기 위함
-- ============================================================

ALTER TABLE coach_conversations
    DROP CONSTRAINT ck_cc_role;

ALTER TABLE coach_conversations
    ADD CONSTRAINT ck_cc_role
        CHECK (role IN ('USER', 'COACH', 'SUMMARY'));
