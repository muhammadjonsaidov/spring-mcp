package io.salesdoctor.spring_mcp.repository;

import io.salesdoctor.spring_mcp.jooq.tables.records.OrderItemsRecord;
import io.salesdoctor.spring_mcp.jooq.tables.records.OrdersRecord;
import io.salesdoctor.spring_mcp.jooq.tables.records.ProductsRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static io.salesdoctor.spring_mcp.jooq.Tables.*;

@Repository
public class OrderRepository {

    private final DSLContext dsl;
    private final StockRepository stockRepository;

    public OrderRepository(DSLContext dsl, StockRepository stockRepository) {
        this.dsl = dsl;
        this.stockRepository = stockRepository;
    }

    @Transactional
    public OrdersRecord createOrder(Long customerId, Long agentId,
                                    List<OrderItemInput> items, LocalDate deliveryDate
    ) {
        String orderNumber = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (OrderItemInput item : items) {
            ProductsRecord product = dsl.selectFrom(PRODUCTS)
                    .where(PRODUCTS.ID.eq(item.productId()))
                    .fetchOne();
            if (product == null) {
                throw new IllegalArgumentException("Mahsulot topilmadi: ID=" + item.productId());
            }

            var stock = (agentId != null)
                    ? stockRepository.getAgentStock(item.productId(), agentId)
                    : stockRepository.getWarehouseStock(item.productId());
            if (stock == null || stock.getQuantity() < item.quantity()) {
                String source = (agentId != null) ? "agent " + agentId : "asosiy ombor";
                throw new IllegalStateException(
                        "Omborda yetarli mahsulot yo'q: " + product.getName() +
                                " (manba: " + source + ")" +
                                " (mavjud: " + (stock == null ? 0 : stock.getQuantity()) +
                                ", kerak: " + item.quantity() + ")"
                );
            }
            totalAmount = totalAmount.add(
                    product.getPrice().multiply(BigDecimal.valueOf(item.quantity()))
            );
        }

        OrdersRecord order = dsl.insertInto(ORDERS)
                .set(ORDERS.ORDER_NUMBER, orderNumber)
                .set(ORDERS.CUSTOMER_ID, customerId)
                .set(ORDERS.AGENT_ID, agentId)
                .set(ORDERS.STATUS, "NEW")
                .set(ORDERS.TOTAL_AMOUNT, totalAmount)
                .set(ORDERS.DELIVERY_DATE, deliveryDate)
                .returning()
                .fetchOne();

        for (OrderItemInput item : items) {
            ProductsRecord product = dsl.selectFrom(PRODUCTS)
                    .where(PRODUCTS.ID.eq(item.productId()))
                    .fetchOne();

            assert order != null;
            assert product != null;
            dsl.insertInto(ORDER_ITEMS)
                    .set(ORDER_ITEMS.ORDER_ID, order.getId())
                    .set(ORDER_ITEMS.PRODUCT_ID, item.productId())
                    .set(ORDER_ITEMS.QUANTITY, item.quantity())
                    .set(ORDER_ITEMS.UNIT_PRICE, product.getPrice())
                    .set(ORDER_ITEMS.DISCOUNT_PERCENT, BigDecimal.ZERO)
                    .execute();

            int updated = (agentId != null)
                    ? stockRepository.decreaseAgentStock(item.productId(), agentId, item.quantity())
                    : stockRepository.decreaseWarehouseStock(item.productId(), item.quantity());

            if (updated == 0) {
                throw new IllegalStateException(
                        "Omborni kamaytirishda xatolik: productId=" + item.productId()
                );
            }
        }

        dsl.update(CUSTOMERS)
                .set(CUSTOMERS.DEBT_AMOUNT, CUSTOMERS.DEBT_AMOUNT.plus(totalAmount))
                .where(CUSTOMERS.ID.eq(customerId))
                .execute();

        return order;
    }

    public OrdersRecord findById(Long id) {
        return dsl.selectFrom(ORDERS)
                .where(ORDERS.ID.eq(id))
                .fetchOne();
    }

    public OrdersRecord findByOrderNumber(String orderNumber) {
        return dsl.selectFrom(ORDERS)
                .where(ORDERS.ORDER_NUMBER.eq(orderNumber))
                .fetchOne();
    }

    public List<OrdersRecord> findByCustomer(Long customerId) {
        return dsl.selectFrom(ORDERS)
                .where(ORDERS.CUSTOMER_ID.eq(customerId))
                .orderBy(ORDERS.CREATED_AT.desc())
                .fetch();
    }

    public List<OrdersRecord> findByAgent(Long agentId) {
        return dsl.selectFrom(ORDERS)
                .where(ORDERS.AGENT_ID.eq(agentId))
                .orderBy(ORDERS.CREATED_AT.desc())
                .fetch();
    }

    public List<OrdersRecord> findAll() {
        return dsl.selectFrom(ORDERS)
                .orderBy(ORDERS.CREATED_AT.desc())
                .limit(100)
                .fetch();
    }

    public List<OrderItemsRecord> findItemsByOrder(Long orderId) {
        return dsl.selectFrom(ORDER_ITEMS)
                .where(ORDER_ITEMS.ORDER_ID.eq(orderId))
                .fetch();
    }

    @Transactional
    public int updateStatus(Long orderId, String newStatus) {
        return dsl.update(ORDERS)
                .set(ORDERS.STATUS, newStatus.toUpperCase())
                .where(ORDERS.ID.eq(orderId))
                .execute();
    }

    public record OrderItemInput(Long productId, int quantity) {
    }
}
