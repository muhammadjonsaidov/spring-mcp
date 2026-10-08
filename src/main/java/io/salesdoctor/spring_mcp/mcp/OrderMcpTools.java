package io.salesdoctor.spring_mcp.mcp;

import io.salesdoctor.spring_mcp.jooq.tables.records.OrderItemsRecord;
import io.salesdoctor.spring_mcp.jooq.tables.records.OrdersRecord;
import io.salesdoctor.spring_mcp.repository.OrderRepository;
import io.salesdoctor.spring_mcp.repository.OrderRepository.OrderItemInput;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class OrderMcpTools {

    private final OrderRepository orderRepository;

    public OrderMcpTools(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @McpTool(name = "createOrder",
            description = "Yangi buyurtma yaratadi. Har bir mahsulot uchun productId va quantity ko'rsatilishi kerak.")
    public String createOrder(
            @McpToolParam(description = "Mijoz ID si") Long customerId,
            @McpToolParam(description = "Agent ID si") Long agentId,
            @McpToolParam(description = "Mahsulotlar ro'yxati (format: productId:quantity,productId:quantity)") String itemsCsv,
            @McpToolParam(description = "Yetkazib berish sanasi (YYYY-MM-DD), bo'sh bo'lsa bugun") String deliveryDate
    ) {
        try {
            List<OrderItemInput> items = parseItems(itemsCsv);
            if (items.isEmpty()) {
                return "Xatolik: kamida bitta mahsulot kerak (format: productId:quantity)";
            }

            LocalDate date = (deliveryDate == null || deliveryDate.isBlank())
                    ? LocalDate.now().plusDays(1)
                    : LocalDate.parse(deliveryDate);

            OrdersRecord order = orderRepository.createOrder(customerId, agentId, items, date);
            return formatOrder(order);

        } catch (IllegalArgumentException | IllegalStateException e) {
            return "Xatolik: " + e.getMessage();
        } catch (Exception e) {
            return "Kutilmagan xatolik: " + e.getMessage();
        }
    }

    @McpTool(name = "getOrder",
            description = "Buyurtmani ID bo'yicha topadi (qatorlari bilan)")
    public String getOrder(
            @McpToolParam(description = "Buyurtma ID si") Long orderId
    ) {
        OrdersRecord order = orderRepository.findById(orderId);
        if (order == null) return "Buyurtma topilmadi: ID=" + orderId;

        List<OrderItemsRecord> items = orderRepository.findItemsByOrder(orderId);
        return formatOrderWithItems(order, items);
    }

    @McpTool(name = "listOrdersByCustomer",
            description = "Mijozning barcha buyurtmalarini ko'rsatadi")
    public String listOrdersByCustomer(
            @McpToolParam(description = "Mijoz ID si") Long customerId
    ) {
        List<OrdersRecord> orders = orderRepository.findByCustomer(customerId);
        if (orders.isEmpty()) return "Bu mijozda buyurtmalar yo'q.";

        return orders.stream().map(this::formatOrder).collect(Collectors.joining("\n"));
    }

    @McpTool(name = "listRecentOrders",
            description = "Eng so'nggi 100 ta buyurtmani ko'rsatadi")
    public String listRecentOrders() {
        List<OrdersRecord> orders = orderRepository.findAll();
        if (orders.isEmpty()) return "Buyurtmalar yo'q.";
        return orders.stream().map(this::formatOrder).collect(Collectors.joining("\n"));
    }

    @McpTool(name = "updateOrderStatus",
            description = "Buyurtma holatini o'zgartiradi (NEW, CONFIRMED, DELIVERED, CANCELLED)")
    public String updateOrderStatus(
            @McpToolParam(description = "Buyurtma ID si") Long orderId,
            @McpToolParam(description = "Yangi holat") String newStatus
    ) {
        int updated = orderRepository.updateStatus(orderId, newStatus);
        return updated > 0
                ? "Buyurtma holati yangilandi: ID=" + orderId + ", holat=" + newStatus.toUpperCase()
                : "Buyurtma topilmadi: ID=" + orderId;
    }

    @McpTool(name = "getOrderByNumber",
            description = "Buyurtmani order number (masalan ORD-A1B2C3D4) bo'yicha topadi")
    public String getOrderByNumber(
            @McpToolParam(description = "Buyurtma raqami (ORD-XXXXXXXX formatida)") String orderNumber
    ) {
        OrdersRecord order = orderRepository.findByOrderNumber(orderNumber);
        if (order == null) return "Buyurtma topilmadi: orderNumber=" + orderNumber;

        return formatOrder(order);
    }

    @McpTool(name = "listOrdersByAgent",
    description = "Agentning barcha buyurtmalarini ko'rsatadi")
    public String listOrdersByAgent(
            @McpToolParam(description = "Agent ID si") Long agentId
    ) {
        List<OrdersRecord> orders = orderRepository.findByAgent(agentId);
        if (orders.isEmpty()) return "Bu agentda buyurtmalar yo'q.";

        return orders.stream().map(this::formatOrder).collect(Collectors.joining("\n"));
    }

    private String formatOrderWithItems(OrdersRecord o, List<OrderItemsRecord> items) {
        String itemsJson = items.stream()
                .map(i -> String.format(
                        "{\"productId\": %d, \"quantity\": %d, \"unitPrice\": %s}",
                        i.getProductId(), i.getQuantity(), i.getUnitPrice()))
                .collect(Collectors.joining(","));

        return String.format(
                "{\"id\": %d, \"orderNumber\": \"%s\", \"status\": \"%s\", " +
                        "\"totalAmount\": %s, \"items\": [%s]}",
                o.getId(), o.getOrderNumber(), o.getStatus(), o.getTotalAmount(), itemsJson
        );
    }

    private String formatOrder(OrdersRecord o) {
        return String.format(
                "{\"id\": %d, \"orderNumber\": \"%s\", \"customerId\": %d, " +
                        "\"agentId\": %d, \"status\": \"%s\", \"totalAmount\": %s, " +
                        "\"deliveryDate\": \"%s\"}",
                o.getId(),
                o.getOrderNumber(),
                o.getCustomerId(),
                o.getAgentId(),
                o.getStatus(),
                o.getTotalAmount(),
                o.getDeliveryDate()
        );
    }

    private List<OrderItemInput> parseItems(String csv) {
        List<OrderItemInput> items = new ArrayList<>();
        if (csv == null || csv.isBlank()) return items;

        for (String pair : csv.split(",")) {
            String[] parts = pair.trim().split(":");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Noto'g'ri format: '" + pair + "'. Kutilgan format: productId:quantity");
            }

            items.add(new OrderItemInput(
                    Long.parseLong(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()))
            );
        }

        return items;
    }
}
