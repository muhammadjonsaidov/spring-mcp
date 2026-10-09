package io.salesdoctor.spring_mcp.support;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

/**
 * Ilova va baza bir xil vaqt mintaqasida ishlashi uchun yagona manba.
 * Baza sessiyasi ham shu mintaqaga o'rnatiladi (application.yml: connection-init-sql).
 */
public final class AppTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Tashkent");

    private AppTime() {
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    public static LocalDate toLocalDate(OffsetDateTime dateTime) {
        return dateTime == null ? today() : dateTime.atZoneSameInstant(ZONE).toLocalDate();
    }

    /**
     * YYYY-MM-DD formatidagi sanani o'qiydi; bo'sh bo'lsa defaultValue qaytaradi.
     *
     * @throws IllegalArgumentException sana noto'g'ri formatda bo'lsa
     */
    public static LocalDate parseDate(String value, LocalDate defaultValue) {
        if (value == null || value.isBlank()) return defaultValue;
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Sana noto'g'ri formatda: '" + value + "'. Kutilgan format: YYYY-MM-DD");
        }
    }
}
