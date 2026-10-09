package io.salesdoctor.spring_mcp.repository;

import io.salesdoctor.spring_mcp.domain.PaymentMethod;
import io.salesdoctor.spring_mcp.jooq.tables.records.PaymentsRecord;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static io.salesdoctor.spring_mcp.jooq.Tables.AGENTS;
import static io.salesdoctor.spring_mcp.jooq.Tables.ORDERS;
import static io.salesdoctor.spring_mcp.jooq.Tables.PAYMENTS;

@Repository
public class PaymentRepository {

    private final DSLContext dsl;
    private final CustomerRepository customerRepository;

    public PaymentRepository(DSLContext dsl, CustomerRepository customerRepository) {
        this.dsl = dsl;
        this.customerRepository = customerRepository;
    }

    @Transactional
    public PaymentsRecord acceptPayment(
            Long customerId, Long orderId, BigDecimal amount,
            String paymentMethod, Long agentId
    ) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("To'lov miqdori 0 dan katta bo'lishi kerak.");
        }

        PaymentMethod method = PaymentMethod.parseOrDefault(paymentMethod);

        // Qator qulflanadi: bir vaqtdagi to'lovlar qarzni navbat bilan kamaytiradi
        var customer = customerRepository.findByIdForUpdate(customerId);
        if (customer == null) {
            throw new IllegalArgumentException("Mijoz topilmadi: ID=" + customerId);
        }

        BigDecimal currentDebt = customer.getDebtAmount() == null
                ? BigDecimal.ZERO
                : customer.getDebtAmount();
        if (amount.compareTo(currentDebt) > 0) {
            throw new IllegalArgumentException(
                    "To'lov summasi mijoz qarzidan katta: to'lov=" + amount + ", qarz=" + currentDebt);
        }

        if (orderId != null) {
            var order = dsl.selectFrom(ORDERS).where(ORDERS.ID.eq(orderId)).fetchOne();
            if (order == null) {
                throw new IllegalArgumentException("Buyurtma topilmadi: ID=" + orderId);
            }
            if (!order.getCustomerId().equals(customerId)) {
                throw new IllegalArgumentException(
                        "Buyurtma bu mijozga tegishli emas: orderId=" + orderId + ", customerId=" + customerId);
            }
            if (!order.getStatus().isActive()) {
                throw new IllegalStateException("Bekor qilingan buyurtmaga to'lov qabul qilinmaydi: ID=" + orderId);
            }
            BigDecimal remaining = order.getTotalAmount().subtract(totalPaidForOrder(orderId));
            if (amount.compareTo(remaining) > 0) {
                throw new IllegalArgumentException(
                        "To'lov summasi buyurtma qoldig'idan katta: to'lov=" + amount + ", qoldiq=" + remaining);
            }
        }

        if (agentId != null && !dsl.fetchExists(AGENTS, AGENTS.ID.eq(agentId))) {
            throw new IllegalArgumentException("Agent topilmadi: ID=" + agentId);
        }

        PaymentsRecord payment = dsl.insertInto(PAYMENTS)
                .set(PAYMENTS.CUSTOMER_ID, customerId)
                .set(PAYMENTS.ORDER_ID, orderId)
                .set(PAYMENTS.AMOUNT, amount)
                .set(PAYMENTS.PAYMENT_METHOD, method)
                .set(PAYMENTS.AGENT_ID, agentId)
                .returning()
                .fetchOne();

        customerRepository.decreaseDebt(customerId, amount);
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
        var result = dsl.select(DSL.sum(PAYMENTS.AMOUNT))
                .from(PAYMENTS)
                .where(PAYMENTS.CREATED_AT.cast(LocalDate.class).eq(date))
                .fetchOne();

        return result == null || result.value1() == null
                ? BigDecimal.ZERO
                : result.value1();
    }

    public BigDecimal totalPaidForOrder(Long orderId) {
        var result = dsl.select(DSL.sum(PAYMENTS.AMOUNT))
                .from(PAYMENTS)
                .where(PAYMENTS.ORDER_ID.eq(orderId))
                .fetchOne();

        return result == null || result.value1() == null
                ? BigDecimal.ZERO
                : result.value1();
    }
}
