package io.salesdoctor.spring_mcp.dto;

import java.time.LocalDate;
import java.util.List;

public record DailySalesReport(LocalDate date, List<AgentSalesRow> agents) {
}
