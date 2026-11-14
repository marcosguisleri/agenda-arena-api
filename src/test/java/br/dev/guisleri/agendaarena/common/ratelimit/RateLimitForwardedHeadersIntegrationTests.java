package br.dev.guisleri.agendaarena.common.ratelimit;

import br.dev.guisleri.agendaarena.support.MutableClock;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Com {@code trust-forwarded-headers=true} (API atrás do proxy), o IP do cliente é a última
 * entrada do {@code X-Forwarded-For}, a que o proxy acrescenta.
 */
@SpringBootTest(properties = {
        "app.rate-limit.enabled=true",
        "app.rate-limit.trust-forwarded-headers=true"
})
@Testcontainers
@Import(RateLimitForwardedHeadersIntegrationTests.TestClockConfiguration.class)
class RateLimitForwardedHeadersIntegrationTests {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private WebApplicationContext webApplicationContext;

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
    }

    @Test
    @DisplayName("Clientes diferentes atrás do mesmo proxy têm limites independentes")
    void eachClientBehindTheProxyHasItsOwnBucket() throws Exception {
        // Todos chegam da conexão do proxy (mesmo remoteAddr), mas com IPs de cliente diferentes.
        for (int i = 0; i < 15; i++) {
            login("203.0.113." + i, "user" + SEQUENCE.incrementAndGet() + "@example.com")
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    @DisplayName("Prefixos forjados não mudam o balde: vale a última entrada, a do proxy")
    void forgedPrefixesDoNotEscapeTheLimit() throws Exception {
        String realClient = "198.51.100." + SEQUENCE.incrementAndGet();

        for (int i = 0; i < 10; i++) {
            login("1.1.1." + i + ", " + realClient, "user" + SEQUENCE.incrementAndGet()
                    + "@example.com")
                    .andExpect(status().isUnauthorized());
        }

        login("9.9.9.9, " + realClient, "user" + SEQUENCE.incrementAndGet() + "@example.com")
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("Sem o cabeçalho, usa o endereço da conexão")
    void fallsBackToTheConnectionAddress() throws Exception {
        String remote = "10.99." + SEQUENCE.incrementAndGet() + ".1";

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/auth/login")
                            .with(request -> {
                                request.setRemoteAddr(remote);
                                return request;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("user" + SEQUENCE.incrementAndGet() + "@example.com")))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/auth/login")
                        .with(request -> {
                            request.setRemoteAddr(remote);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("user" + SEQUENCE.incrementAndGet() + "@example.com")))
                .andExpect(status().isTooManyRequests());
    }

    private ResultActions login(String forwardedFor, String email) throws Exception {
        return mockMvc.perform(post("/auth/login")
                .with(request -> {
                    request.setRemoteAddr("10.0.0.1");
                    return request;
                })
                .header("X-Forwarded-For", forwardedFor)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(email)));
    }

    private static String body(String email) {
        return "{\"email\": \"" + email + "\", \"password\": \"wrong-password\"}";
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
