package br.dev.guisleri.agendaarena.match;

import br.dev.guisleri.agendaarena.auth.service.JwtTokenService;
import br.dev.guisleri.agendaarena.establishment.entity.Establishment;
import br.dev.guisleri.agendaarena.establishment.repository.EstablishmentRepository;
import br.dev.guisleri.agendaarena.match.exception.TeamShirtColorConflictException;
import br.dev.guisleri.agendaarena.match.model.MatchTeam;
import br.dev.guisleri.agendaarena.match.model.ShirtColor;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
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
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Partida, fatia 4: times A e B (sorteio e montagem manual), nome e cor de camiseta, resumo.
 * Aleatoriedade com semente fixa. Preço do horário: R$ 150,00. Tokens reais (relógio do sistema).
 */
@SpringBootTest
@Testcontainers
class MatchTeamsIntegrationTests {

    private static final String PASSWORD = "password-123";
    private static final Duration CONCURRENCY_TIMEOUT = Duration.ofSeconds(10);

    @TestConfiguration
    static class SeededRandomConfiguration {

        @Bean
        @Primary
        RandomGenerator seededRandomGenerator() {
            return new Random(20261113L);
        }
    }

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
    private MatchService matchService;

    private MockMvc mockMvc;
    private ExecutorService executor;
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

        LocalDate baseDate = LocalDate.now(clock)
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

        reservationId = reserve(baseDate);
        token = createMatch();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    @Nested
    @DisplayName("Team draw")
    class TeamDraw {

        @Test
        void shouldDrawBalancedTeamsWhileOpen() throws Exception {
            joinPlayers(9, 2);

            drawTeams()
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("OPEN"))
                    .andExpect(jsonPath("$.teamsUpdatedAt").exists())
                    .andExpect(jsonPath("$.participants[0].team").exists());

            assertThat(countTeam("A") + countTeam("B")).isEqualTo(11);
            assertThat(Math.abs(countTeam("A") - countTeam("B"))).isLessThanOrEqualTo(1);
            assertThat(goalkeepersIn("A")).isEqualTo(1);
            assertThat(goalkeepersIn("B")).isEqualTo(1);

            mockMvc.perform(get("/matches/{token}", token))
                    .andExpect(jsonPath("$.teamsUpdatedAt").exists())
                    .andExpect(jsonPath("$.participants[0].team").exists());
        }

        @Test
        void shouldDrawTeamsWhileClosed() throws Exception {
            joinPlayers(4, 0);
            action("/close").andExpect(status().isOk());

            drawTeams()
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CLOSED"));

            assertThat(countTeam("A")).isEqualTo(2);
            assertThat(countTeam("B")).isEqualTo(2);
        }

        @Test
        void shouldRedrawTeams() throws Exception {
            joinPlayers(10, 0);
            drawTeams().andExpect(status().isOk());
            Map<Long, String> first = teams();

            boolean changed = false;
            for (int attempt = 0; attempt < 10 && !changed; attempt++) {
                drawTeams().andExpect(status().isOk());
                changed = !teams().equals(first);
            }

            assertThat(changed).isTrue();
            assertThat(countTeam("A")).isEqualTo(5);
        }

        @Test
        void shouldReplaceManualAssignmentWhenDrawing() throws Exception {
            long firstId = joinPlayers(4, 0);
            moveTo(firstId, "\"A\"").andExpect(status().isOk());

            drawTeams().andExpect(status().isOk());

            assertThat(countTeam("A") + countTeam("B")).isEqualTo(4);
        }

