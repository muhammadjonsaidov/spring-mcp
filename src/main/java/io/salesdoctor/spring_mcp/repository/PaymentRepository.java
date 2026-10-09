package io.salesdoctor.spring_mcp.repository;

import io.salesdoctor.spring_mcp.jooq.tables.records.PaymentsRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static io.salesdoctor.spring_mcp.jooq.Tables.CUSTOMERS;
import static io.salesdoctor.spring_mcp.jooq.Tables.PAYMENTS;

@Repository
public class PaymentRepository {

    private final DSLContext dsl;

    public PaymentRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Transactional
    public PaymentsRecord acceptPayment(
            Long customerId, Long orderId, BigDecimal amount,
            String paymentMethod, Long agentId
    ) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("To'lov miqdori 0 dan katta bo'lishi kerak.");
        }

        var customer = dsl.selectFrom(CUSTOMERS)
                .where(CUSTOMERS.ID.eq(customerId))
                .fetchOne();
        if (customer == null) {
            throw new IllegalArgumentException("Mijoz topilmadi: ID=" + customerId);
        }

        PaymentsRecord payment = dsl.insertInto(PAYMENTS)
                .set(PAYMENTS.CUSTOMER_ID, customerId)
                .set(PAYMENTS.ORDER_ID, orderId)
                .set(PAYMENTS.AMOUNT, amount)
                .set(PAYMENTS.PAYMENT_METHOD, paymentMethod == null ? "CASH" : paymentMethod.toUpperCase())
                .set(PAYMENTS.AGENT_ID, agentId)
                .returning()
                .fetchOne();

        BigDecimal currentDebt = customer.getDebtAmount() == null
                ? BigDecimal.ZERO
                : customer.getDebtAmount();

        BigDecimal newDebt = currentDebt.subtract(amount);
        if (newDebt.compareTo(BigDecimal.ZERO) < 0) {
            newDebt = BigDecimal.ZERO;
        }

        dsl.update(CUSTOMERS)
                .set(CUSTOMERS.DEBT_AMOUNT, newDebt)
                .where(CUSTOMERS.ID.eq(customerId))
                .execute();

        return payment;
    }

    public PaymentsRecord findById(Long id) {
        return dsl.selectFrom(PAYMENTS)
                .where(PAYMENTS.ID.eq(id))
                .fetchOne();
    }

    public List<PaymentsRecord> findByCustomer(Long customerId) {
        return dsl.selectFrom(PAYMENTS)
                .where(PAYMENTS.CUSTOMER_ID.eq(customerId))
                .orderBy(PAYMENTS.CREATED_AT.desc())
                .fetch();
    }

    public List<PaymentsRecord> findByOrder(Long orderId) {
        return dsl.selectFrom(PAYMENTS)
                .where(PAYMENTS.ORDER_ID.eq(orderId))
                .orderBy(PAYMENTS.CREATED_AT.desc())
                .fetch();
    }

    public List<PaymentsRecord> findByAgent(Long agentId) {
        return dsl.selectFrom(PAYMENTS)
                .where(PAYMENTS.AGENT_ID.eq(agentId))
                .orderBy(PAYMENTS.CREATED_AT.desc())
                .fetch();
    }

    public List<PaymentsRecord> findRecent() {
        return dsl.selectFrom(PAYMENTS)
                .orderBy(PAYMENTS.CREATED_AT.desc())
                .limit(100)
                .fetch();
    }

    public BigDecimal totalByDate(LocalDate date) {
        var result = dsl.select(org.jooq.impl.DSL.sum(PAYMENTS.AMOUNT))
                .from(PAYMENTS)
                .where(PAYMENTS.CREATED_AT.cast(LocalDate.class).eq(date))
                .fetchOne();

        return result == null || result.value1() == null
                ? BigDecimal.ZERO
                : result.value1();
    }

    public BigDecimal totalPaidForOrder(Long orderId) {
        var result = dsl.select(org.jooq.impl.DSL.sum(PAYMENTS.AMOUNT))
                .from(PAYMENTS)
                .where(PAYMENTS.ORDER_ID.eq(orderId))
                .fetchOne();

        return result == null || result.value1() == null
                ? BigDecimal.ZERO
                : result.value1();
    }
}
