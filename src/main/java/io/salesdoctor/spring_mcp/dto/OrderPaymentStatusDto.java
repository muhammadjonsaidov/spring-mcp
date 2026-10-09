package io.salesdoctor.spring_mcp.dto;

import io.salesdoctor.spring_mcp.domain.OrderStatus;

import java.math.BigDecimal;

public record OrderPaymentStatusDto(Long orderId, OrderStatus status, BigDecimal totalAmount,
                                    BigDecimal totalPaid, BigDecimal remaining) {
}
