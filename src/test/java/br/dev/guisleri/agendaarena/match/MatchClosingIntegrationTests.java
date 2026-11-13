package br.dev.guisleri.agendaarena.match;

import br.dev.guisleri.agendaarena.auth.service.JwtTokenService;
import br.dev.guisleri.agendaarena.establishment.entity.Establishment;
import br.dev.guisleri.agendaarena.establishment.repository.EstablishmentRepository;
import br.dev.guisleri.agendaarena.match.exception.MatchInvalidStateException;
import br.dev.guisleri.agendaarena.match.model.PlayerPosition;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Partida, fatia 3: rateio, pagamento declarado, chave Pix, fechar e reabrir.
 * Preço do horário: R$ 150,00. Usa tokens reais (relógio do sistema).
 */
@SpringBootTest
@Testcontainers
class MatchClosingIntegrationTests {

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
    @DisplayName("Split while open")
    class SplitWhileOpen {

        @Test
        void shouldSplitAmongFieldPlayersAndExemptGoalkeeperByDefault() throws Exception {
            for (int i = 1; i <= 7; i++) {
                join("Linha " + i, "FIELD");
            }
            join("Goleiro", "GOALKEEPER");

            myMatch()
                    .andExpect(jsonPath("$.payingCount").value(7))
                    .andExpect(jsonPath("$.participants[0].shareAmount").value(21.43))
                    .andExpect(jsonPath("$.participants[5].shareAmount").value(21.43))
                    .andExpect(jsonPath("$.participants[6].shareAmount").value(21.42))
                    .andExpect(jsonPath("$.participants[7].paysShare").value(false))
                    .andExpect(jsonPath("$.participants[7].shareAmount").value(0.0));

            mockMvc.perform(get("/matches/{token}", token))
                    .andExpect(jsonPath("$.payingCount").value(7))
                    .andExpect(jsonPath("$.participants[6].shareAmount").value(21.42))
                    .andExpect(jsonPath("$.participants[7].shareAmount").value(0.0));
        }

        @Test
        void shouldRecalculateSharesOnEveryJoin() throws Exception {
            join("Joao", "FIELD")
                    .andExpect(jsonPath("$.participants[0].shareAmount").value(150.00));

            join("Pedro", "FIELD")
                    .andExpect(jsonPath("$.participants[0].shareAmount").value(75.00))
                    .andExpect(jsonPath("$.participants[1].shareAmount").value(75.00));
        }

        @Test
        void shouldLetOrganizerIncludeGoalkeeperInTheSplit() throws Exception {
            join("Joao", "FIELD");
            long goalkeeperId = joinAndGetId("Goleiro", "GOALKEEPER");

            paysShare(goalkeeperId, true)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.payingCount").value(2))
                    .andExpect(jsonPath("$.participants[1].paysShare").value(true))
                    .andExpect(jsonPath("$.participants[1].shareAmount").value(75.00));
        }

        @Test
        void shouldLetOrganizerExemptFieldPlayer() throws Exception {
            join("Joao", "FIELD");
            long exemptId = joinAndGetId("Convidado", "FIELD");

            paysShare(exemptId, false)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.participants[0].shareAmount").value(150.00))
                    .andExpect(jsonPath("$.participants[1].shareAmount").value(0.0));
        }

        @Test
        void shouldResetExemptionToPositionDefaultWhenPositionChanges() throws Exception {
            join("Joao", "FIELD");
            long participantId = joinAndGetId("Pedro", "FIELD");

            changePosition(participantId, "GOALKEEPER")
                    .andExpect(jsonPath("$.participants[1].paysShare").value(false))
                    .andExpect(jsonPath("$.participants[0].shareAmount").value(150.00));

            changePosition(participantId, "FIELD")
                    .andExpect(jsonPath("$.participants[1].paysShare").value(true))
                    .andExpect(jsonPath("$.participants[1].shareAmount").value(75.00));
        }

