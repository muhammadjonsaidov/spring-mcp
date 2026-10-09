package io.salesdoctor.spring_mcp.error;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tool'lardan tashlanadigan yagona istisno. Spring AI xatoni faqat matn sifatida uzatadi,
 * shuning uchun kod xabar boshida "[KOD] " ko'rinishida yoziladi va ToolCallInterceptor
 * uni yagona JSON formatiga aylantiradi.
 */
public class ToolException extends RuntimeException {

    private static final Pattern ENCODED = Pattern.compile("^\\[([A-Z_]+)] (.*)$", Pattern.DOTALL);

    private final ErrorCode code;
    private final String detail;

    public ToolException(ErrorCode code, String detail) {
        super("[" + code + "] " + detail);
        this.code = code;
        this.detail = detail;
    }

    public static ToolException invalid(String detail) {
        return new ToolException(ErrorCode.INVALID_ARGUMENT, detail);
    }

    public static ToolException notFound(String detail) {
        return new ToolException(ErrorCode.NOT_FOUND, detail);
    }

    public static ToolException conflict(String detail) {
        return new ToolException(ErrorCode.CONFLICT, detail);
    }

    public ErrorCode code() {
        return code;
    }

    public String detail() {
        return detail;
    }

    /**
     * getMessage() ko'rinishidagi matndan kod va xabarni ajratadi; mos kelmasa null.
     */
    public static ToolException decode(String message) {
        if (message == null) return null;
        Matcher m = ENCODED.matcher(message);
        if (!m.matches()) return null;
        try {
            return new ToolException(ErrorCode.valueOf(m.group(1)), m.group(2));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
