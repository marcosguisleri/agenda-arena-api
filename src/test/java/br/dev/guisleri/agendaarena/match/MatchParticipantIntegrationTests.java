package br.dev.guisleri.agendaarena.match;

import br.dev.guisleri.agendaarena.auth.service.JwtTokenService;
import br.dev.guisleri.agendaarena.establishment.entity.Establishment;
import br.dev.guisleri.agendaarena.establishment.repository.EstablishmentRepository;
import br.dev.guisleri.agendaarena.match.exception.GoalkeeperLimitReachedException;
import br.dev.guisleri.agendaarena.match.model.PlayerPosition;
import br.dev.guisleri.agendaarena.match.repository.MatchParticipantRepository;
import br.dev.guisleri.agendaarena.match.repository.MatchRepository;
import br.dev.guisleri.agendaarena.match.service.MatchService;
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
import java.sql.Timestamp;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Partida, fatia 2: inscrição pelo link (linha/goleiro) e edição da lista pelo organizador.
 * Usa tokens reais (relógio do sistema); as datas são calculadas a partir de hoje.
 */
@SpringBootTest
@Testcontainers
class MatchParticipantIntegrationTests {

    private static final String PASSWORD = "password-123";
    private static final Duration CONCURRENCY_TIMEOUT = Duration.ofSeconds(10);

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
    private MatchParticipantRepository participantRepository;
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
    private long reservationId;
    private String token;

