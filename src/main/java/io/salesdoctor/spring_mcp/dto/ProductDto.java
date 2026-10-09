package io.salesdoctor.spring_mcp.dto;

import io.salesdoctor.spring_mcp.jooq.tables.records.ProductsRecord;

import java.math.BigDecimal;

public record ProductDto(Long id, String name, String sku, BigDecimal price,
                         Long categoryId, boolean active) {

    public static ProductDto from(ProductsRecord r) {
        return new ProductDto(r.getId(), r.getName(), r.getSku(), r.getPrice(),
                r.getCategoryId(), Boolean.TRUE.equals(r.getIsActive()));
    }
}
