package io.salesdoctor.spring_mcp.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * HS256 tokenlarni yaratish va tekshirish. Spring konteksti tashqarisida (TokenCli) ham ishlaydi.
 */
public class JwtTokenService {

    /** Serverga ulanish uchun talab qilinadigan scope (Spring'da SCOPE_mcp authority) */
    public static final String MCP_SCOPE = "mcp";

    private final SecretKey key;
    private final String issuer;

    public JwtTokenService(String secret, String issuer) {
        this.key = toKey(secret);
        this.issuer = issuer;
    }

    public String issue(String subject, Duration validity) {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Token egasi (subject) ko'rsatilishi kerak.");
        }
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plus(validity))
                .id(UUID.randomUUID().toString())
                .claim("scope", MCP_SCOPE)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return new NimbusJwtEncoder(new ImmutableSecret<>(key))
                .encode(JwtEncoderParameters.from(header, claims))
                .getTokenValue();
    }

    /**
     * Imzo, muddat (exp/nbf) va issuer'ni tekshiradigan decoder.
     */
    public JwtDecoder decoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(), new JwtIssuerValidator(issuer)));
        return decoder;
    }

    private static SecretKey toKey(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT kaliti berilmagan: SALESDOCTOR_JWT_SECRET muhit o'zgaruvchisini o'rnating.");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < JwtProperties.MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT kaliti juda qisqa: kamida " + JwtProperties.MIN_SECRET_BYTES + " bayt bo'lishi kerak.");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}
