package io.salesdoctor.spring_mcp.dto;

import java.util.List;

public record OrderDetailsDto(OrderDto order, List<OrderItemDto> items) {
}