        @Test
        void shouldRejectMissingPaysShare() throws Exception {
            long participantId = joinAndGetId("Joao", "FIELD");

            mockMvc.perform(put(participantPath(participantId) + "/pays-share")
                            .header("Authorization", bearer(organizer))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.paysShare").exists());
        }
    }

    @Nested
    @DisplayName("Declared payments")
    class DeclaredPayments {

        @Test
        void shouldDeclarePaymentWithCurrentShare() throws Exception {
            long participantId = joinAndGetId("Joao", "FIELD");
            join("Pedro", "FIELD");

            payment(participantId, true)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.participants[0].paid").value(true))
                    .andExpect(jsonPath("$.participants[0].paidAmount").value(75.00))
                    .andExpect(jsonPath("$.participants[0].amountChangedAfterPayment")
                            .value(false));

            mockMvc.perform(get("/matches/{token}", token))
                    .andExpect(jsonPath("$.participants[0].paid").value(true))
                    .andExpect(jsonPath("$.participants[0].paidAmount").doesNotExist());
        }

        @Test
        void shouldUndoDeclaredPayment() throws Exception {
            long participantId = joinAndGetId("Joao", "FIELD");
            payment(participantId, true).andExpect(status().isOk());

            payment(participantId, false)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.participants[0].paid").value(false))
                    .andExpect(jsonPath("$.participants[0].paidAmount").value(nullValue()));
        }

        @Test
        void shouldRejectPaymentOfExemptParticipant() throws Exception {
            join("Joao", "FIELD");
            long goalkeeperId = joinAndGetId("Goleiro", "GOALKEEPER");

            payment(goalkeeperId, true)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Participant is exempt from the split"));
        }

        @Test
        void shouldNotExemptParticipantWithDeclaredPayment() throws Exception {
            long participantId = joinAndGetId("Joao", "FIELD");
            payment(participantId, true).andExpect(status().isOk());

            paysShare(participantId, false)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Participant has a declared payment"));
            changePosition(participantId, "GOALKEEPER")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Participant has a declared payment"));

            myMatch().andExpect(jsonPath("$.participants[0].position").value("FIELD"))
                    .andExpect(jsonPath("$.participants[0].paysShare").value(true));
        }

        @Test
        void shouldNotFlagChangedAmountWhileOpen() throws Exception {
            long participantId = joinAndGetId("Joao", "FIELD");
            payment(participantId, true).andExpect(status().isOk());

            join("Pedro", "FIELD")
                    .andExpect(status().isCreated());

            myMatch()
                    .andExpect(jsonPath("$.participants[0].paidAmount").value(150.00))
                    .andExpect(jsonPath("$.participants[0].shareAmount").value(75.00))
                    .andExpect(jsonPath("$.participants[0].amountChangedAfterPayment")
                            .value(false));
        }

