package io.salesdoctor.spring_mcp.mcp;

import io.salesdoctor.spring_mcp.dto.StockDto;
import io.salesdoctor.spring_mcp.dto.StockTransferDto;
import io.salesdoctor.spring_mcp.dto.StockValueDto;
import io.salesdoctor.spring_mcp.jooq.tables.records.StockRecord;
import io.salesdoctor.spring_mcp.repository.StockRepository;
import io.salesdoctor.spring_mcp.support.Require;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class StockMcpTools {

    private final StockRepository stockRepository;

    public StockMcpTools(StockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    @McpTool(name = "addStock",
            description = "Omborga mahsulot qo'shadi (yoki mavjudini ko'paytiradi)")
    public StockDto addStock(
            @McpToolParam(description = "Mahsulot ID si") Long productId,
            @McpToolParam(description = "Qo'shiladigan miqdor (0 dan katta)") Integer quantity
    ) {
        stockRepository.addToWarehouse(productId, requirePositive(quantity));
        return getStock(productId);
    }

    @McpTool(name = "getStock",
            description = "Bitta mahsulotning ombor qoldig'ini ko'rsatadi")
    public StockDto getStock(
            @McpToolParam(description = "Mahsulot ID si") Long productId
    ) {
        return StockDto.from(Require.found(stockRepository.getWarehouseStock(productId),
                "Omborda bu mahsulot yo'q: productId=" + productId));
    }

    @McpTool(name = "listWarehouseStock",
            description = "Umumiy ombordagi barcha qoldiqlarni ko'rsatadi")
    public List<StockDto> listWarehouseStock() {
        return stockRepository.findAllWarehouseStock().stream().map(StockDto::from).toList();
    }

    @McpTool(name = "listAgentStock",
            description = "Agentning avtomashinasidagi (van) barcha mahsulot qoldiqlarini ko'rsatadi")
    public List<StockDto> listAgentStock(
            @McpToolParam(description = "Agent ID si") Long agentId
    ) {
        return stockRepository.findAgentStock(agentId).stream().map(StockDto::from).toList();
    }

    @McpTool(name = "getAgentStock",
            description = "Agentdagi bitta mahsulot qoldig'ini ko'rsatadi")
    public StockDto getAgentStock(
            @McpToolParam(description = "Mahsulot ID si") Long productId,
            @McpToolParam(description = "Agent ID si") Long agentId
    ) {
        return StockDto.from(Require.found(stockRepository.getAgentStock(productId, agentId),
                "Agentda bu mahsulot yo'q: productId=" + productId + ", agentId=" + agentId));
    }

    @McpTool(name = "transferStockToAgent",
            description = "Asosiy ombordan agentga mahsulot o'tkazadi (van yuklash)")
    public StockTransferDto transferStockToAgent(
            @McpToolParam(description = "Mahsulot ID si") Long productId,
            @McpToolParam(description = "Agent ID si") Long agentId,
            @McpToolParam(description = "O'tkaziladigan miqdor (0 dan katta)") Integer quantity
    ) {
        int amount = requirePositive(quantity);
        stockRepository.transferToAgent(productId, agentId, amount);
        return balances(productId, agentId, amount);
    }

    @McpTool(name = "returnStockFromAgent",
            description = "Agentdan asosiy omborga mahsulot qaytaradi (kun oxirida)")
    public StockTransferDto returnStockFromAgent(
            @McpToolParam(description = "Mahsulot ID si") Long productId,
            @McpToolParam(description = "Agent ID si") Long agentId,
            @McpToolParam(description = "Qaytariladigan miqdor (0 dan katta)") Integer quantity
    ) {
        int amount = requirePositive(quantity);
        stockRepository.returnFromAgent(productId, agentId, amount);
        return balances(productId, agentId, amount);
    }

    @McpTool(name = "getAgentStockValue",
            description = "Agentdagi barcha mahsulotlarning umumiy qiymatini hisoblaydi")
    public StockValueDto getAgentStockValue(
            @McpToolParam(description = "Agent ID si") Long agentId
    ) {
        return new StockValueDto(agentId, stockRepository.calculateAgentStockValue(agentId));
    }

    private StockTransferDto balances(Long productId, Long agentId, int quantity) {
        return new StockTransferDto(productId, agentId, quantity,
                quantityOf(stockRepository.getWarehouseStock(productId)),
                quantityOf(stockRepository.getAgentStock(productId, agentId)));
    }

    private static int quantityOf(StockRecord stock) {
        return stock == null ? 0 : stock.getQuantity();
    }

    private static int requirePositive(Integer quantity) {
        Require.that(quantity != null && quantity > 0, "Miqdor 0 dan katta bo'lishi kerak.");
        return quantity;
    }
}
