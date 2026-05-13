package com.back.coach.external.github.dto;

/**
 * GraphQL contributionsCollection 으로 조회한, 사용자가 커밋을 기여한 public repo.
 * REST의 GithubRepoDto와 달리 다음 정보가 없다:
 *   - nodeId (GraphQL은 databaseId만 제공)
 *   - defaultBranch (별도 쿼리 필요, 본 구현에서는 null로 둠 → 후속 동기화 시 채움)
 *   - owner (nameWithOwner에서 추출 가능)
 */
public record GithubContributedRepoDto(
        Long githubRepoId,        // databaseId
        String fullName,          // nameWithOwner
        String htmlUrl,           // url
        String primaryLanguage,   // primaryLanguage.name (nullable)
        int contributionCount     // contributions.totalCount
) {}
