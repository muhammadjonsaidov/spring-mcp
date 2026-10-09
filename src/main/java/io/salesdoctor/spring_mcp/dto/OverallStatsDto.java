package io.salesdoctor.spring_mcp.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record OverallStatsDto(LocalDate from, LocalDate to, int totalOrders, BigDecimal totalRevenue,
                              BigDecimal avgOrderValue, int uniqueCustomers) {
}
