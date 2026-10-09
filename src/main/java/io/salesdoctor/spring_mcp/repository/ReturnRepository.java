package io.salesdoctor.spring_mcp.repository;

import io.salesdoctor.spring_mcp.domain.ReturnStatus;
import io.salesdoctor.spring_mcp.error.ToolException;
import io.salesdoctor.spring_mcp.jooq.tables.records.ReturnsRecord;
import io.salesdoctor.spring_mcp.support.AppTime;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static io.salesdoctor.spring_mcp.jooq.Tables.*;

@Repository
public class ReturnRepository {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final DSLContext dsl;
    private final StockRepository stockRepository;
    private final CustomerRepository customerRepository;
    private final KpiTargetRepository kpiTargetRepository;

    public ReturnRepository(DSLContext dsl, StockRepository stockRepository,
                            CustomerRepository customerRepository,
                            KpiTargetRepository kpiTargetRepository) {
        this.dsl = dsl;
        this.stockRepository = stockRepository;
        this.customerRepository = customerRepository;
        this.kpiTargetRepository = kpiTargetRepository;
    }

    /**
     * PENDING qaytarish yaratadi.
     * orderId berilsa: buyurtma shu mijozniki va DELIVERED bo'lishi, mahsulot buyurtmada bo'lishi,
     * miqdor esa buyurtmadagi miqdordan (avval qaytarilganlarni ayirib) oshmasligi kerak.
     * Summa berilmasa sotilgan narx bo'yicha hisoblanadi; berilsa undan oshmasligi kerak.
     */
    @Transactional
    public ReturnsRecord createReturn(Long customerId, Long orderId,
                                      Long productId, Integer quantity,
                                      BigDecimal amount, String reason,
                                      Long agentId) {

        if (quantity == null || quantity <= 0) {
            throw ToolException.invalid("Miqdor 0 dan katta bo'lishi kerak.");
        }
        if (amount != null && amount.signum() < 0) {
            throw ToolException.invalid("Summa manfiy bo'lishi mumkin emas.");
        }
        if (productId == null) {
            throw ToolException.invalid("Mahsulot ID si ko'rsatilishi kerak.");
        }

        var customer = customerRepository.findById(customerId);
        if (customer == null) {
            throw ToolException.notFound("Mijoz topilmadi: ID=" + customerId);
        }

        var product = dsl.selectFrom(PRODUCTS).where(PRODUCTS.ID.eq(productId)).fetchOne();
        if (product == null) {
            throw ToolException.notFound("Mahsulot topilmadi: ID=" + productId);
        }

        BigDecimal unitPrice;
        Long returnAgentId = agentId;

        if (orderId != null) {
            var order = dsl.selectFrom(ORDERS)
                    .where(ORDERS.ID.eq(orderId))
                    .forUpdate()
                    .fetchOne();
            if (order == null) {
                throw ToolException.notFound("Buyurtma topilmadi: ID=" + orderId);
            }
            if (!order.getCustomerId().equals(customerId)) {
                throw ToolException.invalid(
                        "Buyurtma bu mijozga tegishli emas: orderId=" + orderId + ", customerId=" + customerId);
            }
            if (!order.getStatus().allowsReturns()) {
                throw ToolException.conflict(
                        "Faqat yetkazilgan (DELIVERED) buyurtma bo'yicha qaytarish mumkin. Holat: " + order.getStatus());
            }

            var item = dsl.selectFrom(ORDER_ITEMS)
                    .where(ORDER_ITEMS.ORDER_ID.eq(orderId))
                    .and(ORDER_ITEMS.PRODUCT_ID.eq(productId))
                    .fetchOne();
            if (item == null) {
                throw ToolException.invalid(
                        "Bu mahsulot buyurtmada yo'q: orderId=" + orderId + ", productId=" + productId);
            }

            int alreadyReturned = returnedQuantity(orderId, productId);
            int available = item.getQuantity() - alreadyReturned;
            if (quantity > available) {
                throw ToolException.invalid(String.format(
                        "Qaytariladigan miqdor ko'p: buyurtmada %d, avval qaytarilgan %d, mumkin %d",
                        item.getQuantity(), alreadyReturned, available));
            }

            BigDecimal discount = item.getDiscountPercent() == null ? BigDecimal.ZERO : item.getDiscountPercent();
            unitPrice = item.getUnitPrice()
                    .multiply(HUNDRED.subtract(discount))
                    .divide(HUNDRED, 2, RoundingMode.HALF_UP);

            if (returnAgentId == null) {
                returnAgentId = order.getAgentId();
            }
        } else {
            unitPrice = product.getPrice();
        }

        if (returnAgentId != null && !dsl.fetchExists(AGENTS, AGENTS.ID.eq(returnAgentId))) {
            throw ToolException.notFound("Agent topilmadi: ID=" + returnAgentId);
        }

        BigDecimal maxAmount = unitPrice.multiply(BigDecimal.valueOf(quantity));
        BigDecimal returnAmount = amount == null ? maxAmount : amount;
        if (returnAmount.compareTo(maxAmount) > 0) {
            throw ToolException.invalid(
                    "Qaytarish summasi mahsulot qiymatidan katta: summa=" + returnAmount + ", maksimal=" + maxAmount);
        }

        return dsl.insertInto(RETURNS)
                .set(RETURNS.CUSTOMER_ID, customerId)
                .set(RETURNS.ORDER_ID, orderId)
                .set(RETURNS.PRODUCT_ID, productId)
                .set(RETURNS.QUANTITY, quantity)
                .set(RETURNS.AMOUNT, returnAmount)
                .set(RETURNS.REASON, reason)
                .set(RETURNS.STATUS, ReturnStatus.initial())
                .set(RETURNS.AGENT_ID, returnAgentId)
                .returning()
                .fetchOne();
    }

