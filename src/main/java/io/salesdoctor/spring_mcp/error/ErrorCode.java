package io.salesdoctor.spring_mcp.error;

/**
 * Tool xatolarining toifalari. Mijoz (AI) shu kod bo'yicha nima qilishni tanlaydi:
 * INVALID_ARGUMENT - parametrni tuzatish, NOT_FOUND - boshqa ID, CONFLICT - holatni tekshirish.
 */
public enum ErrorCode {

    /** Parametr noto'g'ri yoki yetishmaydi */
    INVALID_ARGUMENT,
    /** So'ralgan yozuv mavjud emas */
    NOT_FOUND,
    /** Biznes qoidasi yoki joriy holat amalga ruxsat bermaydi (qoldiq yetmaydi, holat yakuniy va h.k.) */
    CONFLICT,
    /** Kutilmagan server xatoligi */
    INTERNAL_ERROR
}
