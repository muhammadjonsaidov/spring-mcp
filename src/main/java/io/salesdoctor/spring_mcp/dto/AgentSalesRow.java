package io.salesdoctor.spring_mcp.dto;

import java.math.BigDecimal;

public record AgentSalesRow(Long agentId, String agentName, int orderCount, BigDecimal totalAmount) {
}
