package io.salesdoctor.spring_mcp.support;

import io.salesdoctor.spring_mcp.error.ToolException;

/**
 * Tool'lardagi tekshiruvlar. Tashlangan ToolException yagona xato formatiga aylantiriladi.
 */
public final class Require {

    private Require() {
    }

    /**
     * value null bo'lsa NOT_FOUND.
     */
    public static <T> T found(T value, String message) {
        if (value == null) {
            throw ToolException.notFound(message);
        }
        return value;
    }

    /**
     * Yangilangan/o'chirilgan qatorlar soni 0 bo'lsa NOT_FOUND.
     */
    public static void affected(int rows, String message) {
        if (rows == 0) {
            throw ToolException.notFound(message);
        }
    }

    /**
     * Shart bajarilmasa INVALID_ARGUMENT.
     */
    public static void that(boolean condition, String message) {
        if (!condition) {
            throw ToolException.invalid(message);
        }
    }

    public static String notBlank(String value, String message) {
        that(value != null && !value.isBlank(), message);
        return value;
    }
}
