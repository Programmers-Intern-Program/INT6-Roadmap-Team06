-- ============================================================
-- V6__pattern_detector_idempotency.sql
-- detected_patterns에 idempotency_key 컬럼 추가
-- ============================================================

ALTER TABLE detected_patterns
    ADD COLUMN idempotency_key VARCHAR(100);

-- 같은 (user, type, key)에 대해 미처리 row가 1개만 존재하도록 강제
CREATE UNIQUE INDEX uq_dp_user_type_ikey_unprocessed
    ON detected_patterns (user_id, pattern_type, idempotency_key)
    WHERE processed_at IS NULL;
