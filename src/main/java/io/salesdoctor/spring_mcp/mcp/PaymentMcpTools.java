package io.salesdoctor.spring_mcp.mcp;

import io.salesdoctor.spring_mcp.jooq.tables.records.PaymentsRecord;
import io.salesdoctor.spring_mcp.repository.PaymentRepository;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class PaymentMcpTools {

    private final PaymentRepository paymentRepository;

    public PaymentMcpTools(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @McpTool(name = "acceptPayment",
            description = "Mijozdan to'lov qabul qiladi va qarzini kamaytiradi")
    public String acceptPayment(
            @McpToolParam(description = "Mijoz ID si") Long customerId,
            @McpToolParam(description = "Buyurtma ID si (ixtiyoriy)") Long orderId,
            @McpToolParam(description = "To'lov miqdori") BigDecimal amount,
            @McpToolParam(description = "To'lov usuli: CASH, CARD, TRANSFER (default CASH)") String paymentMethod,
            @McpToolParam(description = "Agent ID si (ixtiyoriy)") Long agentId) {

        try {
            PaymentsRecord payment = paymentRepository.acceptPayment(
                    customerId, orderId, amount, paymentMethod, agentId
            );
            return format(payment);
        } catch (IllegalArgumentException e) {
            return "Xatolik: " + e.getMessage();
        } catch (Exception e) {
            return "Kutilmagan xatolik: " + e.getMessage();
        }
    }

    @McpTool(name = "listPaymentsByCustomer",
            description = "Mijozning barcha to'lovlarini ko'rsatadi")
    public String listPaymentsByCustomer(
            @McpToolParam(description = "Mijoz ID si") Long customerId) {

        List<PaymentsRecord> payments = paymentRepository.findByCustomer(customerId);
        if (payments.isEmpty()) return "Bu mijozda to'lovlar yo'q.";
        return payments.stream().map(this::format).collect(Collectors.joining("\n"));
    }

    @McpTool(name = "listPaymentsByOrder",
            description = "Buyurtma bo'yicha to'lovlarni ko'rsatadi")
    public String listPaymentsByOrder(
            @McpToolParam(description = "Buyurtma ID si") Long orderId) {

        List<PaymentsRecord> payments = paymentRepository.findByOrder(orderId);
        if (payments.isEmpty()) return "Bu buyurtma uchun to'lovlar yo'q.";
        return payments.stream().map(this::format).collect(Collectors.joining("\n"));
    }

    @McpTool(name = "listPaymentsByAgent",
            description = "Agent tomonidan qabul qilingan to'lovlarni ko'rsatadi")
    public String listPaymentsByAgent(
            @McpToolParam(description = "Agent ID si") Long agentId) {

        List<PaymentsRecord> payments = paymentRepository.findByAgent(agentId);
        if (payments.isEmpty()) return "Bu agentda to'lovlar yo'q.";
        return payments.stream().map(this::format).collect(Collectors.joining("\n"));
    }

    @McpTool(name = "listRecentPayments",
            description = "Eng so'nggi 100 ta to'lovni ko'rsatadi")
    public String listRecentPayments() {
        List<PaymentsRecord> payments = paymentRepository.findRecent();
        if (payments.isEmpty()) return "To'lovlar yo'q.";
        return payments.stream().map(this::format).collect(Collectors.joining("\n"));
    }

    @McpTool(name = "dailyPaymentsTotal",
            description = "Kunlik to'lovlar umumiy summasini ko'rsatadi")
    public String dailyPaymentsTotal(
            @McpToolParam(description = "Sana (YYYY-MM-DD), bo'sh bo'lsa bugun") String date) {

        LocalDate targetDate = (date == null || date.isBlank())
                ? LocalDate.now()
                : LocalDate.parse(date);

        BigDecimal total = paymentRepository.totalByDate(targetDate);
        return String.format("{\"date\": \"%s\", \"totalPayments\": %s}", targetDate, total);
    }

    @McpTool(name = "orderPaymentStatus",
            description = "Buyurtma bo'yicha to'langan summani va qarzni ko'rsatadi")
    public String orderPaymentStatus(
            @McpToolParam(description = "Buyurtma ID si") Long orderId) {

        BigDecimal paid = paymentRepository.totalPaidForOrder(orderId);
        return String.format("{\"orderId\": %d, \"totalPaid\": %s}", orderId, paid);
    }

    private String format(PaymentsRecord p) {
        return String.format(
                "{\"id\": %d, \"customerId\": %d, \"orderId\": %s, " +
                        "\"amount\": %s, \"method\": \"%s\", \"agentId\": %s}",
                p.getId(),
                p.getCustomerId(),
                p.getOrderId() == null ? "null" : p.getOrderId().toString(),
                p.getAmount(),
                p.getPaymentMethod(),
                p.getAgentId() == null ? "null" : p.getAgentId().toString()
        );
    }
}
