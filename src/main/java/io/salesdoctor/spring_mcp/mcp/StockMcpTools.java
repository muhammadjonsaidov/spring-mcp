package io.salesdoctor.spring_mcp.mcp;

import io.salesdoctor.spring_mcp.jooq.tables.records.StockRecord;
import io.salesdoctor.spring_mcp.repository.StockRepository;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

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
            @McpToolParam(description = "Qo'shiladigan miqdor") Integer quantity) {

        stockRepository.upsertWarehouseStock(productId, quantity);
        return "Omborga qo'shildi: productId=" + productId + ", miqdor=" + quantity;
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

    @McpTool(name = "getStock",
            description = "Bitta mahsulotning ombor qoldig'ini ko'rsatadi")
    public String getStock(
            @McpToolParam(description = "Mahsulot ID si") Long productId) {
        StockRecord stock = stockRepository.getWarehouseStock(productId);
        if (stock == null) return "Omborda bu mahsulot yo'q: productId=" + productId;
        return String.format("{\"productId\": %d, \"quantity\": %d}",
                stock.getProductId(), stock.getQuantity());
    }

    @McpTool(name = "listAgentStock",
            description = "Agentning avtomashinasidagi (van) barcha mahsulot qoldiqlarini ko'rsatadi")
    public String listAgentStock(
            @McpToolParam(description = "Agent ID si") Long agentId) {

        List<StockRecord> stocks = stockRepository.findAgentStock(agentId);
        if (stocks.isEmpty()) return "Agentda mahsulot yo'q: agentId=" + agentId;

        return stocks.stream()
                .map(s -> String.format("{\"productId\": %d, \"quantity\": %d, \"location\": \"%s\"}",
                        s.getProductId(), s.getQuantity(), s.getWarehouseLocation()))
                .collect(Collectors.joining("\n"));
    }

    @McpTool(name = "transferStockToAgent",
            description = "Asosiy ombordan agentga mahsulot o'tkazadi (van yuklash)")
    public String transferStockToAgent(
            @McpToolParam(description = "Mahsulot ID si") Long productId,
            @McpToolParam(description = "Agent ID si") Long agentId,
            @McpToolParam(description = "O'tkaziladigan miqdor") Integer quantity) {

        // 1. Asosiy omborda yetarli miqdor borligini tekshirish
        StockRecord warehouseStock = stockRepository.getWarehouseStock(productId);
        if (warehouseStock == null || warehouseStock.getQuantity() < quantity) {
            return "Xatolik: asosiy omborda yetarli mahsulot yo'q. Mavjud: " +
                    (warehouseStock == null ? 0 : warehouseStock.getQuantity());
        }

        // 2. Asosiy ombordan kamaytirish
        stockRepository.decreaseWarehouseStock(productId, quantity);

        // 3. Agentga qo'shish
        stockRepository.upsertAgentStock(productId, agentId, quantity);

        return String.format(
                "O'tkazildi: productId=%d, agentId=%d, miqdor=%d",
                productId, agentId, quantity
        );
    }
}
