package io.salesdoctor.spring_mcp.observability;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import org.springframework.web.servlet.function.ServerRequest;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

/**
 * HTTP so'rovdan chaqiruvchi ma'lumotini MCP transport context'iga o'tkazadi.
 * Tool'lar boshqa thread'da (boundedElastic) ishlaydi, shuning uchun SecurityContextHolder
 * u yerda bo'sh bo'ladi; ma'lumot exchange.transportContext() orqali yetib boradi.
 */
public final class CallerContext {

    public static final String CALLER = "caller";
    public static final String REMOTE_ADDRESS = "remoteAddress";
    public static final String ANONYMOUS = "anonymous";

    private CallerContext() {
    }

    /**
     * WebMvcStreamableServerTransportProvider.contextExtractor uchun (so'rov thread'ida ishlaydi).
     */
    public static McpTransportContext extract(ServerRequest request) {
        Map<String, Object> metadata = new HashMap<>();
        Principal principal = request.servletRequest().getUserPrincipal();
        metadata.put(CALLER, principal != null ? principal.getName() : ANONYMOUS);
        metadata.put(REMOTE_ADDRESS, request.servletRequest().getRemoteAddr());
        return McpTransportContext.create(metadata);
    }

    public static String caller(McpSyncServerExchange exchange) {
        if (exchange == null) return ANONYMOUS;
        McpTransportContext context = exchange.transportContext();
        Object caller = context == null ? null : context.get(CALLER);
        return caller == null ? ANONYMOUS : caller.toString();
    }
}
