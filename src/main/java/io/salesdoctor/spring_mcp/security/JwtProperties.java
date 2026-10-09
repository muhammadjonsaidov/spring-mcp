package io.salesdoctor.spring_mcp.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * salesdoctor.security.jwt.* sozlamalari.
 *
 * @param secret          HS256 kaliti, kamida 32 bayt (prod'da faqat env orqali: SALESDOCTOR_JWT_SECRET)
 * @param issuer          token "iss" qiymati; boshqa issuer'li tokenlar rad etiladi
 * @param defaultValidity CLI token muddati (default)
 */
@ConfigurationProperties("salesdoctor.security.jwt")
public record JwtProperties(String secret, String issuer, Duration defaultValidity) {

    public static final String DEFAULT_ISSUER = "salesdoctor-mcp";
    public static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (issuer == null || issuer.isBlank()) issuer = DEFAULT_ISSUER;
        if (defaultValidity == null) defaultValidity = Duration.ofDays(30);
    }
}
