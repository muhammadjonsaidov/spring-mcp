package io.salesdoctor.spring_mcp.dto;

import io.salesdoctor.spring_mcp.domain.ReturnStatus;
import io.salesdoctor.spring_mcp.jooq.tables.records.ReturnsRecord;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ReturnDto(Long id, Long customerId, Long orderId, Long productId, Integer quantity,
                        BigDecimal amount, ReturnStatus status, String reason, Long agentId,
                        OffsetDateTime createdAt, OffsetDateTime resolvedAt) {

    public static ReturnDto from(ReturnsRecord r) {
        return new ReturnDto(r.getId(), r.getCustomerId(), r.getOrderId(), r.getProductId(),
                r.getQuantity(), r.getAmount(), r.getStatus(), r.getReason(), r.getAgentId(),
                r.getCreatedAt(), r.getResolvedAt());
    }
}
