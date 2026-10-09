package io.salesdoctor.spring_mcp.mcp;

import io.salesdoctor.spring_mcp.jooq.tables.records.ReturnsRecord;
import io.salesdoctor.spring_mcp.repository.ReturnRepository;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class ReturnMcpTools {

    private final ReturnRepository returnRepository;

    public ReturnMcpTools(ReturnRepository returnRepository) {
        this.returnRepository = returnRepository;
    }

    @McpTool(name = "createReturn",
            description = "Yangi qaytarish yaratadi (PENDING holatida). Tasdiqlash uchun approveReturn ishlatiladi.")
    public String createReturn(
            @McpToolParam(description = "Mijoz ID si") Long customerId,
            @McpToolParam(description = "Buyurtma ID si (ixtiyoriy)") Long orderId,
            @McpToolParam(description = "Mahsulot ID si") Long productId,
            @McpToolParam(description = "Qaytariladigan miqdor") Integer quantity,
            @McpToolParam(description = "Qaytariladigan summa") BigDecimal amount,
            @McpToolParam(description = "Sababi") String reason,
            @McpToolParam(description = "Agent ID si (ixtiyoriy)") Long agentId) {

        try {
            ReturnsRecord ret = returnRepository.createReturn(
                    customerId, orderId, productId, quantity, amount, reason, agentId
            );
            return format(ret);
        } catch (IllegalArgumentException e) {
            return "Xatolik: " + e.getMessage();
        } catch (Exception e) {
            return "Kutilmagan xatolik: " + e.getMessage();
        }
    }

    @McpTool(name = "approveReturn",
            description = "Qaytarishni tasdiqlaydi: ombor qoldig'ini oshiradi va mijoz qarzini kamaytiradi")
    public String approveReturn(
            @McpToolParam(description = "Qaytarish ID si") Long returnId) {

        try {
            int updated = returnRepository.approveReturn(returnId);
            return updated > 0
                    ? "Qaytarish tasdiqlandi: ID=" + returnId +
                      " (ombor qoldig'i oshdi, mijoz qarzi kamaydi)"
                    : "Qaytarish topilmadi: ID=" + returnId;
        } catch (IllegalArgumentException | IllegalStateException e) {
            return "Xatolik: " + e.getMessage();
        }
    }

    @McpTool(name = "rejectReturn",
            description = "Qaytarishni rad etadi")
    public String rejectReturn(
            @McpToolParam(description = "Qaytarish ID si") Long returnId,
            @McpToolParam(description = "Rad etish sababi") String reason) {

        try {
            int updated = returnRepository.rejectReturn(returnId, reason);
            return updated > 0
                    ? "Qaytarish rad etildi: ID=" + returnId
                    : "Qaytarish topilmadi: ID=" + returnId;
        } catch (IllegalArgumentException | IllegalStateException e) {
            return "Xatolik: " + e.getMessage();
        }
    }

    @McpTool(name = "getReturn",
            description = "Qaytarishni ID bo'yicha topadi")
    public String getReturn(
            @McpToolParam(description = "Qaytarish ID si") Long returnId) {
        ReturnsRecord ret = returnRepository.findById(returnId);
        if (ret == null) return "Qaytarish topilmadi: ID=" + returnId;
        return format(ret);
    }

    @McpTool(name = "listReturnsByCustomer",
            description = "Mijozning barcha qaytarishlarini ko'rsatadi")
    public String listReturnsByCustomer(
            @McpToolParam(description = "Mijoz ID si") Long customerId) {
        List<ReturnsRecord> returns = returnRepository.findByCustomer(customerId);
        if (returns.isEmpty()) return "Bu mijozda qaytarishlar yo'q.";
        return returns.stream().map(this::format).collect(Collectors.joining("\n"));
    }

    @McpTool(name = "listReturnsByStatus",
            description = "Holat bo'yicha qaytarishlarni ko'rsatadi (PENDING, APPROVED, REJECTED)")
    public String listReturnsByStatus(
            @McpToolParam(description = "Holat") String status) {
        List<ReturnsRecord> returns = returnRepository.findByStatus(status);
        if (returns.isEmpty()) return "'" + status + "' holatidagi qaytarishlar yo'q.";
        return returns.stream().map(this::format).collect(Collectors.joining("\n"));
    }

    @McpTool(name = "listPendingReturns",
            description = "Tasdiqlanmagan (PENDING) qaytarishlarni ko'rsatadi")
    public String listPendingReturns() {
        List<ReturnsRecord> returns = returnRepository.findByStatus("PENDING");
        if (returns.isEmpty()) return "Kutilayotgan qaytarishlar yo'q.";
        return returns.stream().map(this::format).collect(Collectors.joining("\n"));
    }

    @McpTool(name = "listReturnsByAgent",
            description = "Agent bo'yicha qaytarishlarni ko'rsatadi")
    public String listReturnsByAgent(
            @McpToolParam(description = "Agent ID si") Long agentId) {
        List<ReturnsRecord> returns = returnRepository.findByAgent(agentId);
        if (returns.isEmpty()) return "Bu agentda qaytarishlar yo'q.";
        return returns.stream().map(this::format).collect(Collectors.joining("\n"));
    }

    @McpTool(name = "listRecentReturns",
            description = "Eng so'nggi 100 ta qaytarishni ko'rsatadi")
    public String listRecentReturns() {
        List<ReturnsRecord> returns = returnRepository.findAll();
        if (returns.isEmpty()) return "Qaytarishlar yo'q.";
        return returns.stream().map(this::format).collect(Collectors.joining("\n"));
    }

    private String format(ReturnsRecord r) {
        return String.format(
                "{\"id\": %d, \"customerId\": %d, \"orderId\": %s, " +
                        "\"productId\": %s, \"quantity\": %d, \"amount\": %s, " +
                        "\"status\": \"%s\", \"reason\": \"%s\"}",
                r.getId(),
                r.getCustomerId(),
                r.getOrderId() == null ? "null" : r.getOrderId().toString(),
                r.getProductId() == null ? "null" : r.getProductId().toString(),
                r.getQuantity() == null ? 0 : r.getQuantity(),
                r.getAmount() == null ? BigDecimal.ZERO : r.getAmount(),
                r.getStatus(),
                esc(r.getReason())
        );
    }

    private String esc(String s) {
        return s == null ? "" : s.replace("\"", "\\\"");
    }
}
