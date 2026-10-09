package io.salesdoctor.spring_mcp.dto;

import java.time.LocalDate;
import java.util.List;

public record AgentKpiReport(LocalDate from, LocalDate to, List<AgentSalesRow> agents) {
}
