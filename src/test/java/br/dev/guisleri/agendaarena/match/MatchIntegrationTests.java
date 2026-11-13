package br.dev.guisleri.agendaarena.match;

import br.dev.guisleri.agendaarena.auth.service.JwtTokenService;
import br.dev.guisleri.agendaarena.establishment.entity.Establishment;
import br.dev.guisleri.agendaarena.establishment.entity.EstablishmentAdmin;
import br.dev.guisleri.agendaarena.establishment.repository.EstablishmentAdminRepository;
import br.dev.guisleri.agendaarena.establishment.repository.EstablishmentRepository;
import br.dev.guisleri.agendaarena.match.repository.MatchRepository;
import br.dev.guisleri.agendaarena.match.service.MatchCreationResult;
import br.dev.guisleri.agendaarena.match.service.MatchService;
import br.dev.guisleri.agendaarena.reservation.entity.Reservation;
import br.dev.guisleri.agendaarena.reservation.repository.ReservationRepository;
import br.dev.guisleri.agendaarena.schedule.entity.ScheduleSlot;
import br.dev.guisleri.agendaarena.schedule.repository.ScheduleSlotRepository;
import br.dev.guisleri.agendaarena.sportfield.entity.SportField;
import br.dev.guisleri.agendaarena.sportfield.model.SportType;
import br.dev.guisleri.agendaarena.sportfield.repository.SportFieldRepository;
import br.dev.guisleri.agendaarena.user.entity.User;
import br.dev.guisleri.agendaarena.user.model.UserRole;
import br.dev.guisleri.agendaarena.user.service.UserService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
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

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Partida, fatia 1: criação pela reserva do organizador e página pública pelo token do link.
 * Usa tokens reais (relógio do sistema); as datas são calculadas a partir de hoje.
 */
@SpringBootTest
@Testcontainers
class MatchIntegrationTests {

    private static final String PASSWORD = "password-123";
    private static final Duration CONCURRENCY_TIMEOUT = Duration.ofSeconds(10);
    private static final String TOKEN_PATTERN = "^[A-Za-z0-9_-]{43}$";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private UserService userService;
    @Autowired
    private JwtTokenService jwtTokenService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private Clock clock;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private EstablishmentRepository establishmentRepository;
    @Autowired
    private EstablishmentAdminRepository establishmentAdminRepository;
    @Autowired
    private SportFieldRepository sportFieldRepository;
    @Autowired
    private ScheduleSlotRepository scheduleSlotRepository;
    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private MatchRepository matchRepository;
    @Autowired
    private MatchService matchService;

    private MockMvc mockMvc;
    private ExecutorService executor;
    private LocalDate baseDate;
    private Establishment establishment;
    private SportField sportField;
    private ScheduleSlot scheduleSlot;
    private User organizer;
    private User otherOrganizer;
    private User admin;

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

