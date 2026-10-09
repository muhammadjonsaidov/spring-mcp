package io.salesdoctor.spring_mcp.dto;

import io.salesdoctor.spring_mcp.jooq.tables.records.TerritoriesRecord;

public record TerritoryDto(Long id, String name, Long parentId) {

    public static TerritoryDto from(TerritoriesRecord r) {
        return new TerritoryDto(r.getId(), r.getName(), r.getParentId());
    }
}
