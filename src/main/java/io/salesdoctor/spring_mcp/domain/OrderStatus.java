package io.salesdoctor.spring_mcp.domain;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Buyurtma holatlari va ular orasidagi ruxsat etilgan o'tishlar.
 * Bazada (orders.status) name() ko'rinishida saqlanadi.
 */
public enum OrderStatus {

    NEW,
    CONFIRMED,
    DELIVERED,
    CANCELLED;

    /**
     * Tool tavsiflari uchun (annotatsiyada faqat konstanta ishlatish mumkin).
     * OrderStatusTest bu matn enum bilan mos kelishini tekshiradi.
     */
    public static final String ALLOWED = "NEW, CONFIRMED, DELIVERED, CANCELLED";
    public static final String TRANSITIONS_TEXT =
            "NEW -> CONFIRMED/DELIVERED/CANCELLED, CONFIRMED -> DELIVERED/CANCELLED";

    /**
     * Shu holatdan o'tish mumkin bo'lgan holatlar. Bo'sh to'plam - yakuniy holat
     * (yetkazilgan buyurtma qaytarish orqali tuzatiladi).
     */
    public Set<OrderStatus> nextStatuses() {
        return switch (this) {
            case NEW -> EnumSet.of(CONFIRMED, DELIVERED, CANCELLED);
            case CONFIRMED -> EnumSet.of(DELIVERED, CANCELLED);
            case DELIVERED, CANCELLED -> EnumSet.noneOf(OrderStatus.class);
        };
    }

    public boolean canTransitionTo(OrderStatus target) {
        return nextStatuses().contains(target);
    }

    public boolean isFinal() {
        return nextStatuses().isEmpty();
    }

    /**
     * Hisobotlar, qarz va to'lovlarda hisobga olinadimi.
     */
    public boolean isActive() {
        return this != CANCELLED;
    }

    /**
     * Shu holatdagi buyurtma bo'yicha mahsulot qaytarish mumkinmi.
     */
    public boolean allowsReturns() {
        return this == DELIVERED;
    }

    public static OrderStatus initial() {
        return NEW;
    }

    public static Set<OrderStatus> active() {
        return EnumSet.complementOf(EnumSet.of(CANCELLED));
    }

    public static OrderStatus parse(String value) {
        return Enums.parse(OrderStatus.class, value, null, "holat");
    }

    /**
     * nextStatuses() dan qurilgan matn (TRANSITIONS_TEXT bilan solishtirish uchun).
     */
    static String describeTransitions() {
        return EnumSet.allOf(OrderStatus.class).stream()
                .filter(s -> !s.isFinal())
                .map(s -> s + " -> " + s.nextStatuses().stream()
                        .map(Enum::name)
                        .collect(Collectors.joining("/")))
                .collect(Collectors.joining(", "));
    }
}
