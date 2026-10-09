package io.salesdoctor.spring_mcp.error;

/**
 * Barcha tool xatolarining yagona formati: {"error": {"code", "message", "tool", "traceId"}}.
 * traceId server logidagi shu chaqiruv yozuvini topish uchun.
 */
public record ToolErrorDto(Error error) {

    public record Error(ErrorCode code, String message, String tool, String traceId) {
    }

    public static ToolErrorDto of(ErrorCode code, String message, String tool, String traceId) {
        return new ToolErrorDto(new Error(code, message, tool, traceId));
    }
}
