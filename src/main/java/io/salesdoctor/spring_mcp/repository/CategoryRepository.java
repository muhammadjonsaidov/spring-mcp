package io.salesdoctor.spring_mcp.repository;

import io.salesdoctor.spring_mcp.jooq.tables.records.CategoriesRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;

import static io.salesdoctor.spring_mcp.jooq.Tables.CATEGORIES;
import static io.salesdoctor.spring_mcp.jooq.Tables.PRODUCTS;

@Repository
public class CategoryRepository {

    private final DSLContext dsl;

    public CategoryRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public CategoriesRecord create(String name, Long parentId) {
        return dsl.insertInto(CATEGORIES)
                .set(CATEGORIES.NAME, name)
                .set(CATEGORIES.PARENT_ID, parentId)
                .returning()
                .fetchOne();
    }

    public List<CategoriesRecord> findAll() {
        return dsl.selectFrom(CATEGORIES)
                .orderBy(CATEGORIES.NAME)
                .fetch();
    }

    public List<CategoriesRecord> findRoots() {
        return dsl.selectFrom(CATEGORIES)
                .where(CATEGORIES.PARENT_ID.isNull())
                .orderBy(CATEGORIES.NAME)
                .fetch();
    }

    public List<CategoriesRecord> findChildren(Long parentId) {
        return dsl.selectFrom(CATEGORIES)
                .where(CATEGORIES.PARENT_ID.eq(parentId))
                .orderBy(CATEGORIES.NAME)
                .fetch();
    }

    public CategoriesRecord findById(Long id) {
        return dsl.selectFrom(CATEGORIES)
                .where(CATEGORIES.ID.eq(id))
                .fetchOne();
    }

    public List<CategoriesRecord> searchByName(String query) {
        return dsl.selectFrom(CATEGORIES)
                .where(CATEGORIES.NAME.containsIgnoreCase(query))
                .orderBy(CATEGORIES.NAME)
                .fetch();
    }

    public int rename(Long id, String newName) {
        return dsl.update(CATEGORIES)
                .set(CATEGORIES.NAME, newName)
                .where(CATEGORIES.ID.eq(id))
                .execute();
    }

    public int delete(Long id) {
        return dsl.deleteFrom(CATEGORIES)
                .where(CATEGORIES.ID.eq(id))
                .execute();
    }

    public int countProducts(Long categoryId) {
        return dsl.selectCount()
                .from(PRODUCTS)
                .where(PRODUCTS.CATEGORY_ID.eq(categoryId))
                .fetchOne(0, int.class);
    }
}
