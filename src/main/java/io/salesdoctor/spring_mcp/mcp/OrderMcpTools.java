package io.salesdoctor.spring_mcp.mcp;

import io.salesdoctor.spring_mcp.domain.OrderStatus;
import io.salesdoctor.spring_mcp.dto.OrderDetailsDto;
import io.salesdoctor.spring_mcp.dto.OrderDto;
import io.salesdoctor.spring_mcp.dto.OrderItemDto;
import io.salesdoctor.spring_mcp.error.ToolException;
import io.salesdoctor.spring_mcp.repository.OrderRepository.OrderItemInput;
import io.salesdoctor.spring_mcp.repository.OrderRepository;
import io.salesdoctor.spring_mcp.support.AppTime;
import io.salesdoctor.spring_mcp.support.Require;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class OrderMcpTools {

    private final OrderRepository orderRepository;

    public OrderMcpTools(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @McpTool(name = "createOrder",
            description = "Yangi buyurtma yaratadi. Har bir mahsulot uchun productId va quantity ko'rsatilishi kerak.")
    public OrderDto createOrder(
            @McpToolParam(description = "Mijoz ID si") Long customerId,
            @McpToolParam(description = "Agent ID si") Long agentId,
            @McpToolParam(description = "Mahsulotlar ro'yxati (format: productId:quantity,productId:quantity)") String itemsCsv,
            @McpToolParam(description = "Yetkazib berish sanasi (YYYY-MM-DD), bo'sh bo'lsa ertaga", required = false) String deliveryDate
    ) {
        List<OrderItemInput> items = parseItems(itemsCsv);
        Require.that(!items.isEmpty(), "Kamida bitta mahsulot kerak (format: productId:quantity)");

        LocalDate date = AppTime.parseDate(deliveryDate, AppTime.today().plusDays(1));
        return OrderDto.from(orderRepository.createOrder(customerId, agentId, items, date));
    }

    @McpTool(name = "getOrder",
            description = "Buyurtmani ID bo'yicha topadi (qatorlari bilan)")
    public OrderDetailsDto getOrder(
            @McpToolParam(description = "Buyurtma ID si") Long orderId
    ) {
        var order = Require.found(orderRepository.findById(orderId), "Buyurtma topilmadi: ID=" + orderId);
        List<OrderItemDto> items = orderRepository.findItemsByOrder(orderId).stream()
                .map(OrderItemDto::from)
                .toList();
        return new OrderDetailsDto(OrderDto.from(order), items);
    }

    @McpTool(name = "listOrdersByCustomer",
            description = "Mijozning barcha buyurtmalarini ko'rsatadi")
    public List<OrderDto> listOrdersByCustomer(
            @McpToolParam(description = "Mijoz ID si") Long customerId
    ) {
        return orderRepository.findByCustomer(customerId).stream().map(OrderDto::from).toList();
    }

    @McpTool(name = "listRecentOrders",
            description = "Eng so'nggi 100 ta buyurtmani ko'rsatadi")
    public List<OrderDto> listRecentOrders() {
        return orderRepository.findAll().stream().map(OrderDto::from).toList();
    }

    @McpTool(name = "updateOrderStatus",
            description = "Buyurtma holatini o'zgartiradi. O'tishlar: " + OrderStatus.TRANSITIONS_TEXT +
                    ". Bekor qilinganda mahsulot, qarz va KPI qaytariladi.")
    public OrderDto updateOrderStatus(
            @McpToolParam(description = "Buyurtma ID si") Long orderId,
            @McpToolParam(description = "Yangi holat: " + OrderStatus.ALLOWED) String newStatus
    ) {
        orderRepository.updateStatus(orderId, OrderStatus.parse(newStatus));
        return OrderDto.from(orderRepository.findById(orderId));
    }

    @McpTool(name = "getOrderByNumber",
            description = "Buyurtmani order number (masalan ORD-A1B2C3D4) bo'yicha topadi")
    public OrderDto getOrderByNumber(
            @McpToolParam(description = "Buyurtma raqami (ORD-XXXXXXXX formatida)") String orderNumber
    ) {
        return OrderDto.from(Require.found(orderRepository.findByOrderNumber(orderNumber),
                "Buyurtma topilmadi: orderNumber=" + orderNumber));
    }

    @McpTool(name = "listOrdersByAgent",
            description = "Agentning barcha buyurtmalarini ko'rsatadi")
    public List<OrderDto> listOrdersByAgent(
            @McpToolParam(description = "Agent ID si") Long agentId
    ) {
        return orderRepository.findByAgent(agentId).stream().map(OrderDto::from).toList();
    }

    private List<OrderItemInput> parseItems(String csv) {
        List<OrderItemInput> items = new ArrayList<>();
        if (csv == null || csv.isBlank()) return items;

        for (String pair : csv.split(",")) {
            String[] parts = pair.trim().split(":");
            if (parts.length != 2) {
                throw ToolException.invalid("Noto'g'ri format: '" + pair + "'. Kutilgan format: productId:quantity");
            }

            long productId;
            int quantity;
            try {
                productId = Long.parseLong(parts[0].trim());
                quantity = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException e) {
                throw ToolException.invalid("Noto'g'ri son: '" + pair + "'. Kutilgan format: productId:quantity");
            }
            if (quantity <= 0) {
                throw ToolException.invalid("Miqdor 0 dan katta bo'lishi kerak: '" + pair + "'");
            }
            items.add(new OrderItemInput(productId, quantity));
        }

        return items;
    }
}
