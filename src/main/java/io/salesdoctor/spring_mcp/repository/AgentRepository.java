package io.salesdoctor.spring_mcp.repository;

import io.salesdoctor.spring_mcp.jooq.tables.Agents;
import io.salesdoctor.spring_mcp.jooq.tables.records.AgentsRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;

import static io.salesdoctor.spring_mcp.jooq.Tables.AGENTS;

@Repository
public class AgentRepository {


    private final DSLContext dsl;

    public AgentRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public AgentsRecord create(String fullName, String phone, String email, String role, Long territoryId) {
        return dsl.insertInto(AGENTS)
                .set(AGENTS.FULL_NAME, fullName)
                .set(AGENTS.PHONE, phone)
                .set(AGENTS.EMAIL, email)
                .set(AGENTS.ROLE, role == null ? "AGENT" : role.toUpperCase())
                .set(AGENTS.TERRITORY_ID, territoryId)
                .set(AGENTS.IS_ACTIVE, true)
                .returning()
                .fetchOne();
    }

    public List<AgentsRecord> findAllActive() {
        return dsl.selectFrom(AGENTS)
                .where(AGENTS.IS_ACTIVE.eq(true))
                .orderBy(AGENTS.FULL_NAME)
                .fetch();
    }

    public List<AgentsRecord> findByTerritory(Long territoryId) {
        return dsl.selectFrom(AGENTS)
                .where(AGENTS.TERRITORY_ID.eq(territoryId))
                .and(AGENTS.IS_ACTIVE.eq(true))
                .orderBy(AGENTS.FULL_NAME)
                .fetch();
    }

    public List<AgentsRecord> findByRole(String role) {
        return dsl.selectFrom(AGENTS)
                .where(AGENTS.ROLE.eq(role.toUpperCase()))
                .and(AGENTS.IS_ACTIVE.eq(true))
                .orderBy(AGENTS.FULL_NAME)
                .fetch();
    }

    public AgentsRecord findById(Long id) {
        return dsl.selectFrom(AGENTS)
                .where(AGENTS.ID.eq(id))
                .fetchOne();
    }

    public AgentsRecord findByEmail(String email) {
        return dsl.selectFrom(AGENTS)
                .where(AGENTS.EMAIL.eq(email))
                .fetchOne();
    }

    public List<AgentsRecord> searchByName(String query) {
        return dsl.selectFrom(AGENTS)
                .where(AGENTS.FULL_NAME.containsIgnoreCase(query))
                .and(AGENTS.IS_ACTIVE.eq(true))
                .orderBy(AGENTS.FULL_NAME)
                .fetch();
    }

    public int updateTerritory(Long agentId, Long territoryId) {
        return dsl.update(AGENTS)
                .set(AGENTS.TERRITORY_ID, territoryId)
                .where(AGENTS.ID.eq(agentId))
                .execute();
    }

    public int updateRole(Long agentId, String newRole) {
        return dsl.update(AGENTS)
                .set(AGENTS.ROLE, newRole.toUpperCase())
                .where(AGENTS.ID.eq(agentId))
                .execute();
    }

    public int deactivate(Long id) {
        return dsl.update(AGENTS)
                .set(AGENTS.IS_ACTIVE, false)
                .where(AGENTS.ID.eq(id))
                .execute();
    }
}
