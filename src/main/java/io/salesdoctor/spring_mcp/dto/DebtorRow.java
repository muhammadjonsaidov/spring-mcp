package io.salesdoctor.spring_mcp.dto;

import java.math.BigDecimal;

public record DebtorRow(Long customerId, String name, String phone, BigDecimal debt) {
}
