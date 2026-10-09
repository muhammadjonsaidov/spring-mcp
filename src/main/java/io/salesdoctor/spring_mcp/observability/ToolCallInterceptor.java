package io.salesdoctor.spring_mcp.observability;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.salesdoctor.spring_mcp.error.ErrorCode;
import io.salesdoctor.spring_mcp.error.ToolErrorDto;
import io.salesdoctor.spring_mcp.error.ToolException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.ai.util.JsonHelper;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * Har bir MCP tool chaqiruvini o'rab oladi:
 * <ul>
 *     <li>kim (JWT subject), qaysi tool, qancha vaqt va qanday natija bilan chaqirganini logga yozadi
 *     (MDC: traceId, tool, caller - JSON loglarda alohida maydon bo'lib chiqadi);</li>
 *     <li>xatolarni yagona {@link ToolErrorDto} formatiga keltiradi.</li>
 * </ul>
 * Spring AI tool'larni "toolSpecs" nomli List&lt;SyncToolSpecification&gt; bean sifatida beradi;
 * shu ro'yxatdagi har bir callHandler almashtiriladi. Tool bean'larining o'zi proxy qilinmaydi
 * (aks holda annotatsiya skaneri @McpTool metodlarini topa olmaydi).
 */
@Component
public class ToolCallInterceptor implements BeanPostProcessor, EnvironmentAware {

    private static final Logger log = LoggerFactory.getLogger("salesdoctor.mcp.calls");
    private static final JsonHelper JSON = new JsonHelper();
    private static final String INTERNAL_MESSAGE = "Kutilmagan server xatoligi. traceId bo'yicha server logini tekshiring.";

    private boolean exposeInternalErrors;

    @Override
    public void setEnvironment(Environment environment) {
        this.exposeInternalErrors = environment.getProperty(
                "salesdoctor.errors.expose-internal-details", Boolean.class, false);
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof List<?> list && !list.isEmpty()
                && list.stream().allMatch(SyncToolSpecification.class::isInstance)) {
            return list.stream()
                    .map(SyncToolSpecification.class::cast)
                    .map(this::wrap)
                    .toList();
        }
        return bean;
    }

    private SyncToolSpecification wrap(SyncToolSpecification spec) {
        String toolName = spec.tool().name();
        BiFunction<McpSyncServerExchange, CallToolRequest, CallToolResult> delegate = spec.callHandler();

        return new SyncToolSpecification(spec.tool(), (exchange, request) -> {
            String traceId = UUID.randomUUID().toString().substring(0, 8);
            String caller = CallerContext.caller(exchange);
            long start = System.nanoTime();

            MDC.put("traceId", traceId);
            MDC.put("tool", toolName);
            MDC.put("caller", caller);
            try {
                CallToolResult result;
                try {
                    result = delegate.apply(exchange, request);
                } catch (RuntimeException e) {
                    // McpError kabi protokol xatolari ham shu yerga tushadi
                    log.error("MCP tool {} kutilmagan istisno bilan tugadi", toolName, e);
                    result = CallToolResult.builder().isError(true).addTextContent(e.toString()).build();
                }

                if (Boolean.TRUE.equals(result.isError())) {
                    result = toErrorResult(result, toolName, traceId);
                }
                logCall(toolName, caller, start, result);
                return result;
            } finally {
                MDC.remove("traceId");
                MDC.remove("tool");
                MDC.remove("caller");
            }
        });
    }

    /**
     * Spring AI xatoni "xabar\nsababi" ko'rinishidagi matn qilib qaytaradi;
     * ToolException uchun ikkalasi bir xil bo'ladi, shuning uchun birinchi qismi olinadi.
     */
    private CallToolResult toErrorResult(CallToolResult original, String toolName, String traceId) {
        String text = textOf(original);
        String message = firstHalfIfDuplicated(text);

        ToolException decoded = ToolException.decode(message);
        ToolErrorDto error;
        if (decoded != null) {
            error = ToolErrorDto.of(decoded.code(), decoded.detail(), toolName, traceId);
        } else {
            log.error("MCP tool {} ichki xatolik: {}", toolName, text);
            error = ToolErrorDto.of(ErrorCode.INTERNAL_ERROR,
                    exposeInternalErrors ? message : INTERNAL_MESSAGE, toolName, traceId);
        }

        return CallToolResult.builder()
                .isError(true)
                .addTextContent(JSON.toJson(error))
                .build();
    }

    private static void logCall(String toolName, String caller, long start, CallToolResult result) {
        long durationMs = (System.nanoTime() - start) / 1_000_000;
        boolean error = Boolean.TRUE.equals(result.isError());
        String errorCode = error ? errorCodeOf(result) : null;

        // tool, caller va traceId MDC'da: key-value sifatida takrorlansa structured (ECS) logda
        // "Duplicate nested pairs" xatosi bilan yozuv yo'qoladi
        var event = log.atInfo()
                .addKeyValue("event", "mcp.tool.call")
                .addKeyValue("durationMs", durationMs)
                .addKeyValue("outcome", error ? "error" : "success");
        if (errorCode != null) {
            event = event.addKeyValue("errorCode", errorCode);
        }
        event.log("MCP tool {} caller={} {} {}ms{}", toolName, caller,
                        error ? "ERROR" : "OK", durationMs, error ? " code=" + errorCode : "");
    }

    private static String errorCodeOf(CallToolResult result) {
        String text = textOf(result);
        for (ErrorCode code : ErrorCode.values()) {
            if (text.contains("\"code\":\"" + code.name() + "\"")) return code.name();
        }
        return ErrorCode.INTERNAL_ERROR.name();
    }

    private static String textOf(CallToolResult result) {
        if (result.content() == null) return "";
        return result.content().stream()
                .filter(TextContent.class::isInstance)
                .map(c -> ((TextContent) c).text())
                .collect(Collectors.joining("\n"));
    }

    static String firstHalfIfDuplicated(String text) {
        String separator = System.lineSeparator();
        int half = (text.length() - separator.length()) / 2;
        if (half > 0 && text.length() == half * 2 + separator.length()) {
            String first = text.substring(0, half);
            if (text.equals(first + separator + first)) return first;
        }
        return text;
    }
}
