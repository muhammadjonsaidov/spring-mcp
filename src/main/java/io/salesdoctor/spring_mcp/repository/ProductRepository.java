package io.salesdoctor.spring_mcp.repository;


import io.salesdoctor.spring_mcp.jooq.tables.records.ProductsRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

import static io.salesdoctor.spring_mcp.jooq.Tables.PRODUCTS;

@Repository
public class ProductRepository {

    private final DSLContext dsl;

    public ProductRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public ProductsRecord createProduct(String name, String sku, BigDecimal price, Long categoryId) {
        return dsl.insertInto(PRODUCTS)
                .set(PRODUCTS.NAME, name)
                .set(PRODUCTS.SKU, sku)
                .set(PRODUCTS.PRICE, price)
                .set(PRODUCTS.CATEGORY_ID, categoryId)
                .set(PRODUCTS.IS_ACTIVE, true)
                .returning()
                .fetchOne();
    }

    public List<ProductsRecord> findAll() {
        return dsl.selectFrom(PRODUCTS)
                .where(PRODUCTS.IS_ACTIVE.eq(true))
                .orderBy(PRODUCTS.NAME)
                .fetch();
    }

    public ProductsRecord findById(Long id) {
        return dsl.selectFrom(PRODUCTS)
                .where(PRODUCTS.ID.eq(id))
                .fetchOne();
    }

    public ProductsRecord findBySku(String sku) {
        return dsl.selectFrom(PRODUCTS)
                .where(PRODUCTS.SKU.eq(sku))
                .fetchOne();
    }

    public List<ProductsRecord> searchByName(String query) {
        return dsl.selectFrom(PRODUCTS)
                .where(PRODUCTS.NAME.containsIgnoreCase(query))
                .and(PRODUCTS.IS_ACTIVE.eq(true))
                .orderBy(PRODUCTS.NAME)
                .fetch();
    }

    public int updatePrice(Long productId, BigDecimal newPrice) {
        return dsl.update(PRODUCTS)
                .set(PRODUCTS.PRICE, newPrice)
                .where(PRODUCTS.ID.eq(productId))
                .execute();
    }

    public int softDelete(Long id) {
        return dsl.update(PRODUCTS)
                .set(PRODUCTS.IS_ACTIVE, false)
                .where(PRODUCTS.ID.eq(id))
                .execute();
    }
}
