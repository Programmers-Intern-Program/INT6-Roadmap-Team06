package com.back.coach.service.github.summary;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiffPreprocessorTest {

    private final DiffPreprocessor preprocessor = new DiffPreprocessor();

    @Test
    @DisplayName("일반 소스 파일의 hunk는 그대로 유지된다")
    void clean_keepsNormalSourceHunks() {
        String diff = """
                diff --git a/src/main/java/X.java b/src/main/java/X.java
                index abc..def 100644
                --- a/src/main/java/X.java
                +++ b/src/main/java/X.java
                @@ -1,3 +1,5 @@
                 line1
                +new line
                 line2
                """;

        String cleaned = preprocessor.clean(diff);

        assertThat(cleaned).contains("X.java").contains("+new line");
    }

    @Test
    @DisplayName("lockfile hunk는 통째로 drop된다")
    void clean_dropsLockfileHunks() {
        String diff = """
                diff --git a/src/X.java b/src/X.java
                @@ -1,1 +1,1 @@
                -old
                +new
                diff --git a/package-lock.json b/package-lock.json
                @@ -1,100 +1,110 @@
                 noisy lockfile content
                +more noise
                diff --git a/yarn.lock b/yarn.lock
                @@ -1,1 +1,1 @@
                +y
                """;

        String cleaned = preprocessor.clean(diff);

        assertThat(cleaned).contains("X.java");
        assertThat(cleaned).doesNotContain("package-lock.json").doesNotContain("yarn.lock");
        assertThat(cleaned).doesNotContain("noisy lockfile");
    }

    @Test
    @DisplayName("생성/번들/바이너리/vendored 경로는 모두 drop된다")
    void clean_dropsGeneratedBinaryVendored() {
        String diff = """
                diff --git a/dist/bundle.js b/dist/bundle.js
                @@ -1 +1 @@
                +bundled
                diff --git a/src/app.min.js b/src/app.min.js
                @@ -1 +1 @@
                +minified
                diff --git a/assets/logo.png b/assets/logo.png
                @@ -1 +1 @@
                Binary files differ
                diff --git a/vendor/lib/x.js b/vendor/lib/x.js
                @@ -1 +1 @@
                +vendored
                diff --git a/node_modules/foo/index.js b/node_modules/foo/index.js
                @@ -1 +1 @@
                +npm
                diff --git a/src/Real.java b/src/Real.java
                @@ -1 +1 @@
                +keep
                """;

        String cleaned = preprocessor.clean(diff);

        assertThat(cleaned).contains("Real.java").contains("+keep");
        assertThat(cleaned).doesNotContain("bundle.js").doesNotContain("app.min.js");
        assertThat(cleaned).doesNotContain("logo.png").doesNotContain("vendor/lib").doesNotContain("node_modules");
    }

    @Test
    @DisplayName("빈 diff는 빈 문자열을 반환한다")
    void clean_emptyInput() {
        assertThat(preprocessor.clean("")).isEmpty();
        assertThat(preprocessor.clean(null)).isEmpty();
    }
}
