package io.salesdoctor.spring_mcp.repository;

import io.salesdoctor.spring_mcp.domain.AgentRole;
import io.salesdoctor.spring_mcp.domain.OrderStatus;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static io.salesdoctor.spring_mcp.jooq.Tables.*;

@Repository
public class ReportRepository {

    private final DSLContext dsl;

    public ReportRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public List<Record> dailySales(LocalDate date) {
        return List.copyOf(dsl.select(
                        ORDERS.AGENT_ID,
                        AGENTS.FULL_NAME,
                        DSL.count(ORDERS.ID).as("orderCount"),
                        DSL.sum(ORDERS.TOTAL_AMOUNT).as("totalAmount")
                ).from(ORDERS)
                .join(AGENTS).on(AGENTS.ID.eq(ORDERS.AGENT_ID))
                .where(ORDERS.CREATED_AT.cast(LocalDate.class).eq(date))
                .and(ORDERS.STATUS.in(OrderStatus.active()))
                .groupBy(ORDERS.AGENT_ID, AGENTS.FULL_NAME)
                .orderBy(DSL.sum(ORDERS.TOTAL_AMOUNT).desc())
                .fetch());
    }

    public List<Record> topProducts(LocalDate from, LocalDate to, int limit) {
        return List.copyOf(dsl.select(
                        PRODUCTS.ID,
                        PRODUCTS.NAME,
                        DSL.sum(ORDER_ITEMS.QUANTITY).as("totalQuantity"),
                        DSL.sum(
                                ORDER_ITEMS.UNIT_PRICE.mul(ORDER_ITEMS.QUANTITY)
                                        .mul(DSL.inline(BigDecimal.valueOf(100))
                                                .minus(DSL.coalesce(ORDER_ITEMS.DISCOUNT_PERCENT, BigDecimal.ZERO)))
                                        .div(DSL.inline(BigDecimal.valueOf(100)))
                        ).as("totalRevenue")
                )
                .from(ORDER_ITEMS)
                .join(PRODUCTS).on(PRODUCTS.ID.eq(ORDER_ITEMS.PRODUCT_ID))
                .join(ORDERS).on(ORDERS.ID.eq(ORDER_ITEMS.ORDER_ID))
                .where(ORDERS.CREATED_AT.cast(LocalDate.class).between(from, to))
                .and(ORDERS.STATUS.in(OrderStatus.active()))
                .groupBy(PRODUCTS.ID, PRODUCTS.NAME)
                .orderBy(DSL.sum(ORDER_ITEMS.QUANTITY).desc())
                .limit(limit)
                .fetch());
    }

    public List<Record> agentSales(LocalDate from, LocalDate to) {
        return List.copyOf(dsl.select(
                        AGENTS.ID,
                        AGENTS.FULL_NAME,
                        AGENTS.ROLE,
                        DSL.count(ORDERS.ID).as("orderCount"),
                        DSL.sum(ORDERS.TOTAL_AMOUNT).as("totalAmount")
                )
                .from(AGENTS)
                .leftJoin(ORDERS).on(ORDERS.AGENT_ID.eq(AGENTS.ID))
                .and(ORDERS.CREATED_AT.cast(LocalDate.class).between(from, to))
                .and(ORDERS.STATUS.in(OrderStatus.active()))
                .where(AGENTS.IS_ACTIVE.eq(true))
                .and(AGENTS.ROLE.eq(AgentRole.AGENT))
                .groupBy(AGENTS.ID, AGENTS.FULL_NAME, AGENTS.ROLE)
                .orderBy(DSL.sum(ORDERS.TOTAL_AMOUNT).desc().nullsLast())
                .fetch());
    }

