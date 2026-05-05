package com.back.coach.external.llm;

import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;

public final class LlmJsonResponseExtractor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private LlmJsonResponseExtractor() {
    }

    public static String extractJson(String response) {
        if (response == null || response.isBlank()) {
            throw invalidResponse();
        }

        String text = stripWrappingFence(response.trim());
        String json = findFirstValidJson(text);
        if (json == null) {
            throw invalidResponse();
        }
        return json;
    }

    private static String stripWrappingFence(String text) {
        if (!text.startsWith("```")) {
            return text;
        }

        int firstLineEnd = text.indexOf('\n');
        if (firstLineEnd < 0) {
            return text;
        }

        int closingFence = text.indexOf("```", firstLineEnd + 1);
        if (closingFence < 0) {
            return text;
        }

        return text.substring(firstLineEnd + 1, closingFence).trim();
    }

    private static String findFirstValidJson(String text) {
        for (int start = 0; start < text.length(); start++) {
            char current = text.charAt(start);
            if (current != '{' && current != '[') {
                continue;
            }

            String candidate = scanJsonValue(text, start);
            if (candidate != null && isJson(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static String scanJsonValue(String text, int start) {
        Deque<Character> expectedClosers = new ArrayDeque<>();
        pushExpectedCloser(expectedClosers, text.charAt(start));

        boolean inString = false;
        boolean escaped = false;
        for (int index = start + 1; index < text.length(); index++) {
            char current = text.charAt(index);

            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }

            if (current == '"') {
                inString = true;
                continue;
            }
            if (current == '{' || current == '[') {
                pushExpectedCloser(expectedClosers, current);
                continue;
            }
            if (current == '}' || current == ']') {
                if (expectedClosers.isEmpty() || expectedClosers.pop() != current) {
                    return null;
                }
                if (expectedClosers.isEmpty()) {
                    return text.substring(start, index + 1).trim();
                }
            }
        }

        return null;
    }

    private static void pushExpectedCloser(Deque<Character> expectedClosers, char opener) {
        expectedClosers.push(opener == '{' ? '}' : ']');
    }

    private static boolean isJson(String candidate) {
        try {
            MAPPER.readTree(candidate);
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    private static ServiceException invalidResponse() {
        return new ServiceException(ErrorCode.LLM_INVALID_RESPONSE);
    }
}
