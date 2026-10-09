package io.salesdoctor.spring_mcp.dto;

import java.time.LocalDate;
import java.util.List;

public record TerritorySalesReport(LocalDate from, LocalDate to, List<TerritorySalesRow> territories) {
}
