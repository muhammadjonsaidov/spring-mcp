package io.salesdoctor.spring_mcp.dto;

import io.salesdoctor.spring_mcp.jooq.tables.records.CategoriesRecord;

public record CategoryDto(Long id, String name, Long parentId) {

    public static CategoryDto from(CategoriesRecord r) {
        return new CategoryDto(r.getId(), r.getName(), r.getParentId());
    }
}
