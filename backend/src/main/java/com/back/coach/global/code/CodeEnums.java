package com.back.coach.global.code;

import java.util.Arrays;
import java.util.List;

public final class CodeEnums {

    private CodeEnums() {
    }

    public static <E extends Enum<E> & CodeEnum> E fromCode(Class<E> enumType, String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        return Arrays.stream(enumType.getEnumConstants())
                .filter(value -> value.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported code: " + code));
    }

    public static <E extends Enum<E> & CodeEnum> List<String> codes(Class<E> enumType) {
        return Arrays.stream(enumType.getEnumConstants())
                .map(CodeEnum::code)
                .toList();
    }
}
