package br.dev.guisleri.agendaarena.match;

import br.dev.guisleri.agendaarena.auth.service.JwtTokenService;
import br.dev.guisleri.agendaarena.establishment.entity.Establishment;
import br.dev.guisleri.agendaarena.establishment.repository.EstablishmentRepository;
import br.dev.guisleri.agendaarena.match.exception.ReservationNotEligibleForMatchException;
import br.dev.guisleri.agendaarena.match.repository.MatchRepository;
import br.dev.guisleri.agendaarena.match.service.MatchCreationResult;
import br.dev.guisleri.agendaarena.match.service.MatchService;
import br.dev.guisleri.agendaarena.reservation.entity.RecurringReservation;
import br.dev.guisleri.agendaarena.reservation.service.RecurringReservationService;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Partida de uma ocorrência de recorrência (fatia 4): criar e consultar pela série, ocorrência
 * liberada = "cancelada", e corridas no lock do slot. Usa tokens reais (relógio do sistema); as
 * datas são calculadas a partir de hoje.
 */
@SpringBootTest
@Testcontainers
class MatchOccurrenceIntegrationTests {

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
    private SportFieldRepository sportFieldRepository;
    @Autowired
    private ScheduleSlotRepository scheduleSlotRepository;
    @Autowired
    private MatchRepository matchRepository;
    @Autowired
    private MatchService matchService;
    @Autowired
    private RecurringReservationService recurringReservationService;

    private MockMvc mockMvc;
    private ExecutorService executor;
    private LocalDate firstMonday;
    private Establishment establishment;
    private SportField sportField;
    private ScheduleSlot scheduleSlot;
    private User organizer;
    private User otherOrganizer;

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

