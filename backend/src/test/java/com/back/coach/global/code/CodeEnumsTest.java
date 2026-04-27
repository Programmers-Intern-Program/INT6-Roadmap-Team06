package com.back.coach.global.code;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CodeEnumsTest {

    @Test
    void fromCode_returnsMatchedEnum() {
        assertThat(CodeEnums.fromCode(CurrentLevel.class, "JUNIOR"))
                .isEqualTo(CurrentLevel.JUNIOR);
    }

    @Test
    void fromCode_rejectsBlankCode() {
        assertThatThrownBy(() -> CodeEnums.fromCode(CurrentLevel.class, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("code must not be blank");

        assertThatThrownBy(() -> CodeEnums.fromCode(CurrentLevel.class, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("code must not be blank");
    }

    @Test
    void fromCode_rejectsUnsupportedCode() {
        assertThatThrownBy(() -> CodeEnums.fromCode(CurrentLevel.class, "SENIOR"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported code: SENIOR");
    }

    @Test
    void codes_returnsContractValues() {
        assertCodes(AuthProvider.class, "LOCAL", "GOOGLE", "GITHUB");
        assertCodes(CurrentLevel.class, "BEGINNER", "BASIC", "JUNIOR", "INTERMEDIATE", "ADVANCED");
        assertCodes(ProficiencyLevel.class, "NONE", "BASIC", "WORKING", "STRONG");
        assertCodes(SkillSourceType.class, "USER_INPUT", "GITHUB_ESTIMATED", "SYSTEM_DERIVED");
        assertCodes(DiagnosisSeverity.class, "LOW", "MEDIUM", "HIGH");
        assertCodes(GithubAccessType.class, "OAUTH");
        assertCodes(GithubEvidenceType.class, "README", "CODE", "CONFIG", "REPO_METADATA", "COMMIT");
        assertCodes(GithubDepthLevel.class, "INTRO", "APPLIED", "PRACTICAL", "DEEP");
        assertCodes(ProgressStatus.class, "TODO", "IN_PROGRESS", "DONE", "SKIPPED");
        assertCodes(RoadmapTaskType.class, "READ_DOCS", "BUILD_EXAMPLE", "WRITE_NOTE", "APPLY_PROJECT", "REVIEW");
        assertCodes(MaterialType.class, "DOCS", "ARTICLE", "REPOSITORY", "VIDEO", "TEMPLATE");
        assertCodes(JobStatus.class, "REQUESTED", "RUNNING", "SUCCEEDED", "FAILED");
    }

    @SafeVarargs
    private static <E extends Enum<E> & CodeEnum> void assertCodes(Class<E> enumType, String... expectedCodes) {
        assertThat(CodeEnums.codes(enumType))
                .containsExactly(expectedCodes);

        assertThat(enumType.getEnumConstants())
                .allSatisfy(value -> assertThat(value.code()).isEqualTo(value.name()));
    }
}
