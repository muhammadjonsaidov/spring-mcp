package io.salesdoctor.spring_mcp.dto;

/**
 * Ombor va agent mashinasi orasidagi harakat natijasi (harakatdan keyingi qoldiqlar bilan).
 */
public record StockTransferDto(Long productId, Long agentId, int quantity,
                               int warehouseQuantity, int agentQuantity) {
}
