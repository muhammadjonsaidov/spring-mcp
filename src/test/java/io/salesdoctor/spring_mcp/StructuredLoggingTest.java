package io.salesdoctor.spring_mcp;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * prod profilidagi JSON (ECS) log formati: tool chaqiruvi yozuvi yo'qolmasligi va maydonlari.
 */
@SpringBootTest(properties = "logging.structured.format.console=ecs")
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class StructuredLoggingTest {

    @Autowired
    @Qualifier("toolSpecs")
    List<SyncToolSpecification> toolSpecs;

    @Test
    void toolCallIsLoggedAsJsonWithContextFields(CapturedOutput output) {
        SyncToolSpecification getOrder = toolSpecs.stream()
                .filter(s -> s.tool().name().equals("getOrder"))
                .findFirst()
                .orElseThrow();
        getOrder.callHandler().apply(null, new CallToolRequest("getOrder", Map.of("orderId", 999_999), null));

        String line = output.getOut().lines()
                .filter(l -> l.contains("\"event\":\"mcp.tool.call\""))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Tool chaqiruvi logga yozilmadi:\n" + output));

        assertThat(line)
                .contains("\"tool\":\"getOrder\"")
                .contains("\"caller\":\"anonymous\"")
                .contains("\"outcome\":\"error\"")
                .contains("\"errorCode\":\"NOT_FOUND\"")
                .containsPattern("\"traceId\":\"[0-9a-f]{8}\"")
                .containsPattern("\"durationMs\":\\d+");
        assertThat(output).doesNotContain("failed to append");
    }
}
