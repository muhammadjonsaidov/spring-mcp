package io.salesdoctor.spring_mcp.dto;

import io.salesdoctor.spring_mcp.jooq.tables.records.StockRecord;

/**
 * Ombor qatori: asosiy omborda agentId = null, agent mashinasida location = "VAN-{id}".
 */
public record StockDto(Long productId, Long agentId, String location, int quantity) {

    public static StockDto from(StockRecord r) {
        return new StockDto(r.getProductId(), r.getAgentId(), r.getWarehouseLocation(), r.getQuantity());
    }
}
