package io.salesdoctor.spring_mcp.dto;

import io.salesdoctor.spring_mcp.jooq.tables.records.OrderItemsRecord;

import java.math.BigDecimal;

public record OrderItemDto(Long productId, int quantity, BigDecimal unitPrice, BigDecimal discountPercent) {

    public static OrderItemDto from(OrderItemsRecord r) {
        return new OrderItemDto(r.getProductId(), r.getQuantity(), r.getUnitPrice(), r.getDiscountPercent());
    }
}
