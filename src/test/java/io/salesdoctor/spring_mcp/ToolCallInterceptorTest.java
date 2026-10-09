package io.salesdoctor.spring_mcp;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring AI ro'yxatdan o'tkazgan haqiqiy tool handler'lari orqali (MCP serverga keladigan yo'l bilan bir xil):
 * yagona xato formati va chaqiruv logi.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class ToolCallInterceptorTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    @Qualifier("toolSpecs")
    List<SyncToolSpecification> toolSpecs;

    @Test
    void allToolsAreRegisteredAndWrapped() {
        assertThat(toolSpecs).hasSize(85);
    }

    @Test
    void notFoundUsesUnifiedFormat(CapturedOutput output) {
        CallToolResult result = call("getOrder", Map.of("orderId", 999_999));

        JsonNode error = errorOf(result);
        assertThat(error.get("code").asString()).isEqualTo("NOT_FOUND");
        assertThat(error.get("message").asString()).isEqualTo("Buyurtma topilmadi: ID=999999");
        assertThat(error.get("tool").asString()).isEqualTo("getOrder");
        assertThat(error.get("traceId").asString()).hasSize(8);

        assertThat(output).contains("MCP tool getOrder caller=anonymous ERROR")
                .contains("code=NOT_FOUND");
    }

    @Test
    void validationAndBusinessErrorsHaveTheirOwnCodes() {
        assertThat(errorOf(call("createOrder", Map.of("customerId", 1, "agentId", 1, "itemsCsv", "1:-5")))
                .get("code").asString()).isEqualTo("INVALID_ARGUMENT");

        assertThat(errorOf(call("transferStockToAgent", Map.of("productId", 1, "agentId", 1, "quantity", 1_000_000)))
                .get("code").asString()).isEqualTo("CONFLICT");
    }

    @Test
    void unexpectedErrorsBecomeInternalError() {
        // Long o'rniga matn: Spring AI argumentni o'girishda yiqiladi, ToolException emas
        JsonNode error = errorOf(call("getOrder", Map.of("orderId", "abc")));
        assertThat(error.get("code").asString()).isEqualTo("INTERNAL_ERROR");
    }

    @Test
    void successfulCallReturnsDtoJson(CapturedOutput output) {
        CallToolResult result = call("listTerritories", Map.of());

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        JsonNode territories = JSON.readTree(text(result));
        assertThat(territories.isArray()).isTrue();
        assertThat(territories.toString()).contains("\"name\":\"Chilonzor tumani\"");
        assertThat(output).contains("MCP tool listTerritories caller=anonymous OK");
    }

    private CallToolResult call(String tool, Map<String, Object> arguments) {
        SyncToolSpecification spec = toolSpecs.stream()
                .filter(s -> s.tool().name().equals(tool))
                .findFirst()
                .orElseThrow();
        return spec.callHandler().apply(null, new CallToolRequest(tool, arguments, null));
    }

    private static JsonNode errorOf(CallToolResult result) {
        assertThat(result.isError()).isTrue();
        return JSON.readTree(text(result)).get("error");
    }

    private static String text(CallToolResult result) {
        assertThat(result.content()).singleElement().isInstanceOf(TextContent.class);
        return ((TextContent) result.content().getFirst()).text();
    }
}
