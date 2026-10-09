package io.salesdoctor.spring_mcp.support;

/**
 * Tool'lardagi tekshiruvlar. Tashlangan istisnoni Spring AI isError = true javobga aylantiradi.
 */
public final class Require {

    private Require() {
    }

    /**
     * value null bo'lsa "... topilmadi" xatosini tashlaydi.
     */
    public static <T> T found(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    public static void that(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    public static String notBlank(String value, String message) {
        that(value != null && !value.isBlank(), message);
        return value;
    }
}
