package io.salesdoctor.spring_mcp.mcp;

import io.salesdoctor.spring_mcp.domain.PaymentMethod;
import io.salesdoctor.spring_mcp.dto.DailyPaymentsDto;
import io.salesdoctor.spring_mcp.dto.OrderPaymentStatusDto;
import io.salesdoctor.spring_mcp.dto.PaymentDto;
import io.salesdoctor.spring_mcp.repository.OrderRepository;
import io.salesdoctor.spring_mcp.repository.PaymentRepository;
import io.salesdoctor.spring_mcp.support.AppTime;
import io.salesdoctor.spring_mcp.support.Require;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Component
public class PaymentMcpTools {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;

    public PaymentMcpTools(PaymentRepository paymentRepository, OrderRepository orderRepository) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
    }

    @McpTool(name = "acceptPayment",
            description = "Mijozdan to'lov qabul qiladi va qarzini kamaytiradi")
    public PaymentDto acceptPayment(
            @McpToolParam(description = "Mijoz ID si") Long customerId,
            @McpToolParam(description = "Buyurtma ID si (ixtiyoriy)", required = false) Long orderId,
            @McpToolParam(description = "To'lov miqdori (qarzdan oshmasligi kerak)") BigDecimal amount,
            @McpToolParam(description = "To'lov usuli: " + PaymentMethod.ALLOWED + " (default CASH)", required = false) String paymentMethod,
            @McpToolParam(description = "Agent ID si (ixtiyoriy)", required = false) Long agentId) {

        return PaymentDto.from(paymentRepository.acceptPayment(customerId, orderId, amount, paymentMethod, agentId));
    }

    @McpTool(name = "listPaymentsByCustomer",
            description = "Mijozning barcha to'lovlarini ko'rsatadi")
    public List<PaymentDto> listPaymentsByCustomer(
            @McpToolParam(description = "Mijoz ID si") Long customerId) {
        return paymentRepository.findByCustomer(customerId).stream().map(PaymentDto::from).toList();
    }

    @McpTool(name = "listPaymentsByOrder",
            description = "Buyurtma bo'yicha to'lovlarni ko'rsatadi")
    public List<PaymentDto> listPaymentsByOrder(
            @McpToolParam(description = "Buyurtma ID si") Long orderId) {
        return paymentRepository.findByOrder(orderId).stream().map(PaymentDto::from).toList();
    }

    @McpTool(name = "listPaymentsByAgent",
            description = "Agent tomonidan qabul qilingan to'lovlarni ko'rsatadi")
    public List<PaymentDto> listPaymentsByAgent(
            @McpToolParam(description = "Agent ID si") Long agentId) {
        return paymentRepository.findByAgent(agentId).stream().map(PaymentDto::from).toList();
    }

    @McpTool(name = "listRecentPayments",
            description = "Eng so'nggi 100 ta to'lovni ko'rsatadi")
    public List<PaymentDto> listRecentPayments() {
        return paymentRepository.findRecent().stream().map(PaymentDto::from).toList();
    }

    @McpTool(name = "dailyPaymentsTotal",
            description = "Kunlik to'lovlar umumiy summasini ko'rsatadi")
    public DailyPaymentsDto dailyPaymentsTotal(
            @McpToolParam(description = "Sana (YYYY-MM-DD), bo'sh bo'lsa bugun", required = false) String date) {

        LocalDate targetDate = AppTime.parseDate(date, AppTime.today());
        return new DailyPaymentsDto(targetDate, paymentRepository.totalByDate(targetDate));
    }

    @McpTool(name = "orderPaymentStatus",
            description = "Buyurtma bo'yicha summa, to'langan qism va qolgan qarzni ko'rsatadi")
    public OrderPaymentStatusDto orderPaymentStatus(
            @McpToolParam(description = "Buyurtma ID si") Long orderId) {

        var order = Require.found(orderRepository.findById(orderId), "Buyurtma topilmadi: ID=" + orderId);

        BigDecimal total = order.getTotalAmount();
        BigDecimal paid = paymentRepository.totalPaidForOrder(orderId);
        BigDecimal remaining = order.getStatus().isActive()
                ? total.subtract(paid).max(BigDecimal.ZERO)
                : BigDecimal.ZERO;

        return new OrderPaymentStatusDto(orderId, order.getStatus(), total, paid, remaining);
    }
}