    /**
     * Faqat PENDING qaytarishni tasdiqlaydi. Holat sharti UPDATE ning o'zida tekshiriladi,
     * shuning uchun bir vaqtdagi ikki so'rovdan faqat bittasi o'tadi.
     */
    @Transactional
    public ReturnsRecord approveReturn(Long returnId) {
        ReturnsRecord ret = dsl.update(RETURNS)
                .set(RETURNS.STATUS, ReturnStatus.APPROVED)
                .set(RETURNS.RESOLVED_AT, OffsetDateTime.now(AppTime.ZONE))
                .where(RETURNS.ID.eq(returnId))
                .and(RETURNS.STATUS.in(sourcesOf(ReturnStatus.APPROVED)))
                .returning()
                .fetchOne();
        if (ret == null) {
            throw notTransitionable(returnId, ReturnStatus.APPROVED, "tasdiqlash");
        }

        if (ret.getProductId() != null && ret.getQuantity() > 0) {
            stockRepository.upsertWarehouseStock(ret.getProductId(), ret.getQuantity());
        }

        BigDecimal amount = ret.getAmount();
        if (amount != null && amount.signum() > 0) {
            customerRepository.decreaseDebt(ret.getCustomerId(), amount);
            kpiTargetRepository.applyToAgentKpi(ret.getAgentId(), kpiDate(ret), amount.negate());
        }

        return ret;
    }

    @Transactional
    public ReturnsRecord rejectReturn(Long returnId, String reason) {
        var update = dsl.update(RETURNS)
                .set(RETURNS.STATUS, ReturnStatus.REJECTED)
                .set(RETURNS.RESOLVED_AT, OffsetDateTime.now(AppTime.ZONE));
        if (reason != null && !reason.isBlank()) {
            update = update.set(RETURNS.REASON, reason);
        }

        ReturnsRecord ret = update
                .where(RETURNS.ID.eq(returnId))
                .and(RETURNS.STATUS.in(sourcesOf(ReturnStatus.REJECTED)))
                .returning()
                .fetchOne();
        if (ret == null) {
            throw notTransitionable(returnId, ReturnStatus.REJECTED, "rad etish");
        }
        return ret;
    }

    public ReturnsRecord findById(Long id) {
        return dsl.selectFrom(RETURNS)
                .where(RETURNS.ID.eq(id))
                .fetchOne();
    }

    public List<ReturnsRecord> findByCustomer(Long customerId) {
        return dsl.selectFrom(RETURNS)
                .where(RETURNS.CUSTOMER_ID.eq(customerId))
                .orderBy(RETURNS.CREATED_AT.desc())
                .fetch();
    }

    public List<ReturnsRecord> findByStatus(ReturnStatus status) {
        return dsl.selectFrom(RETURNS)
                .where(RETURNS.STATUS.eq(status))
                .orderBy(RETURNS.CREATED_AT.desc())
                .fetch();
    }

    public List<ReturnsRecord> findAll() {
        return dsl.selectFrom(RETURNS)
                .orderBy(RETURNS.CREATED_AT.desc())
                .limit(100)
                .fetch();
    }

    public List<ReturnsRecord> findByAgent(Long agentId) {
        return dsl.selectFrom(RETURNS)
                .where(RETURNS.AGENT_ID.eq(agentId))
                .orderBy(RETURNS.CREATED_AT.desc())
                .fetch();
    }

    /**
     * Buyurtmadagi mahsulotdan PENDING va APPROVED qaytarishlar yig'indisi.
     */
    private int returnedQuantity(Long orderId, Long productId) {
        Integer sum = dsl.select(DSL.coalesce(DSL.sum(RETURNS.QUANTITY), BigDecimal.ZERO).cast(Integer.class))
                .from(RETURNS)
                .where(RETURNS.ORDER_ID.eq(orderId))
                .and(RETURNS.PRODUCT_ID.eq(productId))
                .and(RETURNS.STATUS.in(ReturnStatus.reservingQuantity()))
                .fetchOne(0, Integer.class);
        return sum == null ? 0 : sum;
    }

    /**
     * KPI buyurtma sanasidagi davrdan kamayadi; buyurtma bo'lmasa qaytarish sanasidan.
     */
    private LocalDate kpiDate(ReturnsRecord ret) {
        if (ret.getOrderId() != null) {
            var order = dsl.selectFrom(ORDERS).where(ORDERS.ID.eq(ret.getOrderId())).fetchOne();
            if (order != null) {
                return AppTime.toLocalDate(order.getCreatedAt());
            }
        }
        return AppTime.toLocalDate(ret.getCreatedAt());
    }

    /**
     * target ga o'tish mumkin bo'lgan holatlar (ReturnStatus.nextStatuses() bo'yicha).
     */
    private static Set<ReturnStatus> sourcesOf(ReturnStatus target) {
        Set<ReturnStatus> sources = EnumSet.noneOf(ReturnStatus.class);
        for (ReturnStatus status : ReturnStatus.values()) {
            if (status.canTransitionTo(target)) sources.add(status);
        }
        return sources;
    }

    private RuntimeException notTransitionable(Long returnId, ReturnStatus target, String action) {
        ReturnsRecord existing = findById(returnId);
        if (existing == null) {
            return ToolException.notFound("Qaytarish topilmadi: ID=" + returnId);
        }
        return ToolException.conflict(
                "Faqat " + sourcesOf(target) + " holatidagi qaytarishni " + action +
                        " mumkin. Hozirgi holat: " + existing.getStatus());
    }
}
