package io.salesdoctor.spring_mcp.repository;

import io.salesdoctor.spring_mcp.domain.OrderStatus;
import io.salesdoctor.spring_mcp.jooq.tables.records.OrderItemsRecord;
import io.salesdoctor.spring_mcp.jooq.tables.records.OrdersRecord;
import io.salesdoctor.spring_mcp.jooq.tables.records.ProductsRecord;
import io.salesdoctor.spring_mcp.support.AppTime;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.salesdoctor.spring_mcp.jooq.Tables.*;

@Repository
public class OrderRepository {

    private final DSLContext dsl;
    private final StockRepository stockRepository;
    private final CustomerRepository customerRepository;
    private final KpiTargetRepository kpiTargetRepository;

    public OrderRepository(DSLContext dsl, StockRepository stockRepository,
                           CustomerRepository customerRepository,
                           KpiTargetRepository kpiTargetRepository) {
        this.dsl = dsl;
        this.stockRepository = stockRepository;
        this.customerRepository = customerRepository;
        this.kpiTargetRepository = kpiTargetRepository;
    }

    @Transactional
    public OrdersRecord createOrder(Long customerId, Long agentId,
                                    List<OrderItemInput> items, LocalDate deliveryDate) {
        Map<Long, Integer> quantities = mergeItems(items);

        var customer = customerRepository.findById(customerId);
        if (customer == null) {
            throw new IllegalArgumentException("Mijoz topilmadi: ID=" + customerId);
        }
        if (!Boolean.TRUE.equals(customer.getIsActive())) {
            throw new IllegalStateException("Mijoz faol emas: ID=" + customerId);
        }

        if (agentId != null) {
            var agent = dsl.selectFrom(AGENTS).where(AGENTS.ID.eq(agentId)).fetchOne();
            if (agent == null) {
                throw new IllegalArgumentException("Agent topilmadi: ID=" + agentId);
            }
            if (!Boolean.TRUE.equals(agent.getIsActive())) {
                throw new IllegalStateException("Agent faol emas: ID=" + agentId);
            }
        }

        Map<Long, ProductsRecord> products = new LinkedHashMap<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (var entry : quantities.entrySet()) {
            Long productId = entry.getKey();
            int quantity = entry.getValue();

            ProductsRecord product = dsl.selectFrom(PRODUCTS)
                    .where(PRODUCTS.ID.eq(productId))
                    .fetchOne();
            if (product == null) {
                throw new IllegalArgumentException("Mahsulot topilmadi: ID=" + productId);
            }
            if (!Boolean.TRUE.equals(product.getIsActive())) {
                throw new IllegalStateException("Mahsulot faol emas: " + product.getName());
            }

            var stock = (agentId != null)
                    ? stockRepository.getAgentStock(productId, agentId)
                    : stockRepository.getWarehouseStock(productId);
            if (stock == null || stock.getQuantity() < quantity) {
                String source = (agentId != null) ? "agent " + agentId : "asosiy ombor";
                throw new IllegalStateException(
                        "Omborda yetarli mahsulot yo'q: " + product.getName() +
                                " (manba: " + source + ")" +
                                " (mavjud: " + (stock == null ? 0 : stock.getQuantity()) +
                                ", kerak: " + quantity + ")"
                );
            }

            products.put(productId, product);
            totalAmount = totalAmount.add(product.getPrice().multiply(BigDecimal.valueOf(quantity)));
        }

        OrdersRecord order = dsl.insertInto(ORDERS)
                .set(ORDERS.ORDER_NUMBER, "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .set(ORDERS.CUSTOMER_ID, customerId)
                .set(ORDERS.AGENT_ID, agentId)
                .set(ORDERS.STATUS, OrderStatus.initial())
                .set(ORDERS.TOTAL_AMOUNT, totalAmount)
                .set(ORDERS.DELIVERY_DATE, deliveryDate)
                .returning()
                .fetchOne();

        for (var entry : quantities.entrySet()) {
            Long productId = entry.getKey();
            int quantity = entry.getValue();

            dsl.insertInto(ORDER_ITEMS)
                    .set(ORDER_ITEMS.ORDER_ID, order.getId())
                    .set(ORDER_ITEMS.PRODUCT_ID, productId)
                    .set(ORDER_ITEMS.QUANTITY, quantity)
                    .set(ORDER_ITEMS.UNIT_PRICE, products.get(productId).getPrice())
                    .set(ORDER_ITEMS.DISCOUNT_PERCENT, BigDecimal.ZERO)
                    .execute();

            // Shartli UPDATE: qoldiq oradan kamaygan bo'lsa 0 qator yangilanadi
            int updated = (agentId != null)
                    ? stockRepository.decreaseAgentStock(productId, agentId, quantity)
                    : stockRepository.decreaseWarehouseStock(productId, quantity);
            if (updated == 0) {
                throw new IllegalStateException(
                        "Omborda yetarli mahsulot yo'q: " + products.get(productId).getName());
            }
        }

        customerRepository.increaseDebt(customerId, totalAmount);
        kpiTargetRepository.applyToAgentKpi(agentId, AppTime.toLocalDate(order.getCreatedAt()), totalAmount);

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

    /**
     * Holatni o'zgartiradi (o'tish qoidalari OrderStatus da). CANCELLED bo'lganda mahsulot
     * manbaga qaytadi, mijoz qarzi va agent KPI si buyurtma summasiga kamayadi.
     */
    @Transactional
    public OrderStatus updateStatus(Long orderId, OrderStatus target) {
        OrdersRecord order = dsl.selectFrom(ORDERS)
                .where(ORDERS.ID.eq(orderId))
                .forUpdate()
                .fetchOne();
        if (order == null) {
            throw new IllegalArgumentException("Buyurtma topilmadi: ID=" + orderId);
        }

        OrderStatus current = order.getStatus();
        if (current == target) {
            throw new IllegalStateException("Buyurtma allaqachon " + current + " holatida: ID=" + orderId);
        }
        if (!current.canTransitionTo(target)) {
            throw new IllegalStateException(
                    "Holatni " + current + " dan " + target + " ga o'zgartirib bo'lmaydi: ID=" + orderId);
        }

        dsl.update(ORDERS)
                .set(ORDERS.STATUS, target)
                .where(ORDERS.ID.eq(orderId))
                .execute();

        if (!target.isActive()) {
            revertOrder(order);
        }
        return target;
    }

    private void revertOrder(OrdersRecord order) {
        for (OrderItemsRecord item : findItemsByOrder(order.getId())) {
            if (order.getAgentId() != null) {
                stockRepository.upsertAgentStock(item.getProductId(), order.getAgentId(), item.getQuantity());
            } else {
                stockRepository.upsertWarehouseStock(item.getProductId(), item.getQuantity());
            }
        }

        customerRepository.decreaseDebt(order.getCustomerId(), order.getTotalAmount());
        kpiTargetRepository.applyToAgentKpi(order.getAgentId(),
                AppTime.toLocalDate(order.getCreatedAt()), order.getTotalAmount().negate());
    }

    /**
     * Bir xil mahsulot qatorlarini birlashtiradi va miqdorlarni tekshiradi.
     */
    private static Map<Long, Integer> mergeItems(List<OrderItemInput> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Kamida bitta mahsulot kerak.");
        }
        Map<Long, Integer> merged = new LinkedHashMap<>();
        for (OrderItemInput item : items) {
            if (item.productId() == null) {
                throw new IllegalArgumentException("Mahsulot ID si ko'rsatilmagan.");
            }
            if (item.quantity() <= 0) {
                throw new IllegalArgumentException(
                        "Miqdor 0 dan katta bo'lishi kerak: productId=" + item.productId());
            }
            merged.merge(item.productId(), item.quantity(), Math::addExact);
        }
        return merged;
    }

    public record OrderItemInput(Long productId, int quantity) {
    }
}
