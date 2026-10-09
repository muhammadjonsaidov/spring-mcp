package io.salesdoctor.spring_mcp.dto;

import java.math.BigDecimal;

public record StockValueDto(Long agentId, BigDecimal totalValue) {
}
