package io.salesdoctor.spring_mcp.dto;

import java.math.BigDecimal;

/**
 * territoryId = null bo'lgan qator - hududi yo'q mijozlar ("Hududsiz").
 */
public record TerritorySalesRow(Long territoryId, String territoryName, Long parentId,
                                int orderCount, BigDecimal totalAmount) {
}
