package io.salesdoctor.spring_mcp.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Qaytarish holatlari. Faqat PENDING dan APPROVED yoki REJECTED ga o'tiladi.
 * Bazada (returns.status) name() ko'rinishida saqlanadi.
 */
public enum ReturnStatus {

    PENDING,
    APPROVED,
    REJECTED;

    /** Tool tavsiflari uchun; ReturnStatusTest enum bilan mosligini tekshiradi. */
    public static final String ALLOWED = "PENDING, APPROVED, REJECTED";

    public Set<ReturnStatus> nextStatuses() {
        return switch (this) {
            case PENDING -> EnumSet.of(APPROVED, REJECTED);
            case APPROVED, REJECTED -> EnumSet.noneOf(ReturnStatus.class);
        };
    }

    public boolean canTransitionTo(ReturnStatus target) {
        return nextStatuses().contains(target);
    }

    public static ReturnStatus initial() {
        return PENDING;
    }

    /**
     * Buyurtmadan qaytarilgan miqdorni hisoblashda inobatga olinadigan holatlar
     * (rad etilganlar hisobga olinmaydi).
     */
    public static Set<ReturnStatus> reservingQuantity() {
        return EnumSet.of(PENDING, APPROVED);
    }

    public static ReturnStatus parse(String value) {
        return Enums.parse(ReturnStatus.class, value, null, "holat");
    }
}
