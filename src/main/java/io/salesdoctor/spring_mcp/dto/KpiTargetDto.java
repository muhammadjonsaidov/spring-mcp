package io.salesdoctor.spring_mcp.dto;

import io.salesdoctor.spring_mcp.jooq.tables.records.KpiTargetsRecord;

import java.math.BigDecimal;
import java.time.LocalDate;

public record KpiTargetDto(Long id, Long agentId, LocalDate periodStart, LocalDate periodEnd,
                           BigDecimal target, BigDecimal achieved) {

    public static KpiTargetDto from(KpiTargetsRecord r) {
        return new KpiTargetDto(r.getId(), r.getAgentId(), r.getPeriodStart(), r.getPeriodEnd(),
                r.getTargetAmount(), r.getAchievedAmount() == null ? BigDecimal.ZERO : r.getAchievedAmount());
    }
}
