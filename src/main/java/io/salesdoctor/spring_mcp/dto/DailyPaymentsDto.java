package io.salesdoctor.spring_mcp.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyPaymentsDto(LocalDate date, BigDecimal totalPayments) {
}
