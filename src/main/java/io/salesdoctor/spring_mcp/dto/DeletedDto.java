package io.salesdoctor.spring_mcp.dto;

/**
 * O'chirish (yoki soft delete) natijasi.
 */
public record DeletedDto(String entity, Long id) {
}
