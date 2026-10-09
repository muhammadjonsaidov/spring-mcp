package io.salesdoctor.spring_mcp.dto;

import java.math.BigDecimal;

public record TopProductRow(Long productId, String name, BigDecimal totalQuantity, BigDecimal totalRevenue) {
}
