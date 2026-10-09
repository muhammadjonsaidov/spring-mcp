package io.salesdoctor.spring_mcp.dto;

import java.time.LocalDate;
import java.util.List;

public record TopProductsReport(LocalDate from, LocalDate to, int limit, List<TopProductRow> products) {
}
