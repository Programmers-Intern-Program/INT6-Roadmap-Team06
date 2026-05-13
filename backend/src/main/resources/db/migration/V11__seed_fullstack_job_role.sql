INSERT INTO job_roles (role_code, role_name, description, is_active)
VALUES
    (
        'FULLSTACK_DEVELOPER',
        '풀스택 개발자',
        'React와 Next.js 기반 UI, Java와 Spring Boot 기반 API, 데이터 저장소와 배포 흐름을 함께 진단하는 v1 확장 직무',
        TRUE
    )
ON CONFLICT (role_code) DO UPDATE
SET role_name = EXCLUDED.role_name,
    description = EXCLUDED.description,
    is_active = EXCLUDED.is_active,
    updated_at = now();

WITH fullstack_role AS (
    SELECT id
    FROM job_roles
    WHERE role_code = 'FULLSTACK_DEVELOPER'
)
INSERT INTO skill_requirements (job_role_id, skill_name, category, importance)
SELECT fullstack_role.id, seed.skill_name, seed.category, seed.importance
FROM fullstack_role
CROSS JOIN (
    VALUES
        ('TypeScript', 'LANGUAGE', 5),
        ('React', 'FRONTEND', 5),
        ('Next.js', 'FRAMEWORK', 5),
        ('Java', 'LANGUAGE', 4),
        ('Spring Boot', 'FRAMEWORK', 4),
        ('REST API', 'API', 4),
        ('PostgreSQL', 'DATABASE', 3),
        ('Docker', 'INFRA', 3),
        ('Git/GitHub', 'COLLABORATION', 3),
        ('CI/CD', 'DELIVERY', 3)
) AS seed(skill_name, category, importance)
ON CONFLICT (job_role_id, skill_name) DO UPDATE
SET category = EXCLUDED.category,
    importance = EXCLUDED.importance,
    updated_at = now();
