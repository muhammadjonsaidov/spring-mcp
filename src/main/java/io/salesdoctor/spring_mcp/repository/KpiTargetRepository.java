package io.salesdoctor.spring_mcp.repository;

import io.salesdoctor.spring_mcp.jooq.tables.records.KpiTargetsRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static io.salesdoctor.spring_mcp.jooq.Tables.KPI_TARGETS;

@Repository
public class KpiTargetRepository {

    private final DSLContext dsl;


    public KpiTargetRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public KpiTargetsRecord create(Long agentId, LocalDate periodStart,
                                   LocalDate periodEnd, BigDecimal targetAmount) {
        return dsl.insertInto(KPI_TARGETS)
                .set(KPI_TARGETS.AGENT_ID, agentId)
                .set(KPI_TARGETS.PERIOD_START, periodStart)
                .set(KPI_TARGETS.PERIOD_END, periodEnd)
                .set(KPI_TARGETS.TARGET_AMOUNT, targetAmount)
                .set(KPI_TARGETS.ACHIEVED_AMOUNT, BigDecimal.ZERO)
                .returning()
                .fetchOne();
    }

    public KpiTargetsRecord findById(Long id) {
        return dsl.selectFrom(KPI_TARGETS)
                .where(KPI_TARGETS.ID.eq(id))
                .fetchOne();
    }

    public List<KpiTargetsRecord> findByAgent(Long agentId) {
        return dsl.selectFrom(KPI_TARGETS)
                .where(KPI_TARGETS.AGENT_ID.eq(agentId))
                .orderBy(KPI_TARGETS.PERIOD_START.desc())
                .fetch();
    }

    public KpiTargetsRecord findByAgentAndPeriod(Long agentId,
                                                 LocalDate periodStart,
                                                 LocalDate periodEnd) {
        return dsl.selectFrom(KPI_TARGETS)
                .where(KPI_TARGETS.AGENT_ID.eq(agentId))
                .and(KPI_TARGETS.PERIOD_START.eq(periodStart))
                .and(KPI_TARGETS.PERIOD_END.eq(periodEnd))
                .fetchOne();
    }

    public KpiTargetsRecord findByAgentAndDate(Long agentId, LocalDate date) {
        return dsl.selectFrom(KPI_TARGETS)
                .where(KPI_TARGETS.AGENT_ID.eq(agentId))
                .and(KPI_TARGETS.PERIOD_START.le(date))
                .and(KPI_TARGETS.PERIOD_END.ge(date))
                .orderBy(KPI_TARGETS.PERIOD_START.desc())
                .limit(1)
                .fetchOne();
    }

    public List<KpiTargetsRecord> findAll() {
        return dsl.selectFrom(KPI_TARGETS)
                .orderBy(KPI_TARGETS.PERIOD_START.desc(), KPI_TARGETS.AGENT_ID)
                .fetch();
    }

    public int addAchievedAmount(Long kpiTargetId, BigDecimal amount) {
        return dsl.update(KPI_TARGETS)
                .set(KPI_TARGETS.ACHIEVED_AMOUNT,
                        KPI_TARGETS.ACHIEVED_AMOUNT.plus(amount))
                .where(KPI_TARGETS.ID.eq(kpiTargetId))
                .execute();
    }

    public int setAchievedAmount(Long kpiTargetId, BigDecimal amount) {
        return dsl.update(KPI_TARGETS)
                .set(KPI_TARGETS.ACHIEVED_AMOUNT, amount)
                .where(KPI_TARGETS.ID.eq(kpiTargetId))
                .execute();
    }

    public int updateTargetAmount(Long kpiTargetId, BigDecimal newTarget) {
        return dsl.update(KPI_TARGETS)
                .set(KPI_TARGETS.TARGET_AMOUNT, newTarget)
                .where(KPI_TARGETS.ID.eq(kpiTargetId))
                .execute();
    }

    public int delete(Long id) {
        return dsl.deleteFrom(KPI_TARGETS)
                .where(KPI_TARGETS.ID.eq(id))
                .execute();
    }
}
