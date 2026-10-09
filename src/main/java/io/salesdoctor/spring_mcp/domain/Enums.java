package io.salesdoctor.spring_mcp.domain;

import io.salesdoctor.spring_mcp.error.ToolException;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Tool'lardan kelgan matnni enum'ga aylantirish uchun umumiy yordamchi.
 */
public final class Enums {

    private Enums() {
    }

    /**
     * Katta-kichik harfga e'tibor bermay o'qiydi; bo'sh qiymat uchun defaultValue qaytaradi.
     *
     * @throws ToolException qiymat enum'da bo'lmasa (ruxsat etilganlar ro'yxati bilan)
     */
    public static <E extends Enum<E>> E parse(Class<E> type, String value, E defaultValue, String label) {
        if (value == null || value.isBlank()) {
            if (defaultValue != null) return defaultValue;
            throw ToolException.invalid(label + " ko'rsatilmagan. Ruxsat etilgan: " + allowed(type));
        }

        String normalized = value.trim().toUpperCase();
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equals(normalized)) return constant;
        }
        throw ToolException.invalid(
                "Noto'g'ri " + label + ": '" + value + "'. Ruxsat etilgan: " + allowed(type));
    }

    /**
     * "A, B, C" ko'rinishidagi ro'yxat.
     */
    public static <E extends Enum<E>> String allowed(Class<E> type) {
        return Arrays.stream(type.getEnumConstants())
                .map(Enum::name)
                .collect(Collectors.joining(", "));
    }
}
