package io.salesdoctor.spring_mcp.repository;

import io.salesdoctor.spring_mcp.jooq.tables.records.ReturnsRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static io.salesdoctor.spring_mcp.jooq.Tables.CUSTOMERS;
import static io.salesdoctor.spring_mcp.jooq.Tables.RETURNS;

@Repository
public class ReturnRepository {

    private final DSLContext dsl;
    private final StockRepository stockRepository;

    public ReturnRepository(DSLContext dsl, StockRepository stockRepository) {
        this.dsl = dsl;
        this.stockRepository = stockRepository;
    }

    @Transactional
    public ReturnsRecord createReturn(Long customerId, Long orderId,
                                      Long productId, Integer quantity,
                                      BigDecimal amount, String reason,
                                      Long agentId) {

        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Miqdor 0 dan katta bo'lishi kerak.");
        }

        // Mijoz mavjudligini tekshirish
        var customer = dsl.selectFrom(CUSTOMERS)
                .where(CUSTOMERS.ID.eq(customerId))
                .fetchOne();

        if (customer == null) {
            throw new IllegalArgumentException("Mijoz topilmadi: ID=" + customerId);
        }

        return dsl.insertInto(RETURNS)
                .set(RETURNS.CUSTOMER_ID, customerId)
                .set(RETURNS.ORDER_ID, orderId)
                .set(RETURNS.PRODUCT_ID, productId)
                .set(RETURNS.QUANTITY, quantity)
                .set(RETURNS.AMOUNT, amount == null ? BigDecimal.ZERO : amount)
                .set(RETURNS.REASON, reason)
                .set(RETURNS.STATUS, "PENDING")
                .set(RETURNS.AGENT_ID, agentId)
                .returning()
                .fetchOne();
    }

    @Transactional
    public int approveReturn(Long returnId) {
        ReturnsRecord ret = findById(returnId);
        if (ret == null) {
            throw new IllegalArgumentException("Qaytarish topilmadi: ID=" + returnId);
        }
        if (!"PENDING".equals(ret.getStatus())) {
            throw new IllegalStateException("Faqat PENDING holatidagi qaytarishni tasdiqlash mumkin.");
        }

        // 1. Statusni yangilash
        int updated = dsl.update(RETURNS)
                .set(RETURNS.STATUS, "APPROVED")
                .set(RETURNS.RESOLVED_AT, OffsetDateTime.now())
                .where(RETURNS.ID.eq(returnId))
                .execute();

        // 2. Ombor qoldig'ini oshirish
        if (ret.getProductId() != null && ret.getQuantity() > 0) {
            stockRepository.upsertWarehouseStock(ret.getProductId(), ret.getQuantity());
        }

        // 3. Mijozning qarzini kamaytirish
        if (ret.getAmount() != null && ret.getAmount().compareTo(BigDecimal.ZERO) > 0) {
            var customer = dsl.selectFrom(CUSTOMERS)
                    .where(CUSTOMERS.ID.eq(ret.getCustomerId()))
                    .fetchOne();

            if (customer != null) {
                BigDecimal currentDebt = customer.getDebtAmount() == null
                        ? BigDecimal.ZERO
                        : customer.getDebtAmount();

                BigDecimal newDebt = currentDebt.subtract(ret.getAmount());
                if (newDebt.compareTo(BigDecimal.ZERO) < 0) {
                    newDebt = BigDecimal.ZERO;
                }

                dsl.update(CUSTOMERS)
                        .set(CUSTOMERS.DEBT_AMOUNT, newDebt)
                        .where(CUSTOMERS.ID.eq(ret.getCustomerId()))
                        .execute();
            }
        }

        return updated;
    }

    @Transactional
    public int rejectReturn(Long returnId, String reason) {
        ReturnsRecord ret = findById(returnId);
        if (ret == null) {
            throw new IllegalArgumentException("Qaytarish topilmadi: ID=" + returnId);
        }
        if (!"PENDING".equals(ret.getStatus())) {
            throw new IllegalStateException("Faqat PENDING holatidagi qaytarishni rad etish mumkin.");
        }

        return dsl.update(RETURNS)
                .set(RETURNS.STATUS, "REJECTED")
                .set(RETURNS.REASON, reason == null ? ret.getReason() : reason)
                .set(RETURNS.RESOLVED_AT, OffsetDateTime.now())
                .where(RETURNS.ID.eq(returnId))
                .execute();
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

    public List<ReturnsRecord> findByStatus(String status) {
        return dsl.selectFrom(RETURNS)
                .where(RETURNS.STATUS.eq(status.toUpperCase()))
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
}
