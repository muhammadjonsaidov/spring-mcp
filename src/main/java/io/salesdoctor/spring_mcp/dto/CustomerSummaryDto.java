package io.salesdoctor.spring_mcp.dto;

import java.math.BigDecimal;

public record CustomerSummaryDto(Long customerId, String name, BigDecimal debt,
                                 int orderCount, BigDecimal totalPurchases) {
}
