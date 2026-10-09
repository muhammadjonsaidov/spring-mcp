package io.salesdoctor.spring_mcp;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import io.salesdoctor.spring_mcp.security.JwtProperties;
import io.salesdoctor.spring_mcp.security.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class SecurityIntegrationTest {

    private static final String INITIALIZE = """
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{
              "protocolVersion":"2025-06-18","capabilities":{},
              "clientInfo":{"name":"test","version":"1.0"}}}
            """;

    @Autowired MockMvc mvc;
    @Autowired JwtTokenService tokens;
    @Autowired JwtProperties jwtProperties;

    // --- Health / info: ochiq ---

    @Test
    void healthIsPublicAndReportsDatabase() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.db.status").value("UP"));

        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
        mvc.perform(get("/actuator/info")).andExpect(status().isOk());
    }

    // --- /mcp: faqat yaroqli JWT bilan ---

    @Test
    void mcpRequiresToken() throws Exception {
        mcp(null).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() throws Exception {
        String forged = new JwtTokenService("another-secret-another-secret-0123456789", "salesdoctor-mcp")
                .issue("attacker", Duration.ofHours(1));
        mcp(forged).andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        Instant past = Instant.now().minus(Duration.ofHours(2));
        String expired = sign(JwtClaimsSet.builder()
                .issuer("salesdoctor-mcp").subject("claude-code").claim("scope", "mcp")
                .issuedAt(past).expiresAt(past.plus(Duration.ofHours(1)))
                .build());

        mcp(expired).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenWithoutMcpScopeIsForbidden() throws Exception {
        Instant now = Instant.now();
        String noScope = sign(JwtClaimsSet.builder()
                .issuer("salesdoctor-mcp").subject("no-scope")
                .issuedAt(now).expiresAt(now.plusSeconds(600))
                .build());

        mcp(noScope).andExpect(status().isForbidden());
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() throws Exception {
        mcp(new JwtTokenService(jwtProperties.secret(), "someone-else").issue("claude-code", Duration.ofHours(1)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validTokenReachesMcpAndCallerIsLogged(CapturedOutput output) throws Exception {
        String token = tokens.issue("claude-code", Duration.ofHours(1));

        MvcResult init = mcp(token).andExpect(status().isOk()).andReturn();
        String sessionId = init.getResponse().getHeader("Mcp-Session-Id");
        assertThat(bodyOf(init)).contains("salesdoctor-mcp");
        assertThat(sessionId).isNotBlank();

        mvc.perform(post("/mcp")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("Mcp-Session-Id", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                        .content("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"))
                .andExpect(status().isAccepted());

        MvcResult call = mvc.perform(post("/mcp")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("Mcp-Session-Id", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                        .content("""
                                {"jsonrpc":"2.0","id":2,"method":"tools/call",
                                 "params":{"name":"getOrder","arguments":{"orderId":999999}}}
                                """))
                .andReturn();

        assertThat(bodyOf(call)).contains("NOT_FOUND");
        assertThat(output).contains("MCP tool getOrder caller=claude-code ERROR");
    }

    /** Server ishlatayotgan kalit bilan (dev default yoki .env dagi) imzolaydi */
    private String sign(JwtClaimsSet claims) {
        var key = new SecretKeySpec(jwtProperties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return new NimbusJwtEncoder(new ImmutableSecret<>(key))
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private ResultActions mcp(String token) throws Exception {
        var request = post("/mcp")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .content(INITIALIZE);
        if (token != null) request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return mvc.perform(request);
    }

    /**
     * Javob oqim (SSE) bo'lib kelsa, asinxron dispatch tugashini kutadi.
     */
    private String bodyOf(MvcResult result) throws Exception {
        if (result.getRequest().isAsyncStarted()) {
            result.getAsyncResult(Duration.ofSeconds(10).toMillis());
            return mvc.perform(asyncDispatch(result)).andReturn().getResponse().getContentAsString();
        }
        return result.getResponse().getContentAsString();
    }
}
