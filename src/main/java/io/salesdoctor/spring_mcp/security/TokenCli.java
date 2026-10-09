package io.salesdoctor.spring_mcp.security;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;

/**
 * MCP mijozlari uchun JWT yaratadi (Spring kontekstini ko'tarmaydi, bazaga ulanmaydi).
 * <pre>
 * ./mvnw -q compile exec:java -Dexec.args="--subject claude-code --days 30"
 * </pre>
 * Kalit server bilan bir xil bo'lishi kerak. Qidirish tartibi: SALESDOCTOR_JWT_SECRET muhit
 * o'zgaruvchisi, joriy papkadagi .env fayli, so'ng --dev bilan serverning dev kaliti.
 */
public final class TokenCli {

    /** application-dev.yaml dagi kalit bilan bir xil - faqat lokal ishlab chiqish uchun */
    static final String DEV_SECRET = "dev-only-secret-change-me-0123456789abcdef";

    private TokenCli() {
    }

    public static void main(String[] args) {
        String subject = null;
        long days = 30;
        boolean dev = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--subject" -> subject = args[++i];
                case "--days" -> days = Long.parseLong(args[++i]);
                case "--dev" -> dev = true;
                default -> fail("Noma'lum parametr: " + args[i]);
            }
        }
        if (subject == null) fail("--subject majburiy (masalan --subject claude-code)");

        Properties dotEnv = readDotEnv(Path.of(".env"));
        String secret = setting("SALESDOCTOR_JWT_SECRET", dotEnv);
        if (secret == null) {
            if (!dev) fail("SALESDOCTOR_JWT_SECRET topilmadi (muhit o'zgaruvchisi yoki .env); "
                    + "kalitsiz lokal dev server uchun --dev qo'shing");
            secret = DEV_SECRET;
        }
        String issuer = setting("SALESDOCTOR_JWT_ISSUER", dotEnv);
        if (issuer == null) issuer = JwtProperties.DEFAULT_ISSUER;

        System.out.println(new JwtTokenService(secret, issuer).issue(subject, Duration.ofDays(days)));
    }

    private static String setting(String key, Properties dotEnv) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) value = dotEnv.getProperty(key);
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * Spring'dagi "optional:file:.env[.properties]" bilan bir xil: KEY=value, # izohlar.
     */
    private static Properties readDotEnv(Path path) {
        Properties properties = new Properties();
        if (Files.isRegularFile(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                properties.load(reader);
            } catch (IOException e) {
                fail(".env o'qib bo'lmadi: " + e.getMessage());
            }
        }
        return properties;
    }

    private static void fail(String message) {
        System.err.println(message);
        System.err.println("Foydalanish: --subject <nom> [--days 30] [--dev]");
        System.exit(1);
    }
}