        @Test
        void shouldNotRemoveParticipantWithDeclaredPayment() throws Exception {
            long participantId = joinAndGetId("Joao", "FIELD");
            payment(participantId, true).andExpect(status().isOk());

            mockMvc.perform(delete(participantPath(participantId))
                            .header("Authorization", bearer(organizer)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Participant has a declared payment"));

            payment(participantId, false).andExpect(status().isOk());
            mockMvc.perform(delete(participantPath(participantId))
                            .header("Authorization", bearer(organizer)))
                    .andExpect(status().isNoContent());
        }

        @Test
        void shouldRejectMissingPaidFlag() throws Exception {
            long participantId = joinAndGetId("Joao", "FIELD");

            mockMvc.perform(put(participantPath(participantId) + "/payment")
                            .header("Authorization", bearer(organizer))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.paid").exists());
        }
    }

    @Nested
    @DisplayName("Pix key")
    class PixKey {

        @Test
        void shouldShowTrimmedPixKeyToOrganizerAndGuests() throws Exception {
            pixKey("\"  organizador@example.com  \"")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pixKey").value("organizador@example.com"));

            mockMvc.perform(get("/matches/{token}", token))
                    .andExpect(jsonPath("$.pixKey").value("organizador@example.com"));
        }

        @Test
        void shouldClearPixKeyWithBlankOrNull() throws Exception {
            pixKey("\"chave\"").andExpect(status().isOk());

            pixKey("\"   \"")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pixKey").value(nullValue()));

            pixKey("\"chave\"").andExpect(status().isOk());
            pixKey("null")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pixKey").value(nullValue()));
        }

        @Test
        void shouldRejectTooLongPixKey() throws Exception {
            pixKey("\"" + "a".repeat(141) + "\"")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.pixKey").exists());
        }

        @Test
        void shouldAllowChangingPixKeyWhileClosed() throws Exception {
            join("Joao", "FIELD");
            close().andExpect(status().isOk());

            pixKey("\"chave-nova\"")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pixKey").value("chave-nova"));
        }
    }

    @Nested
    @DisplayName("Close and reopen")
    class CloseAndReopen {

        @Test
        void shouldCloseFreezingSharesAndBlockingRegistration() throws Exception {
            join("Joao", "FIELD");
            join("Pedro", "FIELD");
            join("Goleiro", "GOALKEEPER");

            close()
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CLOSED"))
                    .andExpect(jsonPath("$.closedAt").exists())
                    .andExpect(jsonPath("$.participants[0].shareAmount").value(75.00))
                    .andExpect(jsonPath("$.participants[2].shareAmount").value(0.0));

            assertThat(frozenShares()).containsExactly(
                    new BigDecimal("75.00"),
                    new BigDecimal("75.00"),
                    new BigDecimal("0.00")
            );

            join("Atrasado", "FIELD")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Match is not open"));

            mockMvc.perform(get("/matches/{token}", token))
                    .andExpect(jsonPath("$.status").value("CLOSED"))
                    .andExpect(jsonPath("$.participants.length()").value(3));
        }

        @Test
        void shouldBlockSplitChangesWhileClosed() throws Exception {
            long participantId = joinAndGetId("Joao", "FIELD");
            close().andExpect(status().isOk());

            paysShare(participantId, false)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Match is not open"));
        }

        @Test
        void shouldDeclarePaymentWhileClosedWithFrozenShare() throws Exception {
            long participantId = joinAndGetId("Joao", "FIELD");
            join("Pedro", "FIELD");
            join("Lucas", "FIELD");
            close().andExpect(status().isOk());

            payment(participantId, true)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.participants[0].paid").value(true))
                    .andExpect(jsonPath("$.participants[0].paidAmount").value(50.00));
        }

        @Test
        void shouldRejectClosingWithoutPayingParticipants() throws Exception {
            close()
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Match has no paying participants"));

            join("Goleiro", "GOALKEEPER");

            close().andExpect(status().isConflict());
            myMatch().andExpect(jsonPath("$.status").value("OPEN"));
        }

        @Test
        void shouldRejectClosingTwice() throws Exception {
            join("Joao", "FIELD");
            close().andExpect(status().isOk());

            close()
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Match is not open"));
        }

        @Test
        void shouldReopenUnfreezingSharesAndKeepingPayments() throws Exception {
            long participantId = joinAndGetId("Joao", "FIELD");
            join("Pedro", "FIELD");
            close().andExpect(status().isOk());
            payment(participantId, true).andExpect(status().isOk());

            reopen()
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("OPEN"))
                    .andExpect(jsonPath("$.closedAt").value(nullValue()))
                    .andExpect(jsonPath("$.participants[0].paid").value(true))
                    .andExpect(jsonPath("$.participants[0].amountChangedAfterPayment")
                            .value(false));

            assertThat(frozenShares()).containsOnlyNulls();

            join("Lucas", "FIELD")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.participants[0].shareAmount").value(50.00));

            // Aberta: a cota muda a cada inscrição, então não há alerta.
            myMatch()
                    .andExpect(jsonPath("$.participants[0].paid").value(true))
                    .andExpect(jsonPath("$.participants[0].paidAmount").value(75.00))
                    .andExpect(jsonPath("$.participants[0].shareAmount").value(50.00))
                    .andExpect(jsonPath("$.participants[0].amountChangedAfterPayment")
                            .value(false));

            // Fechada de novo: a cota congelada difere do valor pago, e o alerta aparece.
            close()
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.participants[0].shareAmount").value(50.00))
                    .andExpect(jsonPath("$.participants[0].amountChangedAfterPayment")
                            .value(true))
                    .andExpect(jsonPath("$.participants[1].amountChangedAfterPayment")
                            .value(false));
        }

        @Test
        void shouldRejectReopeningOpenMatch() throws Exception {
            reopen()
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Match is not closed"));
        }
    }

    @Nested
    @DisplayName("Cancelled reservation")
    class CancelledReservation {

        @Test
        void shouldBlockOrganizerChangesButKeepReadingAfterCancellation() throws Exception {
            long participantId = joinAndGetId("Joao", "FIELD");
            join("Pedro", "FIELD");
            jdbcTemplate.update(
                    "UPDATE reservations SET status = 'CANCELLED' WHERE id = ?", reservationId);

            close()
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Reservation was cancelled"));
            payment(participantId, true)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Reservation was cancelled"));
            pixKey("\"chave\"")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Reservation was cancelled"));
            paysShare(participantId, false).andExpect(status().isConflict());
            mockMvc.perform(post(matchPath() + "/team-draw")
                            .header("Authorization", bearer(organizer)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Reservation was cancelled"));

            myMatch()
                    .andExpect(jsonPath("$.status").value("OPEN"))
                    .andExpect(jsonPath("$.reservationStatus").value("CANCELLED"))
                    .andExpect(jsonPath("$.pixKey").value(nullValue()))
                    .andExpect(jsonPath("$.participants[0].paid").value(false));
            mockMvc.perform(get("/matches/{token}/summary", token))
                    .andExpect(status().isOk());
        }

        @Test
        void shouldBlockReopeningAfterCancellation() throws Exception {
            join("Joao", "FIELD");
            close().andExpect(status().isOk());
            jdbcTemplate.update(
                    "UPDATE reservations SET status = 'CANCELLED' WHERE id = ?", reservationId);

            reopen()
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Reservation was cancelled"));
        }
    }

    @Nested
    @DisplayName("Access")
    class Access {

        @Test
        void shouldRequireAuthentication() throws Exception {
            long participantId = joinAndGetId("Joao", "FIELD");

            mockMvc.perform(post(matchPath() + "/close")).andExpect(status().isUnauthorized());
            mockMvc.perform(post(matchPath() + "/reopen")).andExpect(status().isUnauthorized());
            mockMvc.perform(put(matchPath() + "/pix-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"pixKey\": \"x\"}"))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(put(participantPath(participantId) + "/payment")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"paid\": true}"))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(put(participantPath(participantId) + "/pays-share")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"paysShare\": false}"))
                    .andExpect(status().isUnauthorized());

            myMatch()
                    .andExpect(jsonPath("$.status").value("OPEN"))
                    .andExpect(jsonPath("$.pixKey").value(nullValue()))
                    .andExpect(jsonPath("$.participants[0].paid").value(false));
        }

        @Test
        void shouldHideMatchFromAnotherOrganizerWithoutSideEffects() throws Exception {
            long participantId = joinAndGetId("Joao", "FIELD");

            mockMvc.perform(post(matchPath() + "/close")
                            .header("Authorization", bearer(otherOrganizer)))
                    .andExpect(status().isNotFound());
            mockMvc.perform(put(matchPath() + "/pix-key")
                            .header("Authorization", bearer(otherOrganizer))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"pixKey\": \"x\"}"))
                    .andExpect(status().isNotFound());
            mockMvc.perform(put(participantPath(participantId) + "/payment")
                            .header("Authorization", bearer(otherOrganizer))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"paid\": true}"))
                    .andExpect(status().isNotFound());

            myMatch()
                    .andExpect(jsonPath("$.status").value("OPEN"))
                    .andExpect(jsonPath("$.pixKey").value(nullValue()))
                    .andExpect(jsonPath("$.participants[0].paid").value(false));
        }
    }

