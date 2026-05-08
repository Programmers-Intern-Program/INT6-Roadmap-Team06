# 정렬본 문서 묶음 안내

문서 순서
- 01_service_design_aligned.md
- 02_data_model_aligned.md
- 03_api_spec_aligned.md
- 04_function_spec_aligned.md
- 05_architecture_aligned.md
- 06_requirements_aligned.md
- 07_user_flow_screen_design_aligned.md
- 08_db_physical_schema.md
- 09_contract_appendix.md
- 10_b_role_v1_handoff.md

권장 읽기 순서
1. 01 서비스 설계
2. 06 요구사항
3. 04 기능 명세
4. 07 화면 설계
5. 03 API 명세
6. 02 데이터 모델
7. 08 DB 물리 스키마
8. 09 계약 부록
9. 05 아키텍처

보조 문서
- 10_b_role_v1_handoff.md: B 역할 v1 저장/상태/재조회/대시보드 마감 체크와 v2 저장/상태 구현 범위 정리
- 13_v1_smoke_test_checklist.md: v1 전체 화면 흐름 수동검증 체크리스트와 blocker 기록 기준
- 14_v1_smoke_test_result_2026-05-04.md: 2026-05-04 로컬 v1 smoke 실행 결과와 blocker 기록
- 15_v1_smoke_test_result_2026-05-05.md: 2026-05-05 OAuth/LLM 파서 수정 후 재검증 결과
- 16_v2_context_snapshot_contract.md: v2 Context Snapshot payload, 원본 참조, version/active 규칙
- 17_v2_context_tier_assembly.md: v2 3-Tier Context 조립 기준과 템플릿별 기본 Tier
- 18_v2_session_version_policy.md: v2 Coach 세션의 snapshot version 고정과 새 version 반영 정책
- 19_v2_detected_patterns_contract.md: v2 Pattern Detector 감지 원본과 activeSignals 요약 계약
- 20_v2_coach_api_contract.md: v2 Coach API 계약 — 세션/메시지/재계획 엔드포인트, 4경로 분기, Context 호출 기준
- 21_v2_db_schema_ddl.md: v2 신규 테이블 DDL — user_context_snapshots, chat_sessions, coach_conversations, replan_proposals, detected_patterns, agent_events
- 22_v1_smoke_test_result_2026-05-06.md: 2026-05-06 v1 full smoke 최종 결과 — 핵심 흐름 PASS
- 23_v1_smoke_debug_2026-05-06.md: 2026-05-06 smoke 디버그 세션 — 버그 5종 수정 기록
- 24_v1_demo_smoke_result_2026-05-08.md: 2026-05-08 v1 시연 핵심 저장/재조회 자동 검증 결과
