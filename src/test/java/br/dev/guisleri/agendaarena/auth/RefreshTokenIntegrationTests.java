package br.dev.guisleri.agendaarena.auth;

import br.dev.guisleri.agendaarena.auth.exception.InvalidRefreshTokenException;
import br.dev.guisleri.agendaarena.auth.service.RefreshTokenService;
import br.dev.guisleri.agendaarena.user.entity.User;
import br.dev.guisleri.agendaarena.user.service.UserService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Refresh token: login, renovação com rotação, detecção de reuso, logout e expiração. Como o
 * {@code AuthenticationIntegrationTests}, monta o próprio MockMvc com a cadeia de segurança real
 * e não manda nenhum {@code Authorization}: as rotas de refresh e logout são públicas.
 */
@SpringBootTest
@Testcontainers
class RefreshTokenIntegrationTests {

    private static final String PASSWORD = "password-123";
    private static final String INVALID_MESSAGE = "Invalid refresh token";
    private static final Duration CONCURRENCY_TIMEOUT = Duration.ofSeconds(10);

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserService userService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;
    private ExecutorService executor;
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

        executor = Executors.newFixedThreadPool(2);

        organizer = userService.createUser(
                "Maria da Silva",
                "maria@example.com",
                "11999991234",
                PASSWORD
        );
    }

    @AfterEach
    void stopExecutor() throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    @Nested
    @DisplayName("Login")
    class LoginIssuesRefreshToken {

        @Test
        void shouldReturnRefreshTokenAndStoreOnlyItsHash() throws Exception {
            String response = login()
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                    .andExpect(jsonPath("$.refreshExpiresIn").value(30L * 24 * 60 * 60))
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            String refreshToken = JsonPath.read(response, "$.refreshToken");

            assertThat(refreshToken).hasSize(43);
            assertThat(sessionCount()).isEqualTo(1);
            assertThat(currentHash(organizer)).isEqualTo(sha256(refreshToken));
            assertThat(currentHash(organizer)).isNotEqualTo(refreshToken);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM refresh_sessions "
                            + "WHERE current_token_hash = ? OR previous_token_hash = ?",
                    Long.class, refreshToken, refreshToken
            )).isZero();
        }

        @Test
        void shouldGiveEachLoginItsOwnSession() throws Exception {
            String first = refreshTokenOf(login());
            String second = refreshTokenOf(login());

            assertThat(first).isNotEqualTo(second);
            assertThat(sessionCount()).isEqualTo(2);
        }

        @Test
        void shouldDeleteFinishedSessionsOfTheUserOnLogin() throws Exception {
            String expired = refreshTokenOf(login());
            String revoked = refreshTokenOf(login());
            String active = refreshTokenOf(login());

            jdbcTemplate.update(
                    "UPDATE refresh_sessions SET expires_at = now() - interval '1 second' "
                            + "WHERE current_token_hash = ?", sha256(expired));
            logout(revoked).andExpect(status().isNoContent());
            assertThat(sessionCount()).isEqualTo(3);

            login().andExpect(status().isOk());

            // Sobram a ativa e a nova.
            assertThat(sessionCount()).isEqualTo(2);
            refresh(active).andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("POST /auth/refresh")
    class Refresh {

        @Test
        void shouldIssueNewPairAndKeepSubjectAndRole() throws Exception {
            String first = refreshTokenOf(login());

            String response = refresh(first)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.expiresIn").value(3600))
                    .andExpect(jsonPath("$.refreshExpiresIn").value(30L * 24 * 60 * 60))
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            String accessToken = JsonPath.read(response, "$.accessToken");
            String second = JsonPath.read(response, "$.refreshToken");
            Jwt jwt = jwtDecoder.decode(accessToken);

            assertThat(second).isNotEqualTo(first);
            assertThat(jwt.getSubject()).isEqualTo(String.valueOf(organizer.getId()));
            assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ORGANIZER");
            assertThat(sessionCount()).isEqualTo(1);
            assertThat(currentHash(organizer)).isEqualTo(sha256(second));
        }

        @Test
        void shouldAllowRotatingManyTimes() throws Exception {
            String token = refreshTokenOf(login());

            for (int i = 0; i < 3; i++) {
                token = refreshTokenOf(refresh(token).andExpect(status().isOk()));
            }

            assertThat(sessionCount()).isEqualTo(1);
        }

        @Test
        void shouldRevokeTheWholeSessionWhenAnAlreadyRotatedTokenIsReused()
                throws Exception {
            String first = refreshTokenOf(login());
            String second = refreshTokenOf(refresh(first).andExpect(status().isOk()));

            refresh(first)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value(INVALID_MESSAGE));

            // O token novo também morreu: a sessão foi revogada e a revogação foi gravada.
            refresh(second)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value(INVALID_MESSAGE));
            assertThat(revokedCount()).isEqualTo(1);
        }

        @Test
        void shouldRejectATokenOlderThanThePreviousWithoutRevokingTheSession()
                throws Exception {
            String first = refreshTokenOf(login());
            String second = refreshTokenOf(refresh(first).andExpect(status().isOk()));
            String third = refreshTokenOf(refresh(second).andExpect(status().isOk()));

            refresh(first)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value(INVALID_MESSAGE));

            assertThat(revokedCount()).isZero();
            refresh(third).andExpect(status().isOk());
        }

        @Test
        void shouldRejectExpiredSession() throws Exception {
            String token = refreshTokenOf(login());
            jdbcTemplate.update(
                    "UPDATE refresh_sessions SET expires_at = now() - interval '1 second'");

            refresh(token)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value(INVALID_MESSAGE));
        }

        @Test
        void shouldRejectUnknownToken() throws Exception {
            refresh("x".repeat(43))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.error").value("Unauthorized"))
                    .andExpect(jsonPath("$.message").value(INVALID_MESSAGE));
        }

        @Test
        void shouldRejectAccessTokenUsedAsRefreshToken() throws Exception {
            String accessToken = JsonPath.read(
                    login().andReturn().getResponse().getContentAsString(),
                    "$.accessToken"
            );

            // O JWT passa do tamanho máximo de um refresh token (128), então nem chega ao banco.
            refresh(accessToken)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.refreshToken").isNotEmpty());
            assertThat(revokedCount()).isZero();
        }

        @Test
        void shouldRevokeSessionAndRejectWhenUserIsInactive() throws Exception {
            String token = refreshTokenOf(login());
            jdbcTemplate.update("UPDATE users SET active = false WHERE id = ?", organizer.getId());

            refresh(token)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value(INVALID_MESSAGE));

            assertThat(revokedCount()).isEqualTo(1);
        }

        @Test
        void shouldRejectBlankOrMissingOrTooLongToken() throws Exception {
            postRaw("/auth/refresh", "{\"refreshToken\":\"\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.refreshToken").isNotEmpty());
            postRaw("/auth/refresh", "{}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.refreshToken").isNotEmpty());
            postRaw("/auth/refresh", "{\"refreshToken\":\"" + "a".repeat(129) + "\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.refreshToken").isNotEmpty());
        }
    }

    @Nested
    @DisplayName("POST /auth/logout")
    class Logout {

        @Test
        void shouldRevokeTheSessionAndBeIdempotent() throws Exception {
            String token = refreshTokenOf(login());

            logout(token).andExpect(status().isNoContent());
            logout(token).andExpect(status().isNoContent());

            refresh(token)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value(INVALID_MESSAGE));
            assertThat(revokedCount()).isEqualTo(1);
        }

        @Test
        void shouldAcceptUnknownTokenWithoutEffect() throws Exception {
            String token = refreshTokenOf(login());

            logout("y".repeat(43)).andExpect(status().isNoContent());

            assertThat(revokedCount()).isZero();
            refresh(token).andExpect(status().isOk());
        }

        @Test
        void shouldOnlyEndTheSessionOfTheGivenToken() throws Exception {
            String phone = refreshTokenOf(login());
            String computer = refreshTokenOf(login());

            logout(phone).andExpect(status().isNoContent());

            refresh(phone).andExpect(status().isUnauthorized());
            refresh(computer).andExpect(status().isOk());
        }

        @Test
        void shouldRejectBlankToken() throws Exception {
            postRaw("/auth/logout", "{\"refreshToken\":\"  \"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.refreshToken").isNotEmpty());
        }
    }

    @Nested
    @DisplayName("Session lock races")
    class SessionLockRaces {

        @Test
        @Timeout(15)
        void secondRotationWithTheSameTokenIsRejectedAndRevokesTheSession()
                throws Exception {
            String token = refreshTokenOf(login());
            CountDownLatch rotated = new CountDownLatch(1);
            CountDownLatch allowCommit = new CountDownLatch(1);
            AtomicReference<String> newToken = new AtomicReference<>();

            Future<?> first = executor.submit(() ->
                    inTransaction(() -> {
                        newToken.set(refreshTokenService.rotate(token).refreshToken());
                        rotated.countDown();
                        await(allowCommit);
                    })
            );

            await(rotated);

            // A segunda renovação para na trava da sessão. Captura a exceção dentro da
            // transação do teste para ela não desfazer a revogação (o HTTP real não tem
            // transação externa).
            String waitingConnection = "refresh-waiter";
            AtomicReference<Throwable> secondFailure = new AtomicReference<>();
            Future<?> second = executor.submit(() ->
                    inNamedTransaction(waitingConnection, () -> {
                        try {
                            refreshTokenService.rotate(token);
                        } catch (InvalidRefreshTokenException exception) {
                            secondFailure.set(exception);
                        }
                    })
            );

            awaitConnectionWaitingForLock(waitingConnection);
            allowCommit.countDown();

            first.get(CONCURRENCY_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            second.get(CONCURRENCY_TIMEOUT.toSeconds(), TimeUnit.SECONDS);

            assertThat(secondFailure.get()).isInstanceOf(InvalidRefreshTokenException.class);
            assertThat(revokedCount()).isEqualTo(1);
            refresh(newToken.get()).andExpect(status().isUnauthorized());
        }
    }

    private ResultActions login() throws Exception {
        return postRaw(
                "/auth/login",
                "{\"email\":\"maria@example.com\",\"password\":\"" + PASSWORD + "\"}"
        );
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return postRaw("/auth/refresh", "{\"refreshToken\":\"" + refreshToken + "\"}");
    }

    private ResultActions logout(String refreshToken) throws Exception {
        return postRaw("/auth/logout", "{\"refreshToken\":\"" + refreshToken + "\"}");
    }

    private ResultActions postRaw(String path, String body) throws Exception {
        return mockMvc.perform(post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static String refreshTokenOf(ResultActions result) throws Exception {
        return JsonPath.read(
                result.andReturn().getResponse().getContentAsString(),
                "$.refreshToken"
        );
    }

    private long sessionCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM refresh_sessions", Long.class);
    }

    private long revokedCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_sessions WHERE revoked_at IS NOT NULL",
                Long.class
        );
    }

    private String currentHash(User user) {
        return jdbcTemplate.queryForObject(
                "SELECT current_token_hash FROM refresh_sessions WHERE user_id = ?",
                String.class,
                user.getId()
        );
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                        .digest(value.getBytes(StandardCharsets.UTF_8))
        );
    }

    private void inTransaction(Runnable operation) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                operation.run()
        );
    }

    private void inNamedTransaction(String connectionName, Runnable operation) {
        inTransaction(() -> {
            jdbcTemplate.queryForObject(
                    "SELECT set_config('application_name', ?, true)",
                    String.class,
                    connectionName
            );
            operation.run();
        });
    }

    private void awaitConnectionWaitingForLock(String connectionName)
            throws InterruptedException {
        long deadline = System.nanoTime() + CONCURRENCY_TIMEOUT.toNanos();

        while (System.nanoTime() < deadline) {
            Long waitingConnections = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM pg_stat_activity
                    WHERE application_name = ?
                      AND wait_event_type = 'Lock'
                    """, Long.class, connectionName);

            if (waitingConnections != null && waitingConnections == 1) {
                return;
            }

            Thread.sleep(25);
        }

        throw new AssertionError(
                "Connection " + connectionName + " did not wait for the session lock"
        );
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(CONCURRENCY_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for concurrent operation");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(
                    "Interrupted while waiting for concurrent operation",
                    exception
            );
        }
    }
}