        // Uma segunda-feira pelo menos 14 dias à frente: sempre futura, qualquer que seja hoje.
        firstMonday = LocalDate.now(clock)
                .plusDays(14)
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));

        organizer = user("organizer@example.com");
        otherOrganizer = user("other.organizer@example.com");

        establishment = establishmentRepository.save(new Establishment("Arena Central"));
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
    @DisplayName("Create match for an occurrence")
    class CreateOccurrenceMatch {

        @Test
        void shouldCreateOpenMatchCopyingSlotData() throws Exception {
            RecurringReservation series = series(organizer, firstMonday, null);

            createMatch(organizer, series, firstMonday.plusWeeks(1))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNumber())
                    .andExpect(jsonPath("$.reservationId").doesNotExist())
                    .andExpect(jsonPath("$.reservationStatus").doesNotExist())
                    .andExpect(jsonPath("$.recurringReservationId").value(series.getId()))
                    .andExpect(jsonPath("$.occurrenceDate")
                            .value(firstMonday.plusWeeks(1).toString()))
                    .andExpect(jsonPath("$.publicToken").value(
                            org.hamcrest.Matchers.matchesPattern(TOKEN_PATTERN)))
                    .andExpect(jsonPath("$.status").value("OPEN"))
                    .andExpect(jsonPath("$.cancelled").value(false))
                    .andExpect(jsonPath("$.establishmentName").value("Arena Central"))
                    .andExpect(jsonPath("$.sportFieldName").value("Campo 1"))
                    .andExpect(jsonPath("$.date").value(firstMonday.plusWeeks(1).toString()))
                    .andExpect(jsonPath("$.startTime").value("19:00:00"))
                    .andExpect(jsonPath("$.endTime").value("20:00:00"))
                    .andExpect(jsonPath("$.price").value(150.00));

            assertThat(matchRepository.count()).isEqualTo(1);
            // Nenhuma reserva é criada para a ocorrência (duplicaria a ocupação).
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM reservations", Integer.class)).isZero();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT organizer_id FROM matches", Long.class)).isEqualTo(organizer.getId());
        }

        @Test
        void shouldFreezeTheSlotPriceAtCreation() throws Exception {
            RecurringReservation series = series(organizer, firstMonday, null);
            String token = tokenOf(createMatch(organizer, series, firstMonday)
                    .andExpect(status().isCreated()));

            jdbcTemplate.update(
                    "UPDATE schedule_slots SET price = 999.00 WHERE id = ?", scheduleSlot.getId());

            mockMvc.perform(get("/matches/{token}", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.price").value(150.00));
        }

        @Test
        void shouldReturnExistingMatchWhenCreatedTwice() throws Exception {
            RecurringReservation series = series(organizer, firstMonday, null);
            String first = tokenOf(createMatch(organizer, series, firstMonday)
                    .andExpect(status().isCreated()));

            String second = tokenOf(createMatch(organizer, series, firstMonday)
                    .andExpect(status().isOk()));

            assertThat(second).isEqualTo(first);
            assertThat(matchRepository.count()).isEqualTo(1);
        }

        @Test
        void shouldCreateOneMatchPerOccurrenceWithDifferentTokens() throws Exception {
            RecurringReservation series = series(organizer, firstMonday, null);

            String first = tokenOf(createMatch(organizer, series, firstMonday)
                    .andExpect(status().isCreated()));
            String second = tokenOf(createMatch(organizer, series, firstMonday.plusWeeks(1))
                    .andExpect(status().isCreated()));

            assertThat(first).isNotEqualTo(second);
            assertThat(matchRepository.count()).isEqualTo(2);
        }

        @Test
        void shouldAllowAnyFutureOccurrenceOfTheSeries() throws Exception {
            RecurringReservation series = series(organizer, firstMonday, null);

            createMatch(organizer, series, firstMonday.plusWeeks(40))
                    .andExpect(status().isCreated());
        }

        @Test
        void shouldHideSeriesOfAnotherOrganizer() throws Exception {
            RecurringReservation series = series(otherOrganizer, firstMonday, null);

            createMatch(organizer, series, firstMonday)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));

            assertThat(matchRepository.count()).isZero();
        }

        @Test
        void shouldNotCreateMatchForSeriesWithoutOrganizer() throws Exception {
            RecurringReservation series = series(null, firstMonday, null);

            createMatch(organizer, series, firstMonday).andExpect(status().isNotFound());

            assertThat(matchRepository.count()).isZero();
        }

        @Test
        void shouldReturnNotFoundForUnknownSeries() throws Exception {
            mockMvc.perform(post(matchPath(999_999L, firstMonday))
                            .header("Authorization", bearer(organizer)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldRejectDateThatIsNotTheSlotDayOfWeek() throws Exception {
            RecurringReservation series = series(organizer, firstMonday, null);

            createMatch(organizer, series, firstMonday.plusDays(1))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(
                            "Occurrence date does not match schedule slot day of week"));

            assertThat(matchRepository.count()).isZero();
        }

        @Test
        void shouldRejectDateBeforeTheSeriesStart() throws Exception {
            RecurringReservation series = series(organizer, firstMonday, null);

            createMatch(organizer, series, firstMonday.minusWeeks(1))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(
                            "Occurrence date is outside the recurring reservation period"));
        }

        @Test
        void shouldRejectDateAfterTheSeriesEnd() throws Exception {
            RecurringReservation series = series(organizer, firstMonday, firstMonday.plusWeeks(2));

            createMatch(organizer, series, firstMonday.plusWeeks(3))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(
                            "Occurrence date is outside the recurring reservation period"));

            assertThat(matchRepository.count()).isZero();
        }

        @Test
        void shouldRejectReleasedOccurrence() throws Exception {
            RecurringReservation series = series(organizer, firstMonday, null);
            release(series, firstMonday.plusWeeks(1));

            createMatch(organizer, series, firstMonday.plusWeeks(1))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.message").value("Occurrence was released"));

            assertThat(matchRepository.count()).isZero();
        }

        @Test
        void shouldRejectOccurrenceThatAlreadyStarted() throws Exception {
            LocalDate pastMonday = firstMonday.minusWeeks(4);
            RecurringReservation series = series(organizer, pastMonday, null);

            createMatch(organizer, series, pastMonday)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Occurrence has already started"));

            assertThat(matchRepository.count()).isZero();
        }

        @Test
        void shouldStillReturnExistingMatchAfterTheGameStarted() throws Exception {
            LocalDate pastMonday = firstMonday.minusWeeks(4);
            RecurringReservation series = series(organizer, pastMonday, null);
            String token = tokenOf(createMatch(organizer, series, firstMonday)
                    .andExpect(status().isCreated()));
            // Empurra a partida para o passado (as duas datas juntas: chk_matches_occurrence_...).
            jdbcTemplate.update(
                    "UPDATE matches SET occurrence_date = ?, match_date = ?",
                    pastMonday, pastMonday);

            assertThat(tokenOf(createMatch(organizer, series, pastMonday)
                    .andExpect(status().isOk()))).isEqualTo(token);
        }

        @Test
        void shouldRejectMalformedDateWithTheStandardErrorBody() throws Exception {
            RecurringReservation series = series(organizer, firstMonday, null);

            mockMvc.perform(post("/me/recurring-reservations/{id}/occurrences/not-a-date/match",
                            series.getId())
                            .header("Authorization", bearer(organizer)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("Bad Request"))
                    .andExpect(jsonPath("$.message").value("Invalid value for parameter 'date'"));

            assertThat(matchRepository.count()).isZero();
        }

        @Test
        void shouldRejectInactiveScheduleSlot() throws Exception {
            RecurringReservation series = series(organizer, firstMonday, null);
            jdbcTemplate.update(
                    "UPDATE schedule_slots SET active = false WHERE id = ?", scheduleSlot.getId());

            createMatch(organizer, series, firstMonday)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(
                            "Schedule slot with id " + scheduleSlot.getId() + " is not active"));

            assertThat(matchRepository.count()).isZero();
        }

        @Test
        void shouldStillReturnExistingMatchWhenTheScheduleSlotWasDeactivated() throws Exception {
            RecurringReservation series = series(organizer, firstMonday, null);
            String token = tokenOf(createMatch(organizer, series, firstMonday)
                    .andExpect(status().isCreated()));
            jdbcTemplate.update(
                    "UPDATE schedule_slots SET active = false WHERE id = ?", scheduleSlot.getId());

            assertThat(tokenOf(createMatch(organizer, series, firstMonday)
                    .andExpect(status().isOk()))).isEqualTo(token);
        }

        @Test
        void shouldRequireAuthentication() throws Exception {
            RecurringReservation series = series(organizer, firstMonday, null);

            mockMvc.perform(post(matchPath(series.getId(), firstMonday)))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(get(matchPath(series.getId(), firstMonday)))
                    .andExpect(status().isUnauthorized());

            assertThat(matchRepository.count()).isZero();
        }
    }

    @Nested
    @DisplayName("My match of an occurrence")
    class MyOccurrenceMatch {

        @Test
        void shouldReturnMyMatchWithToken() throws Exception {
            RecurringReservation series = series(organizer, firstMonday, null);
            String token = tokenOf(createMatch(organizer, series, firstMonday));

            mockMvc.perform(get(matchPath(series.getId(), firstMonday))
                            .header("Authorization", bearer(organizer)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.publicToken").value(token))
                    .andExpect(jsonPath("$.occurrenceDate").value(firstMonday.toString()));
        }

        @Test
        void shouldReturnNotFoundWhenTheOccurrenceHasNoMatch() throws Exception {
            RecurringReservation series = series(organizer, firstMonday, null);

            mockMvc.perform(get(matchPath(series.getId(), firstMonday))
                            .header("Authorization", bearer(organizer)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldHideMatchFromAnotherOrganizer() throws Exception {
            RecurringReservation series = series(organizer, firstMonday, null);
            createMatch(organizer, series, firstMonday);

            mockMvc.perform(get(matchPath(series.getId(), firstMonday))
                            .header("Authorization", bearer(otherOrganizer)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldManageTheMatchByIdAndShowItInTheOccurrenceList() throws Exception {
            RecurringReservation series = series(organizer, firstMonday, null);
            ResultActions created = createMatch(organizer, series, firstMonday);
            Number matchId = JsonPath.read(
                    created.andReturn().getResponse().getContentAsString(), "$.id");
            String token = tokenOf(created);

            join(token, "Joao").andExpect(status().isCreated());

            mockMvc.perform(get("/me/matches/{id}", matchId.longValue())
                            .header("Authorization", bearer(organizer)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.participants.length()").value(1))
                    .andExpect(jsonPath("$.recurringReservationId").value(series.getId()));

            mockMvc.perform(get("/me/recurring-reservations/{id}/occurrences", series.getId())
                            .param("weeks", "2")
                            .header("Authorization", bearer(organizer)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].matchId").value(matchId.longValue()))
                    .andExpect(jsonPath("$[0].matchPublicToken").value(token))
                    .andExpect(jsonPath("$[1].matchId").doesNotExist());
        }
    }

    @Nested
    @DisplayName("Released occurrence = cancelled match")
    class ReleasedOccurrence {

        @Test
        void shouldMarkTheMatchAsCancelledAndRefuseChangesAndRegistrations() throws Exception {
            RecurringReservation series = series(organizer, firstMonday, null);
            ResultActions created = createMatch(organizer, series, firstMonday);
            Number matchId = JsonPath.read(
                    created.andReturn().getResponse().getContentAsString(), "$.id");
            String token = tokenOf(created);
            join(token, "Joao").andExpect(status().isCreated());

            release(series, firstMonday);

            mockMvc.perform(get("/me/matches/{id}", matchId.longValue())
                            .header("Authorization", bearer(organizer)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.cancelled").value(true));
            mockMvc.perform(get("/matches/{token}", token))
                    .andExpect(status().isOk());
            mockMvc.perform(post("/me/matches/{id}/close", matchId.longValue())
                            .header("Authorization", bearer(organizer)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Occurrence was released"));
            join(token, "Maria")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Occurrence was released"));

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM match_participants", Integer.class)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Slot lock races")
    class SlotLockRaces {

        @Test
        void shouldCreateOnlyOneMatchWhenCreatedTwiceAtTheSameTime() throws Exception {
            RecurringReservation series = series(organizer, firstMonday, null);
            CountDownLatch created = new CountDownLatch(1);
            CountDownLatch allowCommit = new CountDownLatch(1);

            Future<MatchCreationResult> first = executor.submit(() ->
                    inTransaction(() -> {
                        MatchCreationResult result = matchService.createOccurrenceMatch(
                                organizer.getId(), series.getId(), firstMonday);
                        created.countDown();
                        await(allowCommit);
                        return result;
                    })
            );
            await(created);

            String waitingConnection = "occurrence-create-waiter";
            Future<MatchCreationResult> second = executor.submit(() ->
                    inNamedTransaction(waitingConnection, () ->
                            matchService.createOccurrenceMatch(
                                    organizer.getId(), series.getId(), firstMonday))
            );

            awaitConnectionWaitingForLock(waitingConnection);
            allowCommit.countDown();

            MatchCreationResult firstResult = first.get(10, TimeUnit.SECONDS);
            MatchCreationResult secondResult = second.get(10, TimeUnit.SECONDS);

            assertThat(firstResult.created()).isTrue();
            assertThat(secondResult.created()).isFalse();
            assertThat(secondResult.details().match().getPublicToken())
                    .isEqualTo(firstResult.details().match().getPublicToken());
            assertThat(matchRepository.count()).isEqualTo(1);
        }

        @Test
        void shouldRefuseCreationWhenTheReleaseWonTheLock() throws Exception {
            RecurringReservation series = series(organizer, firstMonday, null);
            CountDownLatch released = new CountDownLatch(1);
            CountDownLatch allowCommit = new CountDownLatch(1);

            Future<?> release = executor.submit(() ->
                    inTransaction(() -> {
                        recurringReservationService.releaseOccurrence(
                                establishment.getId(), sportField.getId(), scheduleSlot.getId(),
                                series.getId(), firstMonday);
                        released.countDown();
                        await(allowCommit);
                        return null;
                    })
            );
            await(released);

            String waitingConnection = "occurrence-create-after-release";
            Future<MatchCreationResult> create = executor.submit(() ->
                    inNamedTransaction(waitingConnection, () ->
                            matchService.createOccurrenceMatch(
                                    organizer.getId(), series.getId(), firstMonday))
            );

            awaitConnectionWaitingForLock(waitingConnection);
            allowCommit.countDown();
            release.get(10, TimeUnit.SECONDS);

            assertThatThrownBy(() -> create.get(10, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(ReservationNotEligibleForMatchException.class);
            assertThat(matchRepository.count()).isZero();
        }

        @Test
        void shouldReleaseAfterTheCreationAndLeaveTheMatchCancelled() throws Exception {
            RecurringReservation series = series(organizer, firstMonday, null);
            CountDownLatch created = new CountDownLatch(1);
            CountDownLatch allowCommit = new CountDownLatch(1);

            Future<MatchCreationResult> create = executor.submit(() ->
                    inTransaction(() -> {
                        MatchCreationResult result = matchService.createOccurrenceMatch(
                                organizer.getId(), series.getId(), firstMonday);
                        created.countDown();
                        await(allowCommit);
                        return result;
                    })
            );
            await(created);

            String waitingConnection = "occurrence-release-after-create";
            Future<?> release = executor.submit(() ->
                    inNamedTransaction(waitingConnection, () ->
                            recurringReservationService.releaseOccurrence(
                                    establishment.getId(), sportField.getId(),
                                    scheduleSlot.getId(), series.getId(), firstMonday))
            );

            awaitConnectionWaitingForLock(waitingConnection);
            allowCommit.countDown();
            create.get(10, TimeUnit.SECONDS);
            release.get(10, TimeUnit.SECONDS);

            assertThat(matchRepository.count()).isEqualTo(1);
            assertThat(matchService
                    .findMyMatchByOccurrence(organizer.getId(), series.getId(), firstMonday)
                    .sourceCancelled()).isTrue();
        }
    }

    // ---------- Montagem dos cenários ----------

    private RecurringReservation series(User owner, LocalDate startDate, LocalDate endDate) {
        return recurringReservationService.createRecurringReservation(
                establishment.getId(),
                sportField.getId(),
                scheduleSlot.getId(),
                owner != null ? owner.getId() : null,
                "Turma",
                "11999991234",
                startDate,
                endDate
        );
    }

    private void release(RecurringReservation series, LocalDate date) {
        recurringReservationService.releaseOccurrence(
                establishment.getId(), sportField.getId(), scheduleSlot.getId(),
                series.getId(), date);
    }

    private ResultActions createMatch(User user, RecurringReservation series, LocalDate date)
            throws Exception {
        return mockMvc.perform(post(matchPath(series.getId(), date))
                .header("Authorization", bearer(user)));
    }

    private ResultActions join(String token, String name) throws Exception {
        return mockMvc.perform(post("/matches/{token}/participants", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"position\":\"FIELD\"}"));
    }

    private static String matchPath(long seriesId, LocalDate date) {
        return "/me/recurring-reservations/" + seriesId + "/occurrences/" + date + "/match";
    }

    private String tokenOf(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(),
                "$.publicToken");
    }

    private User user(String email) {
        return userService.createUser(
                "Usuario " + email, email, "11999991234", PASSWORD, UserRole.ORGANIZER);
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
                "Connection " + connectionName + " did not wait for the schedule slot lock");
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(CONCURRENCY_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for concurrent operation");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting", exception);
        }
    }
}
