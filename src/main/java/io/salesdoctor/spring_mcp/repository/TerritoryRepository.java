package io.salesdoctor.spring_mcp.repository;

import io.salesdoctor.spring_mcp.jooq.tables.records.TerritoriesRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;

import static io.salesdoctor.spring_mcp.jooq.Tables.TERRITORIES;

@Repository
public class TerritoryRepository {

    private final DSLContext dsl;

    public TerritoryRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public TerritoriesRecord create(String name, Long parentId) {
        return dsl.insertInto(TERRITORIES)
                .set(TERRITORIES.NAME, name)
                .set(TERRITORIES.PARENT_ID, parentId)
                .returning()
                .fetchOne();
    }

    public List<TerritoriesRecord> findAll() {
        return dsl.selectFrom(TERRITORIES)
                .orderBy(TERRITORIES.NAME)
                .fetch();
    }

    public TerritoriesRecord findById(Long id) {
        return dsl.selectFrom(TERRITORIES)
                .where(TERRITORIES.ID.eq(id))
                .fetchOne();
    }

    public List<TerritoriesRecord> findChildren(Long parentId) {
        return dsl.selectFrom(TERRITORIES)
                .where(TERRITORIES.PARENT_ID.eq(parentId))
                .orderBy(TERRITORIES.NAME)
                .fetch();
    }

    public List<TerritoriesRecord> findRoots() {
        return dsl.selectFrom(TERRITORIES)
                .where(TERRITORIES.PARENT_ID.isNull())
                .orderBy(TERRITORIES.NAME)
                .fetch();
    }

    public int rename(Long id, String newName) {
        return dsl.update(TERRITORIES)
                .set(TERRITORIES.NAME, newName)
                .where(TERRITORIES.ID.eq(id))
                .execute();
    }

    public int delete(Long id) {
        return dsl.deleteFrom(TERRITORIES)
                .where(TERRITORIES.ID.eq(id))
                .execute();
    }
}
