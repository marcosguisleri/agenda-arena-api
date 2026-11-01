package br.dev.guisleri.agendaarena.reservation;

import br.dev.guisleri.agendaarena.establishment.entity.Establishment;
import br.dev.guisleri.agendaarena.establishment.repository.EstablishmentRepository;
import br.dev.guisleri.agendaarena.reservation.entity.Reservation;
import br.dev.guisleri.agendaarena.reservation.exception.RecurringReservationConflictException;
import br.dev.guisleri.agendaarena.reservation.exception.ReservationInvalidStateException;
import br.dev.guisleri.agendaarena.reservation.model.ReservationStatus;
import br.dev.guisleri.agendaarena.reservation.repository.RecurringReservationReleaseRepository;
import br.dev.guisleri.agendaarena.reservation.repository.RecurringReservationRepository;
import br.dev.guisleri.agendaarena.reservation.repository.ReservationRepository;
import br.dev.guisleri.agendaarena.reservation.repository.SlotBlockRepository;
import br.dev.guisleri.agendaarena.reservation.service.RecurringReservationService;
import br.dev.guisleri.agendaarena.reservation.service.ReservationService;
import br.dev.guisleri.agendaarena.reservation.service.SlotOccupancyService;
import br.dev.guisleri.agendaarena.schedule.entity.ScheduleSlot;
import br.dev.guisleri.agendaarena.schedule.repository.ScheduleSlotRepository;
import br.dev.guisleri.agendaarena.sportfield.entity.SportField;
import br.dev.guisleri.agendaarena.sportfield.model.SportType;
import br.dev.guisleri.agendaarena.sportfield.repository.SportFieldRepository;
import br.dev.guisleri.agendaarena.user.entity.User;
import br.dev.guisleri.agendaarena.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(ReservationStatusTransitionIntegrationTests.TestClockConfiguration.class)
class ReservationStatusTransitionIntegrationTests {

