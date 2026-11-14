package br.dev.guisleri.agendaarena.common.ratelimit;

import br.dev.guisleri.agendaarena.auth.service.JwtTokenService;
import br.dev.guisleri.agendaarena.support.MutableClock;
import br.dev.guisleri.agendaarena.user.entity.User;
import br.dev.guisleri.agendaarena.user.model.UserRole;
import br.dev.guisleri.agendaarena.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Limite de requisições com a cadeia de segurança real. O relógio é controlado pelo teste e cada
 * teste usa IPs, e-mails e links próprios (os baldes ficam na memória do contexto).
 */
@SpringBootTest(properties = "app.rate-limit.enabled=true")
@Testcontainers
@Import(RateLimitIntegrationTests.TestClockConfiguration.class)
class RateLimitIntegrationTests {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private MutableClock clock;
    @Autowired
    private UserService userService;
    @Autowired
    private JwtTokenService jwtTokenService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;

    @DynamicPropertySource
    static void configurePostgreSQL(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        clock.set(Instant.now());
    }

    // ---------- Login ----------

    @Test
    @DisplayName("Login: 10 por minuto por IP; a ficha volta a cada 6 segundos")
    void loginIsLimitedPerIp() throws Exception {
        String ip = newIp();

        for (int i = 1; i <= 10; i++) {
            login(ip, "user" + unique() + "@example.com").andExpect(status().isUnauthorized());
        }

        login(ip, "user" + unique() + "@example.com")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "6"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.message").value("Too many requests. Try again in 6 seconds"));

        clock.advance(Duration.ofSeconds(5));
        login(ip, "user" + unique() + "@example.com").andExpect(status().isTooManyRequests());

        clock.advance(Duration.ofSeconds(1));
        login(ip, "user" + unique() + "@example.com").andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Login por conta: 5 a cada 5 minutos por IP + e-mail, ignorando caixa e espaços")
    void loginIsLimitedPerAccount() throws Exception {
        String ip = newIp();
        String email = "victim" + unique() + "@example.com";

        for (int i = 0; i < 5; i++) {
            login(ip, i % 2 == 0 ? email : "  " + email.toUpperCase() + " ")
                    .andExpect(status().isUnauthorized());
        }

        login(ip, email)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"));

        // Outro e-mail no mesmo IP e o mesmo e-mail em outro IP não são afetados.
        login(ip, "other" + unique() + "@example.com").andExpect(status().isUnauthorized());
        login(newIp(), email).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("IPs diferentes têm limites independentes")
    void ipsAreIndependent() throws Exception {
        String exhausted = newIp();

        for (int i = 0; i < 10; i++) {
            login(exhausted, "user" + unique() + "@example.com");
        }

        login(exhausted, "user" + unique() + "@example.com")
                .andExpect(status().isTooManyRequests());
        login(newIp(), "user" + unique() + "@example.com").andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("O X-Forwarded-For é ignorado por padrão: não dá para fugir do limite")
    void forwardedHeaderIsIgnoredByDefault() throws Exception {
        String ip = newIp();

        for (int i = 0; i < 10; i++) {
            login(ip, "user" + unique() + "@example.com", "198.51.100." + i)
                    .andExpect(status().isUnauthorized());
        }

        login(ip, "user" + unique() + "@example.com", "198.51.100.200")
                .andExpect(status().isTooManyRequests());
    }

    // ---------- Refresh, logout e cadastro ----------

    @Test
    @DisplayName("Refresh e logout dividem o limite de 30 por minuto por IP")
    void refreshAndLogoutShareTheLimit() throws Exception {
        String ip = newIp();

        for (int i = 0; i < 15; i++) {
            postJson("/auth/refresh", "{\"refreshToken\": \"" + "a".repeat(43) + "\"}", ip)
                    .andExpect(status().isUnauthorized());
            postJson("/auth/logout", "{\"refreshToken\": \"" + "a".repeat(43) + "\"}", ip)
                    .andExpect(status().isNoContent());
        }

        postJson("/auth/refresh", "{\"refreshToken\": \"" + "a".repeat(43) + "\"}", ip)
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("Cadastro: 10 por hora por IP")
    void signupIsLimitedPerIp() throws Exception {
        String ip = newIp();

        for (int i = 0; i < 10; i++) {
            postJson("/users", "{}", ip).andExpect(status().isBadRequest());
        }

        postJson("/users", "{}", ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "360"));

        clock.advance(Duration.ofSeconds(360));
        postJson("/users", "{}", ip).andExpect(status().isBadRequest());
    }

    // ---------- Link público da partida ----------

    @Test
    @DisplayName("Página da partida e resumo: 60 por minuto por IP, juntos")
    void matchViewIsLimitedPerIp() throws Exception {
        String ip = newIp();
        String token = "v" + unique();

        for (int i = 0; i < 30; i++) {
            getFrom("/matches/" + token, ip).andExpect(status().isNotFound());
            getFrom("/matches/" + token + "/summary", ip).andExpect(status().isNotFound());
        }

        getFrom("/matches/" + token, ip).andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("Inscrição: 10 por minuto por IP")
    void matchJoinIsLimitedPerIp() throws Exception {
        String ip = newIp();
        String token = "j" + unique();

        for (int i = 0; i < 10; i++) {
            join(token, ip).andExpect(status().isNotFound());
        }

        join(token, ip).andExpect(status().isTooManyRequests());
        // Outro link, mesmo IP: o limite por IP vale para todos.
        join("j" + unique(), ip).andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("Inscrição: 60 por hora por link, mesmo vindo de IPs diferentes")
    void matchJoinIsLimitedPerLink() throws Exception {
        String token = "l" + unique();

        for (int ipIndex = 0; ipIndex < 6; ipIndex++) {
            String ip = newIp();

            for (int i = 0; i < 10; i++) {
                join(token, ip).andExpect(status().isNotFound());
            }
        }

        join(token, newIp()).andExpect(status().isTooManyRequests());
        // Outro link a partir de um IP novo continua livre.
        join("l" + unique(), newIp()).andExpect(status().isNotFound());
    }

    // ---------- Catálogo, regras independentes e demais rotas ----------

    @Test
    @DisplayName("Catálogo público: 120 por minuto por IP")
    void catalogIsLimitedPerIp() throws Exception {
        String ip = newIp();

        for (int i = 0; i < 120; i++) {
            getFrom("/establishments", ip).andExpect(status().isOk());
        }

        getFrom("/establishments", ip).andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("Esgotar uma regra não bloqueia as outras do mesmo IP")
    void rulesAreIndependent() throws Exception {
        String ip = newIp();

        for (int i = 0; i < 10; i++) {
            login(ip, "user" + unique() + "@example.com");
        }

        login(ip, "user" + unique() + "@example.com").andExpect(status().isTooManyRequests());
        getFrom("/establishments", ip).andExpect(status().isOk());
    }

    @Test
    @DisplayName("O 429 também leva os cabeçalhos CORS (o front consegue ler o erro)")
    void tooManyRequestsCarriesCorsHeaders() throws Exception {
        String ip = newIp();

        for (int i = 0; i < 10; i++) {
            login(ip, "user" + unique() + "@example.com");
        }

        mockMvc.perform(withRemote(post("/auth/login")
                        .header("Origin", "http://localhost:4200")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("user" + unique() + "@example.com")), ip))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
    }

    @Test
    @DisplayName("Rotas autenticadas não são limitadas (dívida registrada)")
    void authenticatedRoutesAreNotLimited() throws Exception {
        User organizer = userService.createUser(
                "Maria da Silva", "maria" + unique() + "@example.com", "11999991234",
                "password-123", UserRole.ORGANIZER);
        String bearer = "Bearer " + jwtTokenService.issue(organizer).accessToken();
        String ip = newIp();

        for (int i = 0; i < 150; i++) {
            mockMvc.perform(withRemote(get("/me/reservations")
                            .header("Authorization", bearer), ip))
                    .andExpect(status().isOk());
        }
    }

    // ---------- Apoio ----------

    private static String newIp() {
        return "10.20." + (SEQUENCE.incrementAndGet() / 250) + "." + (SEQUENCE.get() % 250 + 1);
    }

    private static int unique() {
        return SEQUENCE.incrementAndGet();
    }

    private static String loginBody(String email) {
        return "{\"email\": \"" + email + "\", \"password\": \"wrong-password\"}";
    }

    private ResultActions login(String ip, String email) throws Exception {
        return postJson("/auth/login", loginBody(email), ip);
    }

    private ResultActions login(String ip, String email, String forwardedFor) throws Exception {
        return mockMvc.perform(withRemote(post("/auth/login")
                .header("X-Forwarded-For", forwardedFor)
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(email)), ip));
    }

    private ResultActions join(String token, String ip) throws Exception {
        return postJson("/matches/" + token + "/participants",
                "{\"name\": \"Joao\", \"position\": \"FIELD\"}", ip);
    }

    private ResultActions postJson(String path, String body, String ip) throws Exception {
        return mockMvc.perform(withRemote(post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body), ip));
    }

    private ResultActions getFrom(String path, String ip) throws Exception {
        return mockMvc.perform(withRemote(get(path), ip));
    }

    private static MockHttpServletRequestBuilder withRemote(
            MockHttpServletRequestBuilder builder,
            String ip
    ) {
        return builder.with(request -> {
            request.setRemoteAddr(ip);
            return request;
        });
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestClockConfiguration {

        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(Instant.now(), ZoneId.of("America/Sao_Paulo"));
        }
    }
}