    @Nested
    @DisplayName("Match lock races")
    class MatchLockRaces {

        @Test
        @Timeout(15)
        void joinWaitingForCloseIsRejectedAndFrozenSharesAddUpToPrice() throws Exception {
            join("Joao", "FIELD");
            join("Pedro", "FIELD");
            CountDownLatch closeSaved = new CountDownLatch(1);
            CountDownLatch allowCloseCommit = new CountDownLatch(1);

            Future<?> closing = executor.submit(() ->
                    inTransaction(() -> {
                        matchService.closeMatch(organizer.getId(), reservationId);
                        closeSaved.countDown();
                        await(allowCloseCommit);
                    })
            );

            await(closeSaved);

            String losingConnection = "match-join-after-close";
            Future<?> joinAttempt = executor.submit(() ->
                    inNamedTransaction(losingConnection, () ->
                            matchService.joinMatch(token, "Atrasado", PlayerPosition.FIELD)
                    )
            );

            awaitConnectionWaitingForLock(losingConnection);
            allowCloseCommit.countDown();

            closing.get(CONCURRENCY_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            assertThatThrownBy(() ->
                    joinAttempt.get(CONCURRENCY_TIMEOUT.toSeconds(), TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(MatchInvalidStateException.class);

            List<BigDecimal> shares = frozenShares();
            assertThat(shares).hasSize(2);
            assertThat(shares.stream().reduce(BigDecimal.ZERO, BigDecimal::add))
                    .isEqualByComparingTo("150.00");
        }
    }

    // ---------- Helpers ----------

    private ResultActions join(String name, String position) throws Exception {
        return mockMvc.perform(post("/matches/{token}/participants", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"%s\", \"position\": \"%s\"}".formatted(name, position)));
    }

    private long joinAndGetId(String name, String position) throws Exception {
        join(name, position).andExpect(status().isCreated());

        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM match_participants", Long.class);
    }

    private ResultActions myMatch() throws Exception {
        return mockMvc.perform(get(matchPath()).header("Authorization", bearer(organizer)))
                .andExpect(status().isOk());
    }

    private ResultActions close() throws Exception {
        return mockMvc.perform(post(matchPath() + "/close")
                .header("Authorization", bearer(organizer)));
    }

    private ResultActions reopen() throws Exception {
        return mockMvc.perform(post(matchPath() + "/reopen")
                .header("Authorization", bearer(organizer)));
    }

    private ResultActions pixKey(String jsonValue) throws Exception {
        return mockMvc.perform(put(matchPath() + "/pix-key")
                .header("Authorization", bearer(organizer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pixKey\": " + jsonValue + "}"));
    }

    private ResultActions paysShare(long participantId, boolean paysShare) throws Exception {
        return mockMvc.perform(put(participantPath(participantId) + "/pays-share")
                .header("Authorization", bearer(organizer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paysShare\": " + paysShare + "}"));
    }

    private ResultActions payment(long participantId, boolean paid) throws Exception {
        return mockMvc.perform(put(participantPath(participantId) + "/payment")
                .header("Authorization", bearer(organizer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paid\": " + paid + "}"));
    }

    private ResultActions changePosition(long participantId, String position) throws Exception {
        return mockMvc.perform(patch(participantPath(participantId))
                .header("Authorization", bearer(organizer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"position\": \"%s\"}".formatted(position)));
    }

    private List<BigDecimal> frozenShares() {
        return jdbcTemplate.queryForList(
                "SELECT share_amount FROM match_participants ORDER BY created_at, id",
                BigDecimal.class
        );
    }

    private String matchPath() {
        return "/me/reservations/" + reservationId + "/match";
    }

    private String participantPath(long participantId) {
        return matchPath() + "/participants/" + participantId;
    }

    private String createMatch() throws Exception {
        String response = mockMvc.perform(post(matchPath())
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