        baseDate = LocalDate.now(clock)
                .plusDays(14)
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));

        organizer = user("organizer@example.com", UserRole.ORGANIZER);
        otherOrganizer = user("other.organizer@example.com", UserRole.ORGANIZER);
        admin = user("admin@example.com", UserRole.ESTABLISHMENT_ADMIN);

        establishment = establishmentRepository.save(new Establishment("Arena Central"));
        establishmentAdminRepository.save(
                new EstablishmentAdmin(admin, establishment, Instant.now(clock)));

        sportField = sportFieldRepository.save(
                new SportField(establishment, "Campo 1", SportType.SOCIETY));
        scheduleSlot = scheduleSlotRepository.save(
                new ScheduleSlot(
                        sportField,
                        DayOfWeek.MONDAY,
                        LocalTime.of(19, 0),
                        new BigDecimal("150.00")
                ));
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    @Nested
    @DisplayName("Create match")
    class CreateMatch {

        @Test
        void shouldCreateOpenMatchForMyConfirmedReservation() throws Exception {
            long reservationId = reserve(organizer, baseDate);

            createMatch(organizer, reservationId)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.reservationId").value(reservationId))
                    .andExpect(jsonPath("$.publicToken").value(
                            org.hamcrest.Matchers.matchesPattern(TOKEN_PATTERN)))
                    .andExpect(jsonPath("$.status").value("OPEN"))
                    .andExpect(jsonPath("$.reservationStatus").value("CONFIRMED"))
                    .andExpect(jsonPath("$.establishmentName").value("Arena Central"))
                    .andExpect(jsonPath("$.sportFieldName").value("Campo 1"))
                    .andExpect(jsonPath("$.date").value(baseDate.toString()))
                    .andExpect(jsonPath("$.startTime").value("19:00:00"))
                    .andExpect(jsonPath("$.price").value(150.00))
                    .andExpect(jsonPath("$.createdAt").exists());

            assertThat(matchRepository.count()).isEqualTo(1);
        }

        @Test
        void shouldReturnExistingMatchWhenCreatedTwice() throws Exception {
            long reservationId = reserve(organizer, baseDate);
            String firstToken = tokenOf(createMatch(organizer, reservationId)
                    .andExpect(status().isCreated()));

            String secondToken = tokenOf(createMatch(organizer, reservationId)
                    .andExpect(status().isOk()));

            assertThat(secondToken).isEqualTo(firstToken);
            assertThat(matchRepository.count()).isEqualTo(1);
        }

        @Test
        void shouldGenerateDifferentTokensForDifferentReservations() throws Exception {
            String first = tokenOf(createMatch(organizer, reserve(organizer, baseDate)));
            String second = tokenOf(
                    createMatch(organizer, reserve(organizer, baseDate.plusDays(7))));

            assertThat(first).isNotEqualTo(second);
        }

        @ParameterizedTest
        @ValueSource(strings = {"CANCELLATION_REQUESTED", "CANCELLED", "COMPLETED", "NO_SHOW"})
        void shouldRejectReservationThatIsNotConfirmed(String reservationStatus)
                throws Exception {
            long reservationId = reserve(organizer, baseDate);
            jdbcTemplate.update(
                    "UPDATE reservations SET status = ? WHERE id = ?",
                    reservationStatus,
                    reservationId
            );

            createMatch(organizer, reservationId)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.message")
                            .value("Only confirmed reservations can have a match"));

            assertThat(matchRepository.count()).isZero();
        }

        @Test
        void shouldHideReservationOfAnotherOrganizer() throws Exception {
            long reservationId = reserve(otherOrganizer, baseDate);

            createMatch(organizer, reservationId)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));

            assertThat(matchRepository.count()).isZero();
        }

        @Test
        void shouldNotLetEstablishmentAdminCreateMatchThroughMeForSomeoneElse()
                throws Exception {
            long reservationId = reserve(organizer, baseDate);

            createMatch(admin, reservationId).andExpect(status().isNotFound());

            assertThat(matchRepository.count()).isZero();
        }

        @Test
        void shouldNotCreateMatchForManualReservation() throws Exception {
            Reservation manual = reservationRepository.save(
                    Reservation.manual(scheduleSlot, baseDate, "Cliente Balcao", null));

            createMatch(organizer, manual.getId()).andExpect(status().isNotFound());

            assertThat(matchRepository.count()).isZero();
        }

        @Test
        void shouldReturnNotFoundForUnknownReservation() throws Exception {
            createMatch(organizer, 999_999L).andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("My match")
    class MyMatch {

        @Test
        void shouldReturnMyMatchWithToken() throws Exception {
            long reservationId = reserve(organizer, baseDate);
            String token = tokenOf(createMatch(organizer, reservationId));

            mockMvc.perform(get(matchPath(reservationId))
                            .header("Authorization", bearer(organizer)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.publicToken").value(token))
                    .andExpect(jsonPath("$.status").value("OPEN"));
        }

        @Test
        void shouldReturnNotFoundBeforeMatchIsCreated() throws Exception {
            long reservationId = reserve(organizer, baseDate);

            mockMvc.perform(get(matchPath(reservationId))
                            .header("Authorization", bearer(organizer)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldHideMatchOfAnotherOrganizer() throws Exception {
            long reservationId = reserve(otherOrganizer, baseDate);
            createMatch(otherOrganizer, reservationId).andExpect(status().isCreated());

            mockMvc.perform(get(matchPath(reservationId))
                            .header("Authorization", bearer(organizer)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldRequireAuthentication() throws Exception {
            long reservationId = reserve(organizer, baseDate);

            mockMvc.perform(post(matchPath(reservationId)))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(get(matchPath(reservationId)))
                    .andExpect(status().isUnauthorized());

            assertThat(matchRepository.count()).isZero();
        }
    }

    @Nested
    @DisplayName("Public match page")
    class PublicMatchPage {

        @Test
        void shouldShowMatchToAnonymousVisitorWithTheLink() throws Exception {
            long reservationId = reserve(organizer, baseDate);
            String token = tokenOf(createMatch(organizer, reservationId));

            mockMvc.perform(get("/matches/{token}", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("OPEN"))
                    .andExpect(jsonPath("$.reservationCancelled").value(false))
                    .andExpect(jsonPath("$.establishmentName").value("Arena Central"))
                    .andExpect(jsonPath("$.sportFieldName").value("Campo 1"))
                    .andExpect(jsonPath("$.date").value(baseDate.toString()))
                    .andExpect(jsonPath("$.startTime").value("19:00:00"))
                    .andExpect(jsonPath("$.price").value(150.00))
                    // Sem ids internos, sem o próprio token e sem dados do organizador
                    .andExpect(jsonPath("$.reservationId").doesNotExist())
                    .andExpect(jsonPath("$.id").doesNotExist())
                    .andExpect(jsonPath("$.publicToken").doesNotExist())
                    .andExpect(jsonPath("$.organizerId").doesNotExist());
        }

        @Test
        void shouldFlagCancelledReservation() throws Exception {
            long reservationId = reserve(organizer, baseDate);
            String token = tokenOf(createMatch(organizer, reservationId));
            jdbcTemplate.update(
                    "UPDATE reservations SET status = 'CANCELLED' WHERE id = ?",
                    reservationId
            );

            mockMvc.perform(get("/matches/{token}", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reservationCancelled").value(true));
        }

        @Test
        void shouldReturnNotFoundForUnknownToken() throws Exception {
            String unknownToken = "A".repeat(43);

            mockMvc.perform(get("/matches/{token}", unknownToken))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Match not found"));
        }

        @Test
        void shouldNotAcceptSequentialIdInsteadOfToken() throws Exception {
            createMatch(organizer, reserve(organizer, baseDate)).andExpect(status().isCreated());
            Long matchId = matchRepository.findAll().getFirst().getId();

            mockMvc.perform(get("/matches/{token}", matchId.toString()))
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldKeepOtherMatchRoutesDenied() throws Exception {
            String token = tokenOf(createMatch(organizer, reserve(organizer, baseDate)));

            mockMvc.perform(get("/matches"))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(post("/matches/{token}", token))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Schedule slot lock races")
    class ScheduleSlotLockRaces {

        @Test
        @Timeout(15)
        void concurrentCreationsProduceSingleMatch() throws Exception {
            long reservationId = reserve(organizer, baseDate);
            CountDownLatch firstSaved = new CountDownLatch(1);
            CountDownLatch allowFirstCommit = new CountDownLatch(1);

            Future<MatchCreationResult> first = executor.submit(() ->
                    inTransaction(() -> {
                        MatchCreationResult result =
                                matchService.createMatch(organizer.getId(), reservationId);
                        firstSaved.countDown();
                        await(allowFirstCommit);
                        return result;
                    })
            );

            await(firstSaved);

            String losingConnection = "match-create-loser";
            Future<MatchCreationResult> second = executor.submit(() ->
                    inNamedTransaction(
                            losingConnection,
                            () -> matchService.createMatch(organizer.getId(), reservationId)
                    )
            );

            awaitConnectionWaitingForLock(losingConnection);
            allowFirstCommit.countDown();

            MatchCreationResult firstResult =
                    first.get(CONCURRENCY_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            MatchCreationResult secondResult =
                    second.get(CONCURRENCY_TIMEOUT.toSeconds(), TimeUnit.SECONDS);

            assertThat(firstResult.created()).isTrue();
            assertThat(secondResult.created()).isFalse();
            assertThat(secondResult.details().match().getPublicToken())
                    .isEqualTo(firstResult.details().match().getPublicToken());
            assertThat(matchRepository.count()).isEqualTo(1);
        }
    }

    private ResultActions createMatch(User user, long reservationId) throws Exception {
        return mockMvc.perform(post(matchPath(reservationId))
                .header("Authorization", bearer(user)));
    }

    private String matchPath(long reservationId) {
        return "/me/reservations/" + reservationId + "/match";
    }

    private String tokenOf(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(),
                "$.publicToken");
    }

    private long reserve(User user, LocalDate date) throws Exception {
        String response = mockMvc.perform(post(slotPath() + "/reservations")
                        .header("Authorization", bearer(user))
                        .contentType("application/json")
                        .content("{\"reservationDate\":\"" + date + "\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Number id = JsonPath.read(response, "$.id");

        return id.longValue();
    }

    private String slotPath() {
        return "/establishments/%d/sports-fields/%d/schedule-slots/%d".formatted(
                establishment.getId(), sportField.getId(), scheduleSlot.getId());
    }

    private User user(String email, UserRole role) {
        return userService.createUser("Usuario " + email, email, "11999991234", PASSWORD, role);
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenService.issue(user).accessToken();
    }

    private <T> T inTransaction(Supplier<T> operation) {
        return new TransactionTemplate(transactionManager).execute(status -> operation.get());
    }

    private <T> T inNamedTransaction(String connectionName, Supplier<T> operation) {
        return inTransaction(() -> {
            jdbcTemplate.queryForObject(
                    "SELECT set_config('application_name', ?, true)",
                    String.class,
                    connectionName
            );
            return operation.get();
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
                "Connection " + connectionName + " did not wait for the schedule slot lock"
        );
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(CONCURRENCY_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for latch");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }
}
