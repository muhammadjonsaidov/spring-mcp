package io.salesdoctor.spring_mcp.dto;

import io.salesdoctor.spring_mcp.domain.OrderStatus;
import io.salesdoctor.spring_mcp.jooq.tables.records.OrdersRecord;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record OrderDto(Long id, String orderNumber, Long customerId, Long agentId,
                       OrderStatus status, BigDecimal totalAmount, LocalDate deliveryDate,
                       OffsetDateTime createdAt) {

    public static OrderDto from(OrdersRecord r) {
        return new OrderDto(r.getId(), r.getOrderNumber(), r.getCustomerId(), r.getAgentId(),
                r.getStatus(), r.getTotalAmount(), r.getDeliveryDate(), r.getCreatedAt());
    }
}