    private static final String RESERVATION_PATH =
            "/establishments/{establishmentId}/sports-fields/{sportFieldId}"
                    + "/schedule-slots/{scheduleSlotId}/reservations/{reservationId}";
    private static final String AVAILABILITY_PATH =
            "/establishments/{establishmentId}/sports-fields/{sportFieldId}/availability";
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    // Relógio fixo: segunda-feira 2026-10-26, 21:00 em São Paulo.
    // Slot das 19:00 já começou; o das 21:00 começa exatamente agora; o das 22:00 ainda não.
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 26);
    private static final LocalDate NEXT_MONDAY = LocalDate.of(2026, 11, 2);
    private static final LocalDate MONDAY_AFTER_NEXT = LocalDate.of(2026, 11, 9);
    private static final Duration CONCURRENCY_TIMEOUT = Duration.ofSeconds(10);

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private RecurringReservationService recurringReservationService;

    @Autowired
    private SlotOccupancyService slotOccupancyService;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private SlotBlockRepository slotBlockRepository;

    @Autowired
    private RecurringReservationReleaseRepository recurringReservationReleaseRepository;

    @Autowired
    private RecurringReservationRepository recurringReservationRepository;

    @Autowired
    private ScheduleSlotRepository scheduleSlotRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SportFieldRepository sportFieldRepository;

    @Autowired
    private EstablishmentRepository establishmentRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ExecutorService executor;
    private Establishment establishment;
    private SportField sportField;
    private ScheduleSlot startedSlot;
    private ScheduleSlot startingNowSlot;
    private ScheduleSlot futureSlot;
    private User organizer;

    @DynamicPropertySource
    static void configurePostgreSQL(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.open-in-view", () -> false);
    }

    @BeforeEach
    void prepareDatabase() {
        slotBlockRepository.deleteAllInBatch();
        recurringReservationReleaseRepository.deleteAllInBatch();
        recurringReservationRepository.deleteAllInBatch();
        reservationRepository.deleteAllInBatch();
        scheduleSlotRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        sportFieldRepository.deleteAllInBatch();
        establishmentRepository.deleteAllInBatch();

        establishment = establishmentRepository.save(
                new Establishment("Arena Central")
        );
        sportField = sportFieldRepository.save(
                new SportField(establishment, "Campo 1", SportType.SOCIETY)
        );
        startedSlot = saveSlot(LocalTime.of(19, 0));
        startingNowSlot = saveSlot(LocalTime.of(21, 0));
        futureSlot = saveSlot(LocalTime.of(22, 0));
        organizer = userRepository.save(
                new User(
                        "Maria da Silva",
                        "maria@example.com",
                        "11999991234",
                        "password-hash"
                )
        );

        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void stopExecutor() throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    @Nested
    @DisplayName("Complete and no-show over HTTP")
    class HttpContract {

        @Test
        void shouldCompleteStartedReservation() throws Exception {
            Reservation reservation = saveOnline(startedSlot, TODAY);

            act(startedSlot, reservation, "complete")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(reservation.getId().intValue()))
                    .andExpect(jsonPath("$.status").value("COMPLETED"));

            assertThat(statusOf(reservation)).isEqualTo(ReservationStatus.COMPLETED);
        }

        @Test
        void shouldMarkStartedReservationAsNoShow() throws Exception {
            Reservation reservation = saveOnline(startedSlot, TODAY);

            act(startedSlot, reservation, "no-show")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("NO_SHOW"));

            assertThat(statusOf(reservation)).isEqualTo(ReservationStatus.NO_SHOW);
        }

        @Test
        void shouldCompleteManualReservationFromPreviousWeek() throws Exception {
            Reservation reservation = reservationRepository.save(
                    Reservation.manual(
                            startedSlot,
                            LocalDate.of(2026, 10, 19),
                            "Cliente avulso",
                            "11988887777"
                    )
            );

            act(startedSlot, reservation, "complete")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.origin").value("MANUAL"))
                    .andExpect(jsonPath("$.status").value("COMPLETED"));
        }

        @Test
        void shouldAllowTransitionExactlyAtStartTime() throws Exception {
            Reservation reservation = saveOnline(startingNowSlot, TODAY);

            act(startingNowSlot, reservation, "complete")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("COMPLETED"));
        }

        @Test
        void shouldRejectTransitionBeforeStartTimeOnTheSameDay() throws Exception {
            Reservation reservation = saveOnline(futureSlot, TODAY);

            act(futureSlot, reservation, "complete")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message")
                            .value("Reservation has not started yet"));
            act(futureSlot, reservation, "no-show")
                    .andExpect(status().isConflict());

            assertThat(statusOf(reservation)).isEqualTo(ReservationStatus.CONFIRMED);
        }

        @Test
        void shouldRejectTransitionForFutureDate() throws Exception {
            Reservation reservation = saveOnline(startedSlot, NEXT_MONDAY);

            act(startedSlot, reservation, "complete")
                    .andExpect(status().isConflict());
            act(startedSlot, reservation, "no-show")
                    .andExpect(status().isConflict());

            assertThat(statusOf(reservation)).isEqualTo(ReservationStatus.CONFIRMED);
        }

        @Test
        void shouldNotTransitionFinalStatesAgain() throws Exception {
            Reservation completed = saveOnline(startedSlot, TODAY);
            act(startedSlot, completed, "complete").andExpect(status().isOk());

            act(startedSlot, completed, "complete")
                    .andExpect(status().isConflict());
            act(startedSlot, completed, "no-show")
                    .andExpect(status().isConflict());
            act(startedSlot, completed, "cancel")
                    .andExpect(status().isConflict());
            assertThat(statusOf(completed)).isEqualTo(ReservationStatus.COMPLETED);

            Reservation noShow = saveOnline(startingNowSlot, TODAY);
            act(startingNowSlot, noShow, "no-show").andExpect(status().isOk());

            act(startingNowSlot, noShow, "complete")
                    .andExpect(status().isConflict());
            act(startingNowSlot, noShow, "no-show")
                    .andExpect(status().isConflict());
            act(startingNowSlot, noShow, "cancel")
                    .andExpect(status().isConflict());
            assertThat(statusOf(noShow)).isEqualTo(ReservationStatus.NO_SHOW);
        }

        @Test
        void shouldNotCreateBlockWhenCancellingFinishedReservationWithKeepBlocked()
                throws Exception {
            Reservation reservation = saveOnline(startedSlot, TODAY);
            act(startedSlot, reservation, "complete").andExpect(status().isOk());

            mockMvc.perform(post(
                            RESERVATION_PATH + "/cancel",
                            establishment.getId(),
                            sportField.getId(),
                            startedSlot.getId(),
                            reservation.getId()
                    ).param("keepBlocked", "true"))
                    .andExpect(status().isConflict());

            assertThat(slotBlockRepository.count()).isZero();
            assertThat(statusOf(reservation)).isEqualTo(ReservationStatus.COMPLETED);
        }

        @Test
        void shouldNotTransitionCancelledReservation() throws Exception {
            Reservation reservation = saveOnline(startedSlot, TODAY);
            act(startedSlot, reservation, "cancel").andExpect(status().isOk());

            act(startedSlot, reservation, "complete")
                    .andExpect(status().isConflict());
            act(startedSlot, reservation, "no-show")
                    .andExpect(status().isConflict());

            assertThat(statusOf(reservation)).isEqualTo(ReservationStatus.CANCELLED);
        }

        @Test
        void shouldStillCancelConfirmedReservationThatAlreadyStarted()
                throws Exception {
            Reservation reservation = saveOnline(startedSlot, TODAY);

            act(startedSlot, reservation, "cancel")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"));
        }

        @Test
        void shouldReturnNotFoundForUnknownReservation() throws Exception {
            mockMvc.perform(post(
                            RESERVATION_PATH + "/complete",
                            establishment.getId(),
                            sportField.getId(),
                            startedSlot.getId(),
                            999_999L
                    ))
                    .andExpect(status().isNotFound());
            mockMvc.perform(post(
                            RESERVATION_PATH + "/no-show",
                            establishment.getId(),
                            sportField.getId(),
                            startedSlot.getId(),
                            999_999L
                    ))
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldReturnNotFoundWhenReservationBelongsToAnotherSlot()
                throws Exception {
            Reservation reservation = saveOnline(startedSlot, TODAY);

            act(startingNowSlot, reservation, "complete")
                    .andExpect(status().isNotFound());
            act(startingNowSlot, reservation, "no-show")
                    .andExpect(status().isNotFound());

            assertThat(statusOf(reservation)).isEqualTo(ReservationStatus.CONFIRMED);
        }

        @Test
        void shouldReturnNotFoundForUnknownScheduleSlot() throws Exception {
            Reservation reservation = saveOnline(startedSlot, TODAY);

            mockMvc.perform(post(
                            RESERVATION_PATH + "/complete",
                            establishment.getId(),
                            sportField.getId(),
                            999_999L,
                            reservation.getId()
                    ))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("COMPLETED and NO_SHOW keep occupying the slot")
    class Occupancy {

        @Test
        void shouldKeepSlotOccupiedAfterCompletionAndNoShow() throws Exception {
            Reservation completed = saveOnline(startedSlot, TODAY);
            Reservation noShow = saveOnline(startingNowSlot, TODAY);

            act(startedSlot, completed, "complete").andExpect(status().isOk());
            act(startingNowSlot, noShow, "no-show").andExpect(status().isOk());

            assertThat(slotOccupancyService.isOccupied(startedSlot.getId(), TODAY)).isTrue();
            assertThat(slotOccupancyService.isOccupied(startingNowSlot.getId(), TODAY)).isTrue();
        }

        @Test
        void shouldShowFinishedReservationsAsUnavailableInTheAvailability()
                throws Exception {
            Reservation completed = saveOnline(startedSlot, NEXT_MONDAY);
            Reservation noShow = saveOnline(startingNowSlot, NEXT_MONDAY);
            // As transições só são permitidas depois do início; o estado é forçado direto
            // no banco para testar a regra de ocupação em uma data futura.
            forceStatus(completed, "COMPLETED");
            forceStatus(noShow, "NO_SHOW");

            getAvailability(NEXT_MONDAY)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(3))
                    .andExpect(jsonPath("$[0].scheduleSlotId")
                            .value(startedSlot.getId().intValue()))
                    .andExpect(jsonPath("$[0].available").value(false))
                    .andExpect(jsonPath("$[1].scheduleSlotId")
                            .value(startingNowSlot.getId().intValue()))
                    .andExpect(jsonPath("$[1].available").value(false))
                    .andExpect(jsonPath("$[2].scheduleSlotId")
                            .value(futureSlot.getId().intValue()))
                    .andExpect(jsonPath("$[2].available").value(true));
        }

        @Test
        void shouldRejectRecurringReservationOverFinishedReservation() {
            Reservation completed = saveOnline(startedSlot, NEXT_MONDAY);
            forceStatus(completed, "COMPLETED");

            assertThatThrownBy(() ->
                    recurringReservationService.createRecurringReservation(
                            establishment.getId(),
                            sportField.getId(),
                            startedSlot.getId(),
                            organizer.getId(),
                            organizer.getName(),
                            organizer.getPhone(),
                            TODAY.plusDays(7),
                            MONDAY_AFTER_NEXT
                    )
            ).isInstanceOf(RecurringReservationConflictException.class);
            assertThatThrownBy(() ->
                    recurringReservationService.createRecurringReservation(
                            establishment.getId(),
                            sportField.getId(),
                            startedSlot.getId(),
                            organizer.getId(),
                            organizer.getName(),
                            organizer.getPhone(),
                            TODAY.plusDays(7),
                            null
                    )
            ).isInstanceOf(RecurringReservationConflictException.class);

            assertThat(recurringReservationRepository.count()).isZero();
        }

        @Test
        void shouldFreeSlotAfterCancellationOfConfirmedReservationOnly() {
            Reservation cancelled = saveOnline(futureSlot, NEXT_MONDAY);
            reservationService.cancelReservation(
                    establishment.getId(),
                    sportField.getId(),
                    futureSlot.getId(),
                    cancelled.getId()
            );

            Reservation replacement = createManualReservation(futureSlot, NEXT_MONDAY);

            assertThat(replacement.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        }
    }

    @Nested
    @DisplayName("Database constraints (migration V10)")
    class DatabaseConstraints {

        @Test
        void shouldAcceptAllFourStatusesInTheCheckConstraint() {
            Reservation reservation = saveOnline(startedSlot, NEXT_MONDAY);

            for (String value : new String[]{"COMPLETED", "NO_SHOW", "CANCELLED", "CONFIRMED"}) {
                forceStatus(reservation, value);
                assertThat(statusOf(reservation).name()).isEqualTo(value);
            }
        }

        @Test
        void shouldRejectUnknownStatusInTheCheckConstraint() {
            Reservation reservation = saveOnline(startedSlot, NEXT_MONDAY);

            assertThatThrownBy(() -> forceStatus(reservation, "PENDING"))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void shouldRejectSecondOccupyingReservationForTheSameSlotAndDate() {
            for (String occupyingStatus : new String[]{"COMPLETED", "NO_SHOW"}) {
                jdbcTemplate.update("DELETE FROM reservations");

                Reservation first = saveOnline(startedSlot, NEXT_MONDAY);
                forceStatus(first, occupyingStatus);

                assertThatThrownBy(() -> saveOnline(startedSlot, NEXT_MONDAY))
                        .isInstanceOf(DataIntegrityViolationException.class)
                        .hasMessageContaining("idx_unique_active_reservation");
            }
        }

        @Test
        void shouldAllowCancelledReservationNextToAnOccupyingOne() {
            Reservation cancelled = saveOnline(startedSlot, NEXT_MONDAY);
            forceStatus(cancelled, "CANCELLED");

            Reservation completed = saveOnline(startedSlot, NEXT_MONDAY);
            forceStatus(completed, "COMPLETED");

            assertThat(reservationRepository.count()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Slot lock races")
    class SlotLockRaces {

        @Test
        @Timeout(15)
        void cancelWaitsForCompletionAndIsRejected() throws Exception {
            Reservation reservation = saveOnline(startedSlot, TODAY);
            CountDownLatch completed = new CountDownLatch(1);
            CountDownLatch allowCommit = new CountDownLatch(1);

            Future<?> completion = executor.submit(() ->
                    inTransaction(() -> {
                        reservationService.completeReservation(
                                establishment.getId(),
                                sportField.getId(),
                                startedSlot.getId(),
                                reservation.getId()
                        );
                        completed.countDown();
                        await(allowCommit);
                    })
            );

            await(completed);

            String waitingConnection = "cancel-waits-completion";
            Future<?> cancellation = executor.submit(() ->
                    inNamedTransaction(waitingConnection, () ->
                            reservationService.cancelReservation(
                                    establishment.getId(),
                                    sportField.getId(),
                                    startedSlot.getId(),
                                    reservation.getId()
                            )
                    )
            );

            awaitConnectionWaitingForLock(waitingConnection);
            allowCommit.countDown();

            completion.get(CONCURRENCY_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            assertFutureFailedWith(cancellation, ReservationInvalidStateException.class);

            assertThat(statusOf(reservation)).isEqualTo(ReservationStatus.COMPLETED);
        }

        @Test
        @Timeout(15)
        void completionWaitsForCancellationAndIsRejected() throws Exception {
            Reservation reservation = saveOnline(startedSlot, TODAY);
            CountDownLatch cancelled = new CountDownLatch(1);
            CountDownLatch allowCommit = new CountDownLatch(1);

            Future<?> cancellation = executor.submit(() ->
                    inTransaction(() -> {
                        reservationService.cancelReservation(
                                establishment.getId(),
                                sportField.getId(),
                                startedSlot.getId(),
                                reservation.getId()
                        );
                        cancelled.countDown();
                        await(allowCommit);
                    })
            );

            await(cancelled);

            String waitingConnection = "completion-waits-cancel";
            Future<?> completion = executor.submit(() ->
                    inNamedTransaction(waitingConnection, () ->
                            reservationService.completeReservation(
                                    establishment.getId(),
                                    sportField.getId(),
                                    startedSlot.getId(),
                                    reservation.getId()
                            )
                    )
            );

            awaitConnectionWaitingForLock(waitingConnection);
            allowCommit.countDown();

            cancellation.get(CONCURRENCY_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            assertFutureFailedWith(completion, ReservationInvalidStateException.class);

            assertThat(statusOf(reservation)).isEqualTo(ReservationStatus.CANCELLED);
        }

        @Test
        @Timeout(15)
        void completionAndNoShowCannotBothSucceed() throws Exception {
            Reservation reservation = saveOnline(startedSlot, TODAY);
            CountDownLatch completed = new CountDownLatch(1);
            CountDownLatch allowCommit = new CountDownLatch(1);

            Future<?> completion = executor.submit(() ->
                    inTransaction(() -> {
                        reservationService.completeReservation(
                                establishment.getId(),
                                sportField.getId(),
                                startedSlot.getId(),
                                reservation.getId()
                        );
                        completed.countDown();
                        await(allowCommit);
                    })
            );

            await(completed);

            String waitingConnection = "no-show-waits-completion";
            Future<?> noShow = executor.submit(() ->
                    inNamedTransaction(waitingConnection, () ->
                            reservationService.markReservationAsNoShow(
                                    establishment.getId(),
                                    sportField.getId(),
                                    startedSlot.getId(),
                                    reservation.getId()
                            )
                    )
            );

            awaitConnectionWaitingForLock(waitingConnection);
            allowCommit.countDown();

            completion.get(CONCURRENCY_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            assertFutureFailedWith(noShow, ReservationInvalidStateException.class);

            assertThat(statusOf(reservation)).isEqualTo(ReservationStatus.COMPLETED);
        }
    }

    private ScheduleSlot saveSlot(LocalTime startTime) {
        return scheduleSlotRepository.save(
                new ScheduleSlot(
                        sportField,
                        DayOfWeek.MONDAY,
                        startTime,
                        new BigDecimal("150.50")
                )
        );
    }

    /**
     * Cria a reserva direto no repositório: o relógio fixo está depois do início de alguns
     * horários, e o serviço de criação (corretamente) recusa horários que já passaram.
     */
    private Reservation saveOnline(ScheduleSlot slot, LocalDate date) {
        return reservationRepository.saveAndFlush(
                Reservation.online(organizer, slot, date)
        );
    }

    private Reservation createManualReservation(ScheduleSlot slot, LocalDate date) {
        return reservationService.createManualReservation(
                establishment.getId(),
                sportField.getId(),
                slot.getId(),
                date,
                "Cliente avulso",
                "11988887777"
        );
    }

    private ResultActions act(ScheduleSlot slot, Reservation reservation, String action)
            throws Exception {
        return mockMvc.perform(post(
                RESERVATION_PATH + "/" + action,
                establishment.getId(),
                sportField.getId(),
                slot.getId(),
                reservation.getId()
        ));
    }

    private ResultActions getAvailability(LocalDate date) throws Exception {
        return mockMvc.perform(get(
                        AVAILABILITY_PATH,
                        establishment.getId(),
                        sportField.getId()
                )
                        .param("date", date.toString()));
    }

    private ReservationStatus statusOf(Reservation reservation) {
        return reservationRepository.findById(reservation.getId())
                .orElseThrow()
                .getStatus();
    }

    private void forceStatus(Reservation reservation, String statusName) {
        jdbcTemplate.update(
                "UPDATE reservations SET status = ? WHERE id = ?",
                statusName,
                reservation.getId()
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
                "Connection " + connectionName
                        + " did not wait for the schedule slot lock"
        );
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(
                    CONCURRENCY_TIMEOUT.toSeconds(),
                    TimeUnit.SECONDS
            )) {
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

    private void assertFutureFailedWith(
            Future<?> future,
            Class<? extends RuntimeException> expectedException
    ) {
        assertThatThrownBy(() ->
                future.get(CONCURRENCY_TIMEOUT.toSeconds(), TimeUnit.SECONDS)
        )
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(expectedException);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestClockConfiguration {

        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(
                    Instant.parse("2026-10-27T00:00:00Z"),
                    SAO_PAULO
            );
        }
    }
}
