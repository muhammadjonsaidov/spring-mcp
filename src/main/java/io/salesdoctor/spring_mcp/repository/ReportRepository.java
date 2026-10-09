package io.salesdoctor.spring_mcp.repository;

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
                .and(ORDERS.STATUS.ne("CANCELLED"))
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
                        ).as("totalRevenue")
                )
                .from(ORDER_ITEMS)
                .join(PRODUCTS).on(PRODUCTS.ID.eq(ORDER_ITEMS.PRODUCT_ID))
                .join(ORDERS).on(ORDERS.ID.eq(ORDER_ITEMS.ORDER_ID))
                .where(ORDERS.CREATED_AT.cast(LocalDate.class).between(from, to))
                .and(ORDERS.STATUS.ne("CANCELLED"))
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
                .and(ORDERS.STATUS.ne("CANCELLED"))
                .where(AGENTS.IS_ACTIVE.eq(true))
                .and(AGENTS.ROLE.eq("AGENT"))
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

    public List<Record> salesByTerritory(LocalDate from, LocalDate to) {
        return List.copyOf(dsl.select(
                        TERRITORIES.ID,
                        TERRITORIES.NAME,
                        DSL.count(ORDERS.ID).as("orderCount"),
                        DSL.sum(ORDERS.TOTAL_AMOUNT).as("totalAmount")
                )
                .from(ORDERS)
                .join(CUSTOMERS).on(CUSTOMERS.ID.eq(ORDERS.CUSTOMER_ID))
                .join(TERRITORIES).on(TERRITORIES.ID.eq(CUSTOMERS.TERRITORY_ID))
                .where(ORDERS.CREATED_AT.cast(LocalDate.class).between(from, to))
                .and(ORDERS.STATUS.ne("CANCELLED"))
                .groupBy(TERRITORIES.ID, TERRITORIES.NAME)
                .orderBy(DSL.sum(ORDERS.TOTAL_AMOUNT).desc())
                .fetch());
    }

    public Record overallStats(LocalDate from, LocalDate to) {
        return dsl.select(
                        DSL.count(ORDERS.ID).as("totalOrders"),
                        DSL.sum(ORDERS.TOTAL_AMOUNT).as("totalRevenue"),
                        DSL.avg(ORDERS.TOTAL_AMOUNT).as("avgOrderValue"),
                        DSL.countDistinct(ORDERS.CUSTOMER_ID).as("uniqueCustomers")
                )
                .from(ORDERS)
                .where(ORDERS.CREATED_AT.cast(LocalDate.class).between(from, to))
                .and(ORDERS.STATUS.ne("CANCELLED"))
                .fetchOne();
    }

    public Record customerSummary(Long customerId) {
        return dsl.select(
                        CUSTOMERS.ID,
                        CUSTOMERS.NAME,
                        CUSTOMERS.DEBT_AMOUNT,
                        DSL.count(ORDERS.ID).as("orderCount"),
                        DSL.sum(ORDERS.TOTAL_AMOUNT).as("totalPurchases")
                )
                .from(CUSTOMERS)
                .leftJoin(ORDERS).on(ORDERS.CUSTOMER_ID.eq(CUSTOMERS.ID))
                .where(CUSTOMERS.ID.eq(customerId))
                .groupBy(CUSTOMERS.ID, CUSTOMERS.NAME, CUSTOMERS.DEBT_AMOUNT)
                .fetchOne();
    }
}
