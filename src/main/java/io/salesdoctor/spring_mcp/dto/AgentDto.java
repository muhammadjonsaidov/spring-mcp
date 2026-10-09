package io.salesdoctor.spring_mcp.dto;

import io.salesdoctor.spring_mcp.domain.AgentRole;
import io.salesdoctor.spring_mcp.jooq.tables.records.AgentsRecord;

public record AgentDto(Long id, String fullName, String phone, String email,
                       AgentRole role, Long territoryId, boolean active) {

    public static AgentDto from(AgentsRecord r) {
        return new AgentDto(r.getId(), r.getFullName(), r.getPhone(), r.getEmail(),
                r.getRole(), r.getTerritoryId(), Boolean.TRUE.equals(r.getIsActive()));
    }
}
