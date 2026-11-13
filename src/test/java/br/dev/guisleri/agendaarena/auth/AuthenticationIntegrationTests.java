package br.dev.guisleri.agendaarena.auth;

import br.dev.guisleri.agendaarena.user.entity.User;
import br.dev.guisleri.agendaarena.user.model.UserRole;
import br.dev.guisleri.agendaarena.user.service.UserService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

/**
 * Login e validação do token. Não usa o MockMvc autoconfigurado (que já vem autenticado como
 * PLATFORM_ADMIN): monta o próprio, com a cadeia de segurança real e sem usuário padrão.
 */
@SpringBootTest
@Testcontainers
class AuthenticationIntegrationTests {

    private static final String PASSWORD = "password-123";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserService userService;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${app.security.jwt.secret}")
    private String jwtSecret;

    private MockMvc mockMvc;
    private User organizer;

    @DynamicPropertySource
    static void configurePostgreSQL(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE establishments, users RESTART IDENTITY CASCADE");

        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        organizer = userService.createUser(
                "Maria da Silva",
                "maria@example.com",
                "11999991234",
                PASSWORD
        );
    }

    @Test
    void shouldLoginAndIssueTokenWithSubjectAndRole() throws Exception {
        String response = login("maria@example.com", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String accessToken = JsonPath.read(response, "$.accessToken");
        Jwt jwt = jwtDecoder.decode(accessToken);

        assertThat(jwt.getSubject()).isEqualTo(String.valueOf(organizer.getId()));
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ORGANIZER");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("agenda-arena-api");
        assertThat(jwt.getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void shouldLoginIgnoringEmailCaseAndSpaces() throws Exception {
        login("  MARIA@Example.com ", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void shouldRejectWrongPasswordWithGenericMessage() throws Exception {
        login("maria@example.com", "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void shouldRejectUnknownEmailWithTheSameMessage() throws Exception {
        login("nobody@example.com", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void shouldRejectInactiveUser() throws Exception {
        jdbcTemplate.update("UPDATE users SET active = false WHERE id = ?", organizer.getId());

        login("maria@example.com", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void shouldRejectPasswordLongerThan72Bytes() throws Exception {
        login("maria@example.com", "a".repeat(73))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void shouldRejectBlankCredentialsWithValidationErrors() throws Exception {
        login("", "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").isNotEmpty())
                .andExpect(jsonPath("$.errors.password").isNotEmpty());
    }

    @Test
    void shouldRequireTokenOnProtectedRoute() throws Exception {
        mockMvc.perform(get("/me/establishments"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("WWW-Authenticate"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void shouldAcceptTokenReturnedByLogin() throws Exception {
        mockMvc.perform(get("/me/establishments")
                        .header("Authorization", "Bearer " + loginToken()))
                // Organizador autenticado, mas esta rota é de administradores.
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    void shouldRejectMalformedToken() throws Exception {
        mockMvc.perform(get("/me/establishments")
                        .header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void shouldRejectTokenSignedWithAnotherSecret() throws Exception {
        String forged = encode(
                "another-secret-key-with-at-least-32-bytes-000000",
                Instant.now().minusSeconds(10),
                Instant.now().plusSeconds(3600)
        );

        mockMvc.perform(get("/me/establishments")
                        .header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectExpiredToken() throws Exception {
        String expired = encode(
                jwtSecret,
                Instant.now().minusSeconds(7200),
                Instant.now().minusSeconds(3600)
        );

        mockMvc.perform(get("/me/establishments")
                        .header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectTokenWithModifiedPayload() throws Exception {
        String[] parts = loginToken().split("\\.");
        String tamperedPayload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"1\",\"roles\":[\"PLATFORM_ADMIN\"],\"iss\":\"agenda-arena-api\"}"
                        .getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(get("/me/establishments")
                        .header(
                                "Authorization",
                                "Bearer " + parts[0] + "." + tamperedPayload + "." + parts[2]
                        ))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldKeepPublicRoutesOpenWithoutToken() throws Exception {
        mockMvc.perform(get("/establishments"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Joao",
                                  "email": "joao@example.com",
                                  "phone": "11999991234",
                                  "password": "password-123"
                                }
                                """))
                .andExpect(status().isCreated())
                // Cadastro público nunca cria administradores.
                .andExpect(jsonPath("$.id").isNumber());

        String role = jdbcTemplate.queryForObject(
                "SELECT role FROM users WHERE email = 'joao@example.com'",
                String.class
        );

        assertThat(role).isEqualTo(UserRole.ORGANIZER.name());
    }

    @Test
    void shouldDenyUnmappedRoutesByDefault() throws Exception {
        mockMvc.perform(get("/something-new"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/something-new")
                        .header("Authorization", "Bearer " + loginToken()))
                .andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.ResultActions login(
            String email,
            String password
    ) throws Exception {
        return mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, password)));
    }

    private String loginToken() throws Exception {
        String response = login("maria@example.com", PASSWORD)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return JsonPath.read(response, "$.accessToken");
    }

    private String encode(String secret, Instant issuedAt, Instant expiresAt) {
        SecretKeySpec key = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        );
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("agenda-arena-api")
                .subject(String.valueOf(organizer.getId()))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("roles", List.of("ORGANIZER"))
                .build();

        return encoder
                .encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(),
                        claims
                ))
                .getTokenValue();
    }

}
