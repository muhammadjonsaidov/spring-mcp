package io.salesdoctor.spring_mcp.mcp;

import io.salesdoctor.spring_mcp.jooq.tables.records.StockRecord;
import io.salesdoctor.spring_mcp.repository.StockRepository;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class StockMcpTools {

    private final StockRepository stockRepository;

    public StockMcpTools(StockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    @McpTool(name = "addStock",
            description = "Omborga mahsulot qo'shadi (yoki mavjudini ko'paytiradi)")
    public String addStock(
            @McpToolParam(description = "Mahsulot ID si") Long productId,
            @McpToolParam(description = "Qo'shiladigan miqdor") Integer quantity
    ) {
        stockRepository.upsertWarehouseStock(productId, quantity);
        return "Omborga qo'shildi: productId=" + productId + ", miqdor=" + quantity;
    }

    @McpTool(name = "getStock",
            description = "Bitta mahsulotning ombor qoldig'ini ko'rsatadi")
    public String getStock(
            @McpToolParam(description = "Mahsulot ID si") Long productId
    ) {
        StockRecord stock = stockRepository.getWarehouseStock(productId);
        if (stock == null) return "Omborda bu mahsulot yo'q: productId=" + productId;
        return String.format("{\"productId\": %d, \"quantity\": %d}",
                stock.getProductId(), stock.getQuantity());
    }

    @McpTool(name = "listWarehouseStock",
            description = "Umumiy ombordagi barcha qoldiqlarni ko'rsatadi")
    public String listWarehouseStock() {
        List<StockRecord> stocks = stockRepository.findAllWarehouseStock();
        if (stocks.isEmpty()) return "Omborda mahsulot yo'q.";
        return stocks.stream()
                .map(s -> String.format("{\"productId\": %d, \"quantity\": %d}",
                        s.getProductId(), s.getQuantity()))
                .collect(Collectors.joining("\n"));
    }

    @McpTool(name = "listAgentStock",
            description = "Agentning avtomashinasidagi (van) barcha mahsulot qoldiqlarini ko'rsatadi")
    public String listAgentStock(
            @McpToolParam(description = "Agent ID si") Long agentId
    ) {
        List<StockRecord> stocks = stockRepository.findAgentStock(agentId);
        if (stocks.isEmpty()) return "Agentda mahsulot yo'q: agentId=" + agentId;

        return stocks.stream()
                .map(s -> String.format("{\"productId\": %d, \"quantity\": %d, \"location\": \"%s\"}",
                        s.getProductId(), s.getQuantity(), s.getWarehouseLocation()))
                .collect(Collectors.joining("\n"));
    }

    @McpTool(name = "getAgentStock",
            description = "Agentdagi bitta mahsulot qoldig'ini ko'rsatadi")
    public String getAgentStock(
            @McpToolParam(description = "Mahsulot ID si") Long productId,
            @McpToolParam(description = "Agent ID si") Long agentId
    ) {
        StockRecord stock = stockRepository.getAgentStock(productId, agentId);
        if (stock == null) {
            return String.format("Agentda bu mahsulot yo'q: productId=%d, agentId=%d",
                    productId, agentId);
        }
        return String.format("{\"productId\": %d, \"agentId\": %d, \"quantity\": %d}",
                stock.getProductId(), stock.getAgentId(), stock.getQuantity());
    }

    @McpTool(name = "transferStockToAgent",
            description = "Asosiy ombordan agentga mahsulot o'tkazadi (van yuklash)")
    public String transferStockToAgent(
            @McpToolParam(description = "Mahsulot ID si") Long productId,
            @McpToolParam(description = "Agent ID si") Long agentId,
            @McpToolParam(description = "O'tkaziladigan miqdor") Integer quantity
    ) {
        if (quantity <= 0) {
            return "Xatolik: miqdor 0 dan katta bo'lishi kerak.";
        }
        StockRecord warehouseStock = stockRepository.getWarehouseStock(productId);
        if (warehouseStock == null) {
            return "Xatolik: asosiy omborda bu mahsulot yo'q. productId=" + productId;
        }
        if (warehouseStock.getQuantity() < quantity) {
            return String.format(
                    "Xatolik: asosiy omborda yetarli mahsulot yo'q. Mavjud: %d, kerak: %d",
                    warehouseStock.getQuantity(), quantity
            );
        }

        int decreased = stockRepository.decreaseWarehouseStock(productId, quantity);
        if (decreased == 0) {
            return "Xatolik: asosiy omborni kamaytirishda muammo yuz berdi.";
        }
        stockRepository.upsertAgentStock(productId, agentId, quantity);
        return String.format(
                "O'tkazildi: productId=%d, agentId=%d, miqdor=%d",
                productId, agentId, quantity
        );
    }

    @McpTool(name = "returnStockFromAgent",
            description = "Agentdan asosiy omborga mahsulot qaytaradi (kun oxirida)")
    public String returnStockFromAgent(
            @McpToolParam(description = "Mahsulot ID si") Long productId,
            @McpToolParam(description = "Agent ID si") Long agentId,
            @McpToolParam(description = "Qaytariladigan miqdor") Integer quantity
    ) {
        if (quantity <= 0) {
            return "Xatolik: miqdor 0 dan katta bo'lishi kerak.";
        }

        StockRecord agentStock = stockRepository.getAgentStock(productId, agentId);
        if (agentStock == null) {
            return String.format("Xatolik: agentda bu mahsulot yo'q. productId=%d, agentId=%d",
                    productId, agentId);
        }
        if (agentStock.getQuantity() < quantity) {
            return String.format(
                    "Xatolik: agentda yetarli mahsulot yo'q. Mavjud: %d, kerak: %d",
                    agentStock.getQuantity(), quantity
            );
        }

        int decreased = stockRepository.decreaseAgentStock(productId, agentId, quantity);
        if (decreased == 0) {
            return "Xatolik: agent omborini kamaytirishda muammo yuz berdi.";
        }
        stockRepository.upsertWarehouseStock(productId, quantity);

        return String.format(
                "Qaytarildi: productId=%d, agentId=%d, miqdor=%d",
                productId, agentId, quantity
        );
    }

    @McpTool(name = "getAgentStockValue",
            description = "Agentdagi barcha mahsulotlarning umumiy qiymatini hisoblaydi")
    public String getAgentStockValue(
            @McpToolParam(description = "Agent ID si") Long agentId
    ) {
        BigDecimal value = stockRepository.calculateAgentStockValue(agentId);
        return String.format("{\"agentId\": %d, \"totalValue\": %s}", agentId, value);
    }
}
