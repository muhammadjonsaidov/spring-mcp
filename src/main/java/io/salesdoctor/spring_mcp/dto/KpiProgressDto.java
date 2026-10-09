package io.salesdoctor.spring_mcp.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record KpiProgressDto(Long kpiId, Long agentId, LocalDate periodStart, LocalDate periodEnd,
                             BigDecimal target, BigDecimal achieved, BigDecimal progressPercent) {
}
