package io.salesdoctor.spring_mcp.dto;

import io.salesdoctor.spring_mcp.jooq.tables.records.CustomersRecord;

import java.math.BigDecimal;

public record CustomerDto(Long id, String name, String address, String phone,
                          Long territoryId, BigDecimal debt, boolean active) {

    public static CustomerDto from(CustomersRecord r) {
        return new CustomerDto(r.getId(), r.getName(), r.getAddress(), r.getPhone(),
                r.getTerritoryId(), r.getDebtAmount(), Boolean.TRUE.equals(r.getIsActive()));
    }
}