        @Test
        void shouldRejectDrawWithFewerThanTwoParticipants() throws Exception {
            joinPlayers(1, 0);

            drawTeams()
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message")
                            .value("Match needs at least 2 participants to draw teams"));
        }

        @Test
        void shouldKeepTeamsOnReopenAndLeaveLateJoinersWithoutTeam() throws Exception {
            joinPlayers(4, 0);
            action("/close").andExpect(status().isOk());
            drawTeams().andExpect(status().isOk());

            action("/reopen")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.teamsUpdatedAt").exists())
                    .andExpect(jsonPath("$.participants[0].team").exists());

            join("Atrasado", "FIELD")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.participants[4].team").value(nullValue()));

            assertThat(countTeam("A") + countTeam("B")).isEqualTo(4);
        }
    }

    @Nested
    @DisplayName("Manual teams")
    class ManualTeams {

        @Test
        void shouldMovePlayerFreelyBetweenTeamsAndBench() throws Exception {
            long participantId = joinPlayers(3, 0);

            moveTo(participantId, "\"A\"")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.participants[0].team").value("A"))
                    .andExpect(jsonPath("$.teamsUpdatedAt").exists());

            moveTo(participantId, "\"B\"")
                    .andExpect(jsonPath("$.participants[0].team").value("B"));

            moveTo(participantId, "null")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.participants[0].team").value(nullValue()));
        }

        @Test
        void shouldNotRequireBalancedTeams() throws Exception {
            long firstId = joinPlayers(3, 0);

            for (long id = firstId; id < firstId + 3; id++) {
                moveTo(id, "\"A\"").andExpect(status().isOk());
            }

            assertThat(countTeam("A")).isEqualTo(3);
            assertThat(countTeam("B")).isZero();
        }

        @Test
        void shouldTreatMissingTeamAsWithoutTeam() throws Exception {
            long participantId = joinPlayers(1, 0);
            moveTo(participantId, "\"A\"").andExpect(status().isOk());

            mockMvc.perform(put(participantPath(participantId) + "/team")
                            .header("Authorization", bearer(organizer))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.participants[0].team").value(nullValue()));
        }

        @Test
        void shouldMovePlayersWhileClosed() throws Exception {
            long participantId = joinPlayers(2, 0);
            action("/close").andExpect(status().isOk());

            moveTo(participantId, "\"B\"")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.participants[0].team").value("B"));
        }

        @Test
        void shouldRejectUnknownTeam() throws Exception {
            long participantId = joinPlayers(1, 0);

            moveTo(participantId, "\"C\"").andExpect(status().isBadRequest());

            assertThat(countTeam("A") + countTeam("B")).isZero();
        }

        @Test
        void shouldReturnNotFoundForParticipantOfAnotherMatch() throws Exception {
            long otherReservationId = reserve(LocalDate.now(clock)
                    .plusDays(21)
                    .with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY)));
            String otherToken = createMatch(otherReservationId);
            mockMvc.perform(post("/matches/{token}/participants", otherToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\": \"Ze\", \"position\": \"FIELD\"}"))
                    .andExpect(status().isCreated());
            long foreignId = maxParticipantId();

            moveTo(foreignId, "\"A\"").andExpect(status().isNotFound());

            assertThat(countTeam("A")).isZero();
        }
    }

    @Nested
    @DisplayName("Team names and shirt colors")
    class TeamSettings {

        @Test
        void shouldStartWithDefaultNamesAndNoColors() throws Exception {
            myMatch()
                    .andExpect(jsonPath("$.teams.length()").value(2))
                    .andExpect(jsonPath("$.teams[0].team").value("A"))
                    .andExpect(jsonPath("$.teams[0].name").value("Time A"))
                    .andExpect(jsonPath("$.teams[0].shirtColor").value(nullValue()))
                    .andExpect(jsonPath("$.teams[1].team").value("B"))
                    .andExpect(jsonPath("$.teams[1].name").value("Time B"));
        }

        @Test
        void shouldRenameTeamAndChooseShirtColor() throws Exception {
            teamSettings("A", "\"  Colete   Azul \"", "\"BLUE\"")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.teams[0].name").value("Colete Azul"))
                    .andExpect(jsonPath("$.teams[0].shirtColor").value("BLUE"))
                    .andExpect(jsonPath("$.teams[1].name").value("Time B"));

            mockMvc.perform(get("/matches/{token}", token))
                    .andExpect(jsonPath("$.teams[0].name").value("Colete Azul"))
                    .andExpect(jsonPath("$.teams[0].shirtColor").value("BLUE"));
        }

        @Test
        void shouldClearShirtColor() throws Exception {
            teamSettings("B", "\"Sem colete\"", "\"WHITE\"").andExpect(status().isOk());

            teamSettings("B", "\"Sem colete\"", "null")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.teams[1].shirtColor").value(nullValue()));
        }

        @Test
        void shouldRejectSameShirtColorForBothTeams() throws Exception {
            teamSettings("A", "\"Vermelho\"", "\"RED\"").andExpect(status().isOk());

            teamSettings("B", "\"Tambem vermelho\"", "\"RED\"")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message")
                            .value("Teams must have different shirt colors"));

            teamSettings("A", "\"Vermelho de novo\"", "\"RED\"").andExpect(status().isOk());
            myMatch().andExpect(jsonPath("$.teams[1].shirtColor").value(nullValue()));
        }

        @Test
        void shouldValidateTeamSettings() throws Exception {
            teamSettings("A", "\"   \"", "\"BLUE\"")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.name").exists());
            teamSettings("A", "\"" + "a".repeat(31) + "\"", "\"BLUE\"")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.name").exists());
            teamSettings("A", "\"Time\"", "\"TURQUOISE\"").andExpect(status().isBadRequest());
            teamSettings("C", "\"Time\"", "\"BLUE\"").andExpect(status().isBadRequest());

            myMatch().andExpect(jsonPath("$.teams[0].name").value("Time A"));
        }
    }

    @Nested
    @DisplayName("No finish step")
    class NoFinishStep {

        @Test
        void shouldNotExposeFinishEndpoint() throws Exception {
            joinPlayers(2, 0);
            action("/close").andExpect(status().isOk());

            action("/finish").andExpect(status().is4xxClientError());

            assertThat(matchStatus()).isEqualTo("CLOSED");
        }

        @Test
        void shouldDeclarePaymentAfterTheGame() throws Exception {
            long participantId = joinPlayers(2, 0);
            action("/close").andExpect(status().isOk());
            jdbcTemplate.update(
                    "UPDATE reservations SET reservation_date = ? WHERE id = ?",
                    LocalDate.now(clock).minusDays(1),
                    reservationId
            );

            payment(participantId)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.participants[0].paid").value(true));
        }
    }

    @Nested
    @DisplayName("Summary")
    class Summary {

        @Test
        void shouldSummarizePaymentsAndTeams() throws Exception {
            long joaoId = joinAndGetId("Joao", "FIELD");
            long pedroId = joinAndGetId("Pedro", "FIELD");
            join("Lucas", "FIELD");
            long goalkeeperId = joinAndGetId("Goleiro", "GOALKEEPER");
            action("/close").andExpect(status().isOk());
            payment(joaoId).andExpect(status().isOk());
            teamSettings("A", "\"Azul\"", "\"BLUE\"").andExpect(status().isOk());
            moveTo(joaoId, "\"A\"").andExpect(status().isOk());
            moveTo(goalkeeperId, "\"A\"").andExpect(status().isOk());
            moveTo(pedroId, "\"B\"").andExpect(status().isOk());

            mockMvc.perform(get(matchPath() + "/summary")
                            .header("Authorization", bearer(organizer)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CLOSED"))
                    .andExpect(jsonPath("$.establishmentName").value("Arena Central"))
                    .andExpect(jsonPath("$.price").value(150.00))
                    .andExpect(jsonPath("$.participantCount").value(4))
                    .andExpect(jsonPath("$.payingCount").value(3))
                    .andExpect(jsonPath("$.paidCount").value(1))
                    .andExpect(jsonPath("$.collectedTotal").value(50.00))
                    .andExpect(jsonPath("$.pendingTotal").value(100.00))
                    .andExpect(jsonPath("$.pendingPlayers[0]").value("Pedro"))
                    .andExpect(jsonPath("$.pendingPlayers[1]").value("Lucas"))
                    .andExpect(jsonPath("$.teamsAssigned").value(true))
                    .andExpect(jsonPath("$.teams[0].name").value("Azul"))
                    .andExpect(jsonPath("$.teams[0].shirtColor").value("BLUE"))
                    .andExpect(jsonPath("$.teams[0].members.length()").value(2))
                    .andExpect(jsonPath("$.teams[0].members[0].name").value("Joao"))
                    .andExpect(jsonPath("$.teams[0].members[1].position").value("GOALKEEPER"))
                    .andExpect(jsonPath("$.teams[1].name").value("Time B"))
                    .andExpect(jsonPath("$.teams[1].members[0].name").value("Pedro"))
                    .andExpect(jsonPath("$.withoutTeam.length()").value(1))
                    .andExpect(jsonPath("$.withoutTeam[0].name").value("Lucas"));

            mockMvc.perform(get("/matches/{token}/summary", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.collectedTotal").value(50.00))
                    .andExpect(jsonPath("$.teams[0].members[0].id").doesNotExist())
                    .andExpect(jsonPath("$.publicToken").doesNotExist());
        }

        @Test
        void shouldSummarizeOpenMatchWithoutTeams() throws Exception {
            joinPlayers(2, 0);

            mockMvc.perform(get("/matches/{token}/summary", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("OPEN"))
                    .andExpect(jsonPath("$.collectedTotal").value(0.0))
                    .andExpect(jsonPath("$.pendingTotal").value(150.00))
                    .andExpect(jsonPath("$.teamsAssigned").value(false))
                    .andExpect(jsonPath("$.teams[0].members.length()").value(0))
                    .andExpect(jsonPath("$.withoutTeam.length()").value(2));
        }

        @Test
        void shouldReturnNotFoundForUnknownTokenSummary() throws Exception {
            mockMvc.perform(get("/matches/{token}/summary", "C".repeat(43)))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Access")
    class Access {

        @Test
        void shouldRequireAuthentication() throws Exception {
            long participantId = joinPlayers(2, 0);

            mockMvc.perform(post(matchPath() + "/team-draw"))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(put(participantPath(participantId) + "/team")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"team\": \"A\"}"))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(put(matchPath() + "/teams/A")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\": \"X\"}"))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(get(matchPath() + "/summary"))
                    .andExpect(status().isUnauthorized());

            assertThat(countTeam("A") + countTeam("B")).isZero();
            myMatch().andExpect(jsonPath("$.teams[0].name").value("Time A"));
        }

        @Test
        void shouldHideMatchFromAnotherOrganizerWithoutSideEffects() throws Exception {
            long participantId = joinPlayers(2, 0);

            mockMvc.perform(post(matchPath() + "/team-draw")
                            .header("Authorization", bearer(otherOrganizer)))
                    .andExpect(status().isNotFound());
            mockMvc.perform(put(participantPath(participantId) + "/team")
                            .header("Authorization", bearer(otherOrganizer))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"team\": \"A\"}"))
                    .andExpect(status().isNotFound());
            mockMvc.perform(put(matchPath() + "/teams/A")
                            .header("Authorization", bearer(otherOrganizer))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\": \"X\"}"))
                    .andExpect(status().isNotFound());
            mockMvc.perform(get(matchPath() + "/summary")
                            .header("Authorization", bearer(otherOrganizer)))
                    .andExpect(status().isNotFound());

            assertThat(countTeam("A") + countTeam("B")).isZero();
            myMatch().andExpect(jsonPath("$.teams[0].name").value("Time A"));
        }
    }

    @Nested
    @DisplayName("Match lock races")
    class MatchLockRaces {

        @Test
        @Timeout(15)
        void concurrentTeamsCannotTakeTheSameShirtColor() throws Exception {
            CountDownLatch firstSaved = new CountDownLatch(1);
            CountDownLatch allowFirstCommit = new CountDownLatch(1);

            Future<?> first = executor.submit(() ->
                    inTransaction(() -> {
                        matchService.changeTeamSettings(
                                organizer.getId(), reservationId, MatchTeam.A, "Vermelho",
                                ShirtColor.RED);
                        firstSaved.countDown();
                        await(allowFirstCommit);
                    })
            );

            await(firstSaved);

            String losingConnection = "match-shirt-color-loser";
            Future<?> second = executor.submit(() ->
                    inNamedTransaction(losingConnection, () ->
                            matchService.changeTeamSettings(
                                    organizer.getId(), reservationId, MatchTeam.B, "Rubro",
                                    ShirtColor.RED)
                    )
            );

            awaitConnectionWaitingForLock(losingConnection);
            allowFirstCommit.countDown();

            first.get(CONCURRENCY_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            assertThatThrownBy(() ->
                    second.get(CONCURRENCY_TIMEOUT.toSeconds(), TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(TeamShirtColorConflictException.class);

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT team_b_shirt_color FROM matches WHERE public_token = ?",
                    String.class, token)).isNull();
        }
    }

    // ---------- Helpers ----------

    /**
     * Inscreve jogadores e devolve o id do primeiro (ids sequenciais).
     */
    private long joinPlayers(int fieldPlayers, int goalkeepers) throws Exception {
        long firstId = -1;

        for (int i = 1; i <= fieldPlayers; i++) {
            long id = joinAndGetId("Linha " + i, "FIELD");
            firstId = firstId < 0 ? id : firstId;
        }
        for (int i = 1; i <= goalkeepers; i++) {
            long id = joinAndGetId("Goleiro " + i, "GOALKEEPER");
            firstId = firstId < 0 ? id : firstId;
        }

        return firstId;
    }

    private ResultActions join(String name, String position) throws Exception {
        return mockMvc.perform(post("/matches/{token}/participants", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"%s\", \"position\": \"%s\"}".formatted(name, position)));
    }

    private long joinAndGetId(String name, String position) throws Exception {
        join(name, position).andExpect(status().isCreated());

        return maxParticipantId();
    }

    private long maxParticipantId() {
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM match_participants", Long.class);
    }

    private ResultActions myMatch() throws Exception {
        return mockMvc.perform(get(matchPath()).header("Authorization", bearer(organizer)))
                .andExpect(status().isOk());
    }

    private ResultActions drawTeams() throws Exception {
        return action("/team-draw");
    }

    private ResultActions action(String action) throws Exception {
        return mockMvc.perform(post(matchPath() + action)
                .header("Authorization", bearer(organizer)));
    }

    private ResultActions moveTo(long participantId, String teamJson) throws Exception {
        return mockMvc.perform(put(participantPath(participantId) + "/team")
                .header("Authorization", bearer(organizer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"team\": " + teamJson + "}"));
    }

    private ResultActions teamSettings(String team, String nameJson, String colorJson)
            throws Exception {
        return mockMvc.perform(put(matchPath() + "/teams/" + team)
                .header("Authorization", bearer(organizer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": " + nameJson + ", \"shirtColor\": " + colorJson + "}"));
    }

    private ResultActions payment(long participantId) throws Exception {
        return mockMvc.perform(put(participantPath(participantId) + "/payment")
                .header("Authorization", bearer(organizer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paid\": true}"));
    }

    private long countTeam(String team) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM match_participants WHERE team = ?", Long.class, team);
    }

    private long goalkeepersIn(String team) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM match_participants WHERE team = ? AND position = 'GOALKEEPER'",
                Long.class,
                team
        );
    }

    private Map<Long, String> teams() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, team FROM match_participants ORDER BY id");

        return rows.stream().collect(Collectors.toMap(
                row -> ((Number) row.get("id")).longValue(),
                row -> (String) row.get("team")
        ));
    }

    private String matchStatus() {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM matches WHERE public_token = ?", String.class, token);
    }

    private String matchPath() {
        return "/me/reservations/" + reservationId + "/match";
    }

    private String participantPath(long participantId) {
        return matchPath() + "/participants/" + participantId;
    }

    private String createMatch() throws Exception {
        return createMatch(reservationId);
    }

    private String createMatch(long id) throws Exception {
        String response = mockMvc.perform(post("/me/reservations/{id}/match", id)
                        .header("Authorization", bearer(organizer)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return JsonPath.read(response, "$.publicToken");
    }

    private long reserve(LocalDate date) throws Exception {
        String response = mockMvc.perform(post(slotPath() + "/reservations")
                        .header("Authorization", bearer(organizer))
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

    private User user(String email) {
        return userService.createUser(
                "Usuario " + email, email, "11999991234", PASSWORD, UserRole.ORGANIZER);
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