    @DynamicPropertySource
    static void configurePostgreSQL(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void setUp() throws Exception {
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

        reservationId = reserve(organizer, baseDate);
        token = createMatch(organizer, reservationId);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    @Nested
    @DisplayName("Join through the link")
    class JoinThroughLink {

        @Test
        void shouldLetAnonymousGuestJoinAsFieldPlayer() throws Exception {
            join(token, "Joao", "FIELD")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("Joao"))
                    .andExpect(jsonPath("$.position").value("FIELD"))
                    .andExpect(jsonPath("$.id").doesNotExist());

            mockMvc.perform(get("/matches/{token}", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.fieldPlayerCount").value(1))
                    .andExpect(jsonPath("$.goalkeeperCount").value(0))
                    .andExpect(jsonPath("$.participants.length()").value(1))
                    .andExpect(jsonPath("$.participants[0].name").value("Joao"))
                    .andExpect(jsonPath("$.participants[0].id").doesNotExist());
        }

        @Test
        void shouldShowParticipantsWithIdsToOrganizerInJoinOrder() throws Exception {
            join(token, "Joao", "FIELD").andExpect(status().isCreated());
            join(token, "Pedro", "GOALKEEPER").andExpect(status().isCreated());

            mockMvc.perform(get(matchPath()).header("Authorization", bearer(organizer)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.fieldPlayerCount").value(1))
                    .andExpect(jsonPath("$.goalkeeperCount").value(1))
                    .andExpect(jsonPath("$.participants[0].name").value("Joao"))
                    .andExpect(jsonPath("$.participants[0].id").isNumber())
                    .andExpect(jsonPath("$.participants[0].joinedAt").exists())
                    .andExpect(jsonPath("$.participants[1].name").value("Pedro"))
                    .andExpect(jsonPath("$.participants[1].position").value("GOALKEEPER"));
        }

        @Test
        void shouldAllowDuplicateNamesAsDifferentParticipants() throws Exception {
            join(token, "Joao", "FIELD").andExpect(status().isCreated());
            join(token, "Joao", "FIELD").andExpect(status().isCreated());

            assertThat(participantRepository.count()).isEqualTo(2);
        }

        @Test
        void shouldNormalizeName() throws Exception {
            join(token, "   Joao    da   Silva  ", "FIELD")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("Joao da Silva"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   "})
        void shouldRejectBlankName(String name) throws Exception {
            join(token, name, "FIELD")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.name").exists());

            assertThat(participantRepository.count()).isZero();
        }

        @Test
        void shouldRejectTooLongName() throws Exception {
            join(token, "a".repeat(61), "FIELD")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.name").exists());
        }

        @Test
        void shouldRejectMissingPosition() throws Exception {
            mockMvc.perform(post("/matches/{token}/participants", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\": \"Joao\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.position").exists());
        }

        @Test
        void shouldRejectUnknownPosition() throws Exception {
            join(token, "Joao", "STRIKER").andExpect(status().isBadRequest());

            assertThat(participantRepository.count()).isZero();
        }

        @Test
        void shouldAcceptUpToFourGoalkeepersAndRejectTheFifth() throws Exception {
            for (int i = 1; i <= MatchService.MAX_GOALKEEPERS; i++) {
                join(token, "Goleiro " + i, "GOALKEEPER").andExpect(status().isCreated());
            }

            join(token, "Goleiro 5", "GOALKEEPER")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Goalkeeper limit of 4 reached"));

            join(token, "Linha", "FIELD").andExpect(status().isCreated());
            assertThat(countPosition(PlayerPosition.GOALKEEPER)).isEqualTo(4);
        }

        @Test
        void shouldRejectFieldPlayerBeyondAbuseLimit() throws Exception {
            insertParticipants(MatchService.MAX_FIELD_PLAYERS, PlayerPosition.FIELD);

            join(token, "Mais um", "FIELD")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Field player limit of 60 reached"));

            join(token, "Goleiro", "GOALKEEPER").andExpect(status().isCreated());
        }

        @Test
        void shouldReturnNotFoundForUnknownToken() throws Exception {
            join("B".repeat(43), "Joao", "FIELD")
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Match not found"));
        }

        @Test
        void shouldRejectJoinWhenReservationWasCancelled() throws Exception {
            jdbcTemplate.update(
                    "UPDATE reservations SET status = 'CANCELLED' WHERE id = ?", reservationId);

            join(token, "Joao", "FIELD")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Reservation was cancelled"));

            assertThat(participantRepository.count()).isZero();
        }

        @Test
        void shouldRejectJoinAfterTheMatchStarted() throws Exception {
            jdbcTemplate.update(
                    "UPDATE reservations SET reservation_date = ? WHERE id = ?",
                    LocalDate.now(clock).minusDays(1),
                    reservationId
            );

            join(token, "Joao", "FIELD")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Match has already started"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"CLOSED", "FINISHED"})
        void shouldRejectJoinWhenMatchIsNotOpen(String matchStatus) throws Exception {
            setMatchStatus(matchStatus);

            join(token, "Joao", "FIELD")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Match is not open"));
        }
    }

    @Nested
    @DisplayName("Organizer edits the list")
    class OrganizerEditsList {

        @Test
        void shouldChangeFieldPlayerToGoalkeeperAndBack() throws Exception {
            long participantId = joinAndGetId("Joao", PlayerPosition.FIELD);

            changePosition(organizer, participantId, "GOALKEEPER")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(participantId))
                    .andExpect(jsonPath("$.position").value("GOALKEEPER"));

            changePosition(organizer, participantId, "FIELD")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.position").value("FIELD"));
        }

        @Test
        void shouldKeepSamePositionWithoutCountingAgainstLimit() throws Exception {
            insertParticipants(MatchService.MAX_GOALKEEPERS - 1, PlayerPosition.GOALKEEPER);
            long goalkeeperId = joinAndGetId("Quarto goleiro", PlayerPosition.GOALKEEPER);

            changePosition(organizer, goalkeeperId, "GOALKEEPER")
                    .andExpect(status().isOk());
        }

        @Test
        void shouldRejectChangeToGoalkeeperWhenLimitIsReached() throws Exception {
            insertParticipants(MatchService.MAX_GOALKEEPERS, PlayerPosition.GOALKEEPER);
            long participantId = joinAndGetId("Joao", PlayerPosition.FIELD);

            changePosition(organizer, participantId, "GOALKEEPER")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Goalkeeper limit of 4 reached"));

            assertThat(participantRepository.findById(participantId).orElseThrow()
                    .getPosition()).isEqualTo(PlayerPosition.FIELD);
        }

        @Test
        void shouldRemoveParticipant() throws Exception {
            long participantId = joinAndGetId("Joao", PlayerPosition.FIELD);

            mockMvc.perform(delete(participantPath(participantId))
                            .header("Authorization", bearer(organizer)))
                    .andExpect(status().isNoContent());

            assertThat(participantRepository.count()).isZero();
        }

        @Test
        void shouldReturnNotFoundForParticipantOfAnotherMatch() throws Exception {
            long otherReservationId = reserve(organizer, baseDate.plusDays(7));
            String otherToken = createMatch(organizer, otherReservationId);
            long foreignParticipant = idOfOnlyParticipant(join(otherToken, "Ze", "FIELD"));

            changePosition(organizer, foreignParticipant, "GOALKEEPER")
                    .andExpect(status().isNotFound());
            mockMvc.perform(delete(participantPath(foreignParticipant))
                            .header("Authorization", bearer(organizer)))
                    .andExpect(status().isNotFound());

            assertThat(participantRepository.count()).isEqualTo(1);
        }

        @Test
        void shouldHideMatchOfAnotherOrganizer() throws Exception {
            long participantId = joinAndGetId("Joao", PlayerPosition.FIELD);

            changePosition(otherOrganizer, participantId, "GOALKEEPER")
                    .andExpect(status().isNotFound());
            mockMvc.perform(delete(participantPath(participantId))
                            .header("Authorization", bearer(otherOrganizer)))
                    .andExpect(status().isNotFound());

            assertThat(participantRepository.findById(participantId)).isPresent();
        }

        @Test
        void shouldRequireAuthenticationToEditList() throws Exception {
            long participantId = joinAndGetId("Joao", PlayerPosition.FIELD);

            mockMvc.perform(patch(participantPath(participantId))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"position\": \"GOALKEEPER\"}"))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(delete(participantPath(participantId)))
                    .andExpect(status().isUnauthorized());

            assertThat(participantRepository.findById(participantId)).isPresent();
        }

        @Test
        void shouldRejectMissingPositionOnChange() throws Exception {
            long participantId = joinAndGetId("Joao", PlayerPosition.FIELD);

            mockMvc.perform(patch(participantPath(participantId))
                            .header("Authorization", bearer(organizer))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.position").exists());
        }

        @Test
        void shouldRejectEditsWhenMatchIsNotOpen() throws Exception {
            long participantId = joinAndGetId("Joao", PlayerPosition.FIELD);
            setMatchStatus("CLOSED");

            changePosition(organizer, participantId, "GOALKEEPER")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Match is not open"));
            mockMvc.perform(delete(participantPath(participantId))
                            .header("Authorization", bearer(organizer)))
                    .andExpect(status().isConflict());

            assertThat(participantRepository.findById(participantId)).isPresent();
        }
    }

    @Nested
    @DisplayName("Match lock races")
    class MatchLockRaces {

        @Test
        @Timeout(15)
        void onlyOneOfTwoConcurrentGoalkeepersTakesTheLastSpot() throws Exception {
            insertParticipants(MatchService.MAX_GOALKEEPERS - 1, PlayerPosition.GOALKEEPER);
            CountDownLatch firstSaved = new CountDownLatch(1);
            CountDownLatch allowFirstCommit = new CountDownLatch(1);

            Future<?> first = executor.submit(() ->
                    inTransaction(() -> {
                        matchService.joinMatch(token, "Primeiro", PlayerPosition.GOALKEEPER);
                        firstSaved.countDown();
                        await(allowFirstCommit);
                    })
            );

            await(firstSaved);

            String losingConnection = "match-goalkeeper-loser";
            Future<?> second = executor.submit(() ->
                    inNamedTransaction(losingConnection, () ->
                            matchService.joinMatch(token, "Segundo", PlayerPosition.GOALKEEPER)
                    )
            );

            awaitConnectionWaitingForLock(losingConnection);
            allowFirstCommit.countDown();

            first.get(CONCURRENCY_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            assertFutureFailedWith(second, GoalkeeperLimitReachedException.class);

            assertThat(countPosition(PlayerPosition.GOALKEEPER))
                    .isEqualTo(MatchService.MAX_GOALKEEPERS);
        }

        @Test
        @Timeout(15)
        void organizerPositionChangeAndGuestJoinCannotBothTakeTheLastSpot() throws Exception {
            insertParticipants(MatchService.MAX_GOALKEEPERS - 1, PlayerPosition.GOALKEEPER);
            long fieldPlayerId = joinAndGetId("Joao", PlayerPosition.FIELD);
            CountDownLatch changeSaved = new CountDownLatch(1);
            CountDownLatch allowChangeCommit = new CountDownLatch(1);

            Future<?> change = executor.submit(() ->
                    inTransaction(() -> {
                        matchService.changeParticipantPosition(
                                organizer.getId(),
                                reservationId,
                                fieldPlayerId,
                                PlayerPosition.GOALKEEPER
                        );
                        changeSaved.countDown();
                        await(allowChangeCommit);
                    })
            );

            await(changeSaved);

            String losingConnection = "match-join-loser";
            Future<?> joinAttempt = executor.submit(() ->
                    inNamedTransaction(losingConnection, () ->
                            matchService.joinMatch(token, "Convidado", PlayerPosition.GOALKEEPER)
                    )
            );

            awaitConnectionWaitingForLock(losingConnection);
            allowChangeCommit.countDown();

            change.get(CONCURRENCY_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            assertFutureFailedWith(joinAttempt, GoalkeeperLimitReachedException.class);

            assertThat(countPosition(PlayerPosition.GOALKEEPER))
                    .isEqualTo(MatchService.MAX_GOALKEEPERS);
        }
    }

    // ---------- Helpers ----------

    private ResultActions join(String matchToken, String name, String position) throws Exception {
        return mockMvc.perform(post("/matches/{token}/participants", matchToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"%s\", \"position\": \"%s\"}".formatted(name, position)));
    }

    private long joinAndGetId(String name, PlayerPosition position) throws Exception {
        join(token, name, position.name()).andExpect(status().isCreated());

        return jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM match_participants", Long.class);
    }

    private long idOfOnlyParticipant(ResultActions joinResult) throws Exception {
        joinResult.andExpect(status().isCreated());

        return jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM match_participants", Long.class);
    }

    private ResultActions changePosition(User user, long participantId, String position)
            throws Exception {
        return mockMvc.perform(patch(participantPath(participantId))
                .header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"position\": \"%s\"}".formatted(position)));
    }

    private void insertParticipants(int quantity, PlayerPosition position) {
        Long matchId = matchRepository.findByPublicToken(token).orElseThrow().getId();

        for (int i = 0; i < quantity; i++) {
            jdbcTemplate.update("""
                    INSERT INTO match_participants (match_id, name, position, created_at)
                    VALUES (?, ?, ?, ?)
                    """, matchId, position.name() + " " + i, position.name(),
                    Timestamp.from(Instant.now(clock)));
        }
    }

    private long countPosition(PlayerPosition position) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM match_participants WHERE position = ?",
                Long.class,
                position.name()
        );
    }

    private void setMatchStatus(String matchStatus) {
        jdbcTemplate.update("UPDATE matches SET status = ? WHERE public_token = ?",
                matchStatus, token);
    }

    private String matchPath() {
        return "/me/reservations/" + reservationId + "/match";
    }

    private String participantPath(long participantId) {
        return matchPath() + "/participants/" + participantId;
    }

    private String createMatch(User user, long id) throws Exception {
        String response = mockMvc.perform(post("/me/reservations/{id}/match", id)
                        .header("Authorization", bearer(user)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return JsonPath.read(response, "$.publicToken");
    }

    private long reserve(User user, LocalDate date) throws Exception {
        String response = mockMvc.perform(post(slotPath() + "/reservations")
                        .header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reservationDate\": \"%s\"}".formatted(date)))
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
                "Connection " + connectionName + " did not wait for the match lock"
        );
    }

    private void assertFutureFailedWith(Future<?> future, Class<? extends Throwable> type) {
        assertThatThrownBy(() -> future.get(CONCURRENCY_TIMEOUT.toSeconds(), TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(type);
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
