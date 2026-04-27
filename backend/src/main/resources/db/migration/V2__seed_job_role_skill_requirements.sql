INSERT INTO job_roles (role_code, role_name, description, is_active)
VALUES
    (
        'BACKEND_DEVELOPER',
        '백엔드 개발자',
        'Java와 Spring Boot 기반 API 서버, 데이터 저장소, 캐시, 배포 역량을 기준으로 진단하는 v1 기본 직무',
        TRUE
    )
ON CONFLICT (role_code) DO UPDATE
SET role_name = EXCLUDED.role_name,
    description = EXCLUDED.description,
    is_active = EXCLUDED.is_active,
    updated_at = now();

WITH backend_role AS (
    SELECT id
    FROM job_roles
    WHERE role_code = 'BACKEND_DEVELOPER'
)
INSERT INTO skill_requirements (job_role_id, skill_name, category, importance)
SELECT backend_role.id, seed.skill_name, seed.category, seed.importance
FROM backend_role
CROSS JOIN (
    VALUES
        ('Java', 'LANGUAGE', 5),
        ('Spring Boot', 'FRAMEWORK', 5),
        ('REST API', 'API', 5),
        ('JPA', 'DATA_ACCESS', 4),
        ('SQL', 'DATABASE', 4),
        ('PostgreSQL', 'DATABASE', 4),
        ('Redis', 'CACHE', 3),
        ('JUnit', 'TESTING', 3),
        ('Docker', 'INFRA', 3),
        ('Git/GitHub', 'COLLABORATION', 3)
) AS seed(skill_name, category, importance)
ON CONFLICT (job_role_id, skill_name) DO UPDATE
SET category = EXCLUDED.category,
    importance = EXCLUDED.importance,
    updated_at = now();
