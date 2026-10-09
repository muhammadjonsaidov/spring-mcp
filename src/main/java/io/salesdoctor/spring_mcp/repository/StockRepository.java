package io.salesdoctor.spring_mcp.repository;

import io.salesdoctor.spring_mcp.jooq.tables.records.StockRecord;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

import static io.salesdoctor.spring_mcp.jooq.Tables.PRODUCTS;
import static io.salesdoctor.spring_mcp.jooq.Tables.STOCK;

@Repository
public class StockRepository {

    private final DSLContext dsl;

    public StockRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public StockRecord getWarehouseStock(Long productId) {
        return dsl.selectFrom(STOCK)
                .where(STOCK.PRODUCT_ID.eq(productId))
                .and(STOCK.AGENT_ID.isNull())
                .fetchOne();
    }

    public void upsertWarehouseStock(Long productId, int quantity) {
        StockRecord existing = getWarehouseStock(productId);
        if (existing == null) {
            dsl.insertInto(STOCK)
                    .set(STOCK.PRODUCT_ID, productId)
                    .set(STOCK.AGENT_ID, (Long) null)
                    .set(STOCK.WAREHOUSE_LOCATION, "MAIN")
                    .set(STOCK.QUANTITY, quantity)
                    .set(STOCK.RESERVED_QUANTITY, 0)
                    .execute();
        } else {
            dsl.update(STOCK)
                    .set(STOCK.QUANTITY, existing.getQuantity() + quantity)
                    .where(STOCK.ID.eq(existing.getId()))
                    .execute();
        }
    }

    public int decreaseWarehouseStock(Long productId, int quantity) {
        return dsl.update(STOCK)
                .set(STOCK.QUANTITY, STOCK.QUANTITY.minus(quantity))
                .where(STOCK.PRODUCT_ID.eq(productId))
                .and(STOCK.AGENT_ID.isNull())
                .and(STOCK.QUANTITY.ge(quantity))
                .execute();
    }

    public List<StockRecord> findAllWarehouseStock() {
        return dsl.selectFrom(STOCK)
                .where(STOCK.AGENT_ID.isNull())
                .orderBy(STOCK.PRODUCT_ID)
                .fetch();
    }

    public StockRecord getAgentStock(Long productId, Long agentId) {
        return dsl.selectFrom(STOCK)
                .where(STOCK.PRODUCT_ID.eq(productId))
                .and(STOCK.AGENT_ID.eq(agentId))
                .fetchOne();
    }

    public List<StockRecord> findAgentStock(Long agentId) {
        return dsl.selectFrom(STOCK)
                .where(STOCK.AGENT_ID.eq(agentId))
                .orderBy(STOCK.PRODUCT_ID)
                .fetch();
    }

    public void upsertAgentStock(Long productId, Long agentId, int quantity) {
        StockRecord existing = getAgentStock(productId, agentId);
        if (existing == null) {
            dsl.insertInto(STOCK)
                    .set(STOCK.PRODUCT_ID, productId)
                    .set(STOCK.AGENT_ID, agentId)
                    .set(STOCK.WAREHOUSE_LOCATION, "VAN-" + agentId)
                    .set(STOCK.QUANTITY, quantity)
                    .set(STOCK.RESERVED_QUANTITY, 0)
                    .execute();
        } else {
            dsl.update(STOCK)
                    .set(STOCK.QUANTITY, existing.getQuantity() + quantity)
                    .where(STOCK.ID.eq(existing.getId()))
                    .execute();
        }
    }

    public int decreaseAgentStock(Long productId, Long agentId, int quantity) {
        return dsl.update(STOCK)
                .set(STOCK.QUANTITY, STOCK.QUANTITY.minus(quantity))
                .where(STOCK.PRODUCT_ID.eq(productId))
                .and(STOCK.AGENT_ID.eq(agentId))
                .and(STOCK.QUANTITY.ge(quantity))
                .execute();
    }

    public BigDecimal calculateAgentStockValue(Long agentId) {
        var result = dsl.select(DSL.sum(PRODUCTS.PRICE.mul(STOCK.QUANTITY)))
                .from(STOCK)
                .join(PRODUCTS)
                .on(PRODUCTS.ID.eq(STOCK.PRODUCT_ID))
                .where(STOCK.AGENT_ID.eq(agentId))
                .fetchOne();

        return result == null || result.value1() == null
                ? BigDecimal.ZERO
                : result.value1();
    }
}
