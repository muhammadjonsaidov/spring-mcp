package io.salesdoctor.spring_mcp.domain;

/**
 * To'lov usullari. Bazada (payments.payment_method) name() ko'rinishida saqlanadi.
 */
public enum PaymentMethod {

    CASH,
    CARD,
    TRANSFER;

    /** Tool tavsiflari uchun; PaymentMethodTest enum bilan mosligini tekshiradi. */
    public static final String ALLOWED = "CASH, CARD, TRANSFER";

    public static final PaymentMethod DEFAULT = CASH;

    /**
     * Bo'sh qiymat uchun DEFAULT qaytaradi.
     */
    public static PaymentMethod parseOrDefault(String value) {
        return Enums.parse(PaymentMethod.class, value, DEFAULT, "to'lov usuli");
    }
}
