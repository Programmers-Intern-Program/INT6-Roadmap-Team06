package com.back.coach.service.github.summary;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Stage 2 입력 전처리. unified diff에서 노이즈 파일(lockfile/generated/binary/vendored)의 hunk를 통째로 drop.
// 언어 불가지론적 — 파일 경로만 본다. Hunk 단위 cleanup(import-only 등)은 Slice 4.
@Component
public class DiffPreprocessor {

    // diff --git a/<path> b/<path>
    private static final Pattern DIFF_HEADER = Pattern.compile("^diff --git a/(\\S+) b/\\S+", Pattern.MULTILINE);

    public static final List<Predicate<String>> BLACKLISTED_PATH_PATTERNS = List.of(
            // Lockfiles
            exactName("package-lock.json"),
            exactName("yarn.lock"),
            exactName("pnpm-lock.yaml"),
            exactName("Cargo.lock"),
            exactName("Gemfile.lock"),
            exactName("go.sum"),
            exactName("poetry.lock"),
            exactName("gradle.lockfile"),
            // Generated/bundled
            suffix(".min.js"), suffix(".min.css"), suffix(".map"), suffix(".snap"),
            prefix("dist/"), prefix("build/"),
            // Binary
            suffix(".png"), suffix(".jpg"), suffix(".jpeg"), suffix(".gif"), suffix(".ico"), suffix(".pdf"),
            // Vendored
            prefix("vendor/"), prefix("node_modules/")
    );

    public String clean(String unifiedDiff) {
        if (unifiedDiff == null || unifiedDiff.isEmpty()) return "";

        StringBuilder out = new StringBuilder();
        // diff --git 라인 위치를 찾아 block 단위로 자른다.
        Matcher m = DIFF_HEADER.matcher(unifiedDiff);
        int[] starts = matcherStarts(m);
        if (starts.length == 0) return unifiedDiff; // diff --git 헤더 없는 raw text는 그대로

        m.reset();
        int idx = 0;
        while (m.find()) {
            String path = m.group(1);
            int blockStart = m.start();
            int blockEnd = (idx + 1 < starts.length) ? starts[idx + 1] : unifiedDiff.length();
            if (!isBlacklisted(path)) {
                out.append(unifiedDiff, blockStart, blockEnd);
            }
            idx++;
        }
        return out.toString();
    }

    private static int[] matcherStarts(Matcher m) {
        java.util.List<Integer> list = new java.util.ArrayList<>();
        while (m.find()) list.add(m.start());
        int[] arr = new int[list.size()];
        for (int i = 0; i < list.size(); i++) arr[i] = list.get(i);
        return arr;
    }

    private static boolean isBlacklisted(String path) {
        for (Predicate<String> rule : BLACKLISTED_PATH_PATTERNS) {
            if (rule.test(path)) return true;
        }
        return false;
    }

    private static Predicate<String> exactName(String fileName) {
        return p -> p.equals(fileName) || p.endsWith("/" + fileName);
    }

    private static Predicate<String> suffix(String suffix) {
        return p -> p.toLowerCase().endsWith(suffix);
    }

    private static Predicate<String> prefix(String prefix) {
        return p -> p.startsWith(prefix) || p.contains("/" + prefix);
    }
}
