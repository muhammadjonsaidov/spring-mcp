package io.salesdoctor.spring_mcp.dto;

import io.salesdoctor.spring_mcp.domain.PaymentMethod;
import io.salesdoctor.spring_mcp.jooq.tables.records.PaymentsRecord;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PaymentDto(Long id, Long customerId, Long orderId, BigDecimal amount,
                         PaymentMethod method, Long agentId, OffsetDateTime createdAt) {

    public static PaymentDto from(PaymentsRecord r) {
        return new PaymentDto(r.getId(), r.getCustomerId(), r.getOrderId(), r.getAmount(),
                r.getPaymentMethod(), r.getAgentId(), r.getCreatedAt());
    }
}