    public List<Record> debtors(BigDecimal minDebt) {
        return List.copyOf(dsl.select(
                        CUSTOMERS.ID,
                        CUSTOMERS.NAME,
                        CUSTOMERS.PHONE,
                        CUSTOMERS.DEBT_AMOUNT
                )
                .from(CUSTOMERS)
                .where(CUSTOMERS.DEBT_AMOUNT.gt(minDebt))
                .and(CUSTOMERS.IS_ACTIVE.eq(true))
                .orderBy(CUSTOMERS.DEBT_AMOUNT.desc())
                .fetch());
    }

    /**
     * Har bir hudud uchun o'zi va barcha ichki hududlaridagi mijozlarning savdosi.
     * Hududi yo'q mijozlar savdosi alohida "Hududsiz" qatorida (id = null).
     */
    public List<Record> salesByTerritory(LocalDate from, LocalDate to) {
        return List.copyOf(dsl.fetch("""
                WITH RECURSIVE tree (root_id, territory_id) AS (
                    SELECT id, id FROM territories
                    UNION ALL
                    SELECT tree.root_id, child.id
                    FROM tree
                    JOIN territories child ON child.parent_id = tree.territory_id
                ),
                order_scope AS (
                    SELECT o.id, o.total_amount, c.territory_id
                    FROM orders o
                    JOIN customers c ON c.id = o.customer_id
                    WHERE o.created_at::date BETWEEN ? AND ?
                      AND o.status = ANY (?::varchar[])
                )
                SELECT * FROM (
                    SELECT t.id, t.name, t.parent_id,
                           COUNT(os.id) AS "orderCount",
                           COALESCE(SUM(os.total_amount), 0) AS "totalAmount"
                    FROM territories t
                    JOIN tree ON tree.root_id = t.id
                    JOIN order_scope os ON os.territory_id = tree.territory_id
                    GROUP BY t.id, t.name, t.parent_id
                    UNION ALL
                    SELECT NULL, 'Hududsiz', NULL,
                           COUNT(os.id),
                           COALESCE(SUM(os.total_amount), 0)
                    FROM order_scope os
                    WHERE os.territory_id IS NULL
                    HAVING COUNT(os.id) > 0
                ) result
                ORDER BY "totalAmount" DESC
                """, from, to, activeStatusCodes()));
    }

    private static String[] activeStatusCodes() {
        return OrderStatus.active().stream().map(Enum::name).toArray(String[]::new);
    }

    public Record overallStats(LocalDate from, LocalDate to) {
        return dsl.select(
                        DSL.count(ORDERS.ID).as("totalOrders"),
                        DSL.coalesce(DSL.sum(ORDERS.TOTAL_AMOUNT), BigDecimal.ZERO).as("totalRevenue"),
                        DSL.coalesce(DSL.round(DSL.avg(ORDERS.TOTAL_AMOUNT), 2), BigDecimal.ZERO).as("avgOrderValue"),
                        DSL.countDistinct(ORDERS.CUSTOMER_ID).as("uniqueCustomers")
                )
                .from(ORDERS)
                .where(ORDERS.CREATED_AT.cast(LocalDate.class).between(from, to))
                .and(ORDERS.STATUS.in(OrderStatus.active()))
                .fetchOne();
    }

    public Record customerSummary(Long customerId) {
        return dsl.select(
                        CUSTOMERS.ID,
                        CUSTOMERS.NAME,
                        CUSTOMERS.DEBT_AMOUNT,
                        DSL.count(ORDERS.ID).as("orderCount"),
                        DSL.coalesce(DSL.sum(ORDERS.TOTAL_AMOUNT), BigDecimal.ZERO).as("totalPurchases")
                )
                .from(CUSTOMERS)
                .leftJoin(ORDERS).on(ORDERS.CUSTOMER_ID.eq(CUSTOMERS.ID))
                .and(ORDERS.STATUS.in(OrderStatus.active()))
                .where(CUSTOMERS.ID.eq(customerId))
                .groupBy(CUSTOMERS.ID, CUSTOMERS.NAME, CUSTOMERS.DEBT_AMOUNT)
                .fetchOne();
    }
}
