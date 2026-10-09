package io.salesdoctor.spring_mcp.repository;

import io.salesdoctor.spring_mcp.domain.StockLocation;
import io.salesdoctor.spring_mcp.error.ToolException;
import io.salesdoctor.spring_mcp.jooq.tables.records.StockRecord;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static io.salesdoctor.spring_mcp.jooq.Tables.AGENTS;
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

    /**
     * Asosiy omborga qo'shadi. Qator yo'q bo'lsa yaratadi (uq_stock_warehouse_product indeksi bo'yicha atomik).
     */
    public void upsertWarehouseStock(Long productId, int quantity) {
        requirePositive(quantity);
        dsl.insertInto(STOCK)
                .set(STOCK.PRODUCT_ID, productId)
                .set(STOCK.AGENT_ID, (Long) null)
                .set(STOCK.WAREHOUSE_LOCATION, StockLocation.main())
                .set(STOCK.QUANTITY, quantity)
                .set(STOCK.RESERVED_QUANTITY, 0)
                .onConflict(STOCK.PRODUCT_ID)
                .where(STOCK.AGENT_ID.isNull())
                .doUpdate()
                .set(STOCK.QUANTITY, STOCK.QUANTITY.plus(quantity))
                .set(STOCK.UPDATED_AT, DSL.currentOffsetDateTime())
                .execute();
    }

    /**
     * Mahsulot mavjudligini tekshirib, asosiy omborga qo'shadi.
     */
    @Transactional
    public void addToWarehouse(Long productId, int quantity) {
        requirePositive(quantity);
        boolean productExists = dsl.fetchExists(PRODUCTS, PRODUCTS.ID.eq(productId));
        if (!productExists) {
            throw ToolException.notFound("Mahsulot topilmadi: ID=" + productId);
        }
        upsertWarehouseStock(productId, quantity);
    }

    public int decreaseWarehouseStock(Long productId, int quantity) {
        requirePositive(quantity);
        return dsl.update(STOCK)
                .set(STOCK.QUANTITY, STOCK.QUANTITY.minus(quantity))
                .set(STOCK.UPDATED_AT, DSL.currentOffsetDateTime())
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
                .and(STOCK.WAREHOUSE_LOCATION.eq(vanLocation(agentId)))
                .fetchOne();
    }

    public List<StockRecord> findAgentStock(Long agentId) {
        return dsl.selectFrom(STOCK)
                .where(STOCK.AGENT_ID.eq(agentId))
                .orderBy(STOCK.PRODUCT_ID)
                .fetch();
    }

    /**
     * Agent mashinasiga qo'shadi (UNIQUE (product_id, agent_id, warehouse_location) bo'yicha atomik).
     */
    public void upsertAgentStock(Long productId, Long agentId, int quantity) {
        requirePositive(quantity);
        dsl.insertInto(STOCK)
                .set(STOCK.PRODUCT_ID, productId)
                .set(STOCK.AGENT_ID, agentId)
                .set(STOCK.WAREHOUSE_LOCATION, vanLocation(agentId))
                .set(STOCK.QUANTITY, quantity)
                .set(STOCK.RESERVED_QUANTITY, 0)
                .onConflict(STOCK.PRODUCT_ID, STOCK.AGENT_ID, STOCK.WAREHOUSE_LOCATION)
                .doUpdate()
                .set(STOCK.QUANTITY, STOCK.QUANTITY.plus(quantity))
                .set(STOCK.UPDATED_AT, DSL.currentOffsetDateTime())
                .execute();
    }

    public int decreaseAgentStock(Long productId, Long agentId, int quantity) {
        requirePositive(quantity);
        return dsl.update(STOCK)
                .set(STOCK.QUANTITY, STOCK.QUANTITY.minus(quantity))
                .set(STOCK.UPDATED_AT, DSL.currentOffsetDateTime())
                .where(STOCK.PRODUCT_ID.eq(productId))
                .and(STOCK.AGENT_ID.eq(agentId))
                .and(STOCK.WAREHOUSE_LOCATION.eq(vanLocation(agentId)))
                .and(STOCK.QUANTITY.ge(quantity))
                .execute();
    }

    /**
     * Asosiy ombordan faol agent mashinasiga o'tkazadi. Ikkala qadam bitta tranzaksiyada.
     */
    @Transactional
    public void transferToAgent(Long productId, Long agentId, int quantity) {
        requirePositive(quantity);
        requireActiveAgent(agentId);

        StockRecord warehouse = getWarehouseStock(productId);
        if (warehouse == null) {
            throw ToolException.conflict("Asosiy omborda bu mahsulot yo'q: productId=" + productId);
        }
        if (decreaseWarehouseStock(productId, quantity) == 0) {
            throw ToolException.conflict(String.format(
                    "Asosiy omborda yetarli mahsulot yo'q. Mavjud: %d, kerak: %d",
                    warehouse.getQuantity(), quantity));
        }
        upsertAgentStock(productId, agentId, quantity);
    }

    /**
     * Agent mashinasidan asosiy omborga qaytaradi. Ikkala qadam bitta tranzaksiyada.
     */
    @Transactional
    public void returnFromAgent(Long productId, Long agentId, int quantity) {
        requirePositive(quantity);

        StockRecord agentStock = getAgentStock(productId, agentId);
        if (agentStock == null) {
            throw ToolException.conflict(String.format(
                    "Agentda bu mahsulot yo'q. productId=%d, agentId=%d", productId, agentId));
        }
        if (decreaseAgentStock(productId, agentId, quantity) == 0) {
            throw ToolException.conflict(String.format(
                    "Agentda yetarli mahsulot yo'q. Mavjud: %d, kerak: %d",
                    agentStock.getQuantity(), quantity));
        }
        upsertWarehouseStock(productId, quantity);
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

    private void requireActiveAgent(Long agentId) {
        var agent = dsl.selectFrom(AGENTS).where(AGENTS.ID.eq(agentId)).fetchOne();
        if (agent == null) {
            throw ToolException.notFound("Agent topilmadi: ID=" + agentId);
        }
        if (!Boolean.TRUE.equals(agent.getIsActive())) {
            throw ToolException.conflict("Agent faol emas: ID=" + agentId);
        }
    }

    private static void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw ToolException.invalid("Miqdor 0 dan katta bo'lishi kerak.");
        }
    }

    private static String vanLocation(Long agentId) {
        return StockLocation.van(agentId);
    }
}
