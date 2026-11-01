package br.dev.guisleri.agendaarena.reservation;

import br.dev.guisleri.agendaarena.establishment.entity.Establishment;
import br.dev.guisleri.agendaarena.establishment.repository.EstablishmentRepository;
import br.dev.guisleri.agendaarena.reservation.entity.Reservation;
import br.dev.guisleri.agendaarena.reservation.exception.ReservationInvalidStateException;
import br.dev.guisleri.agendaarena.reservation.model.ReservationStatus;
import br.dev.guisleri.agendaarena.reservation.repository.RecurringReservationReleaseRepository;
import br.dev.guisleri.agendaarena.reservation.repository.RecurringReservationRepository;
import br.dev.guisleri.agendaarena.reservation.repository.ReservationRepository;
import br.dev.guisleri.agendaarena.reservation.service.ReservationService;
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
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(ReservationCancellationIntegrationTests.TestClockConfiguration.class)
class ReservationCancellationIntegrationTests {

    private static final String CANCEL_PATH =
            "/establishments/{establishmentId}/sports-fields/{sportFieldId}"
                    + "/schedule-slots/{scheduleSlotId}/reservations/{reservationId}/cancel";
    private static final String AVAILABILITY_PATH =
            "/establishments/{establishmentId}/sports-fields/{sportFieldId}/availability";
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    // Relógio fixo: segunda-feira 2026-10-19, 18:30 em São Paulo (o slot começa às 19:00).
    private static final LocalDate NEXT_MONDAY = LocalDate.of(2026, 10, 26);
    private static final LocalDate FOLLOWING_MONDAY = LocalDate.of(2026, 11, 2);
    private static final Duration CONCURRENCY_TIMEOUT = Duration.ofSeconds(10);

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ReservationRepository reservationRepository;

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
    private ScheduleSlot scheduleSlot;
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
        scheduleSlot = scheduleSlotRepository.save(
                new ScheduleSlot(
                        sportField,
                        DayOfWeek.MONDAY,
                        LocalTime.of(19, 0),
                        new BigDecimal("150.50")
                )
        );
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
    @DisplayName("HTTP contract")
    class HttpContract {

        @Test
        void shouldCancelOnlineReservation() throws Exception {
            Reservation reservation = createOnlineReservation(NEXT_MONDAY);

            postCancel(scheduleSlot.getId(), reservation.getId())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(reservation.getId().intValue()))
                    .andExpect(jsonPath("$.status").value("CANCELLED"))
                    .andExpect(jsonPath("$.origin").value("ONLINE"))
                    .andExpect(jsonPath("$.organizerId")
                            .value(organizer.getId().intValue()))
                    .andExpect(jsonPath("$.reservationDate").value("2026-10-26"));

            assertThat(statusOf(reservation.getId()))
                    .isEqualTo(ReservationStatus.CANCELLED);
        }

        @Test
        void shouldCancelManualReservation() throws Exception {
            Reservation reservation = createManualReservation(NEXT_MONDAY);

            postCancel(scheduleSlot.getId(), reservation.getId())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"))
                    .andExpect(jsonPath("$.origin").value("MANUAL"))
                    .andExpect(jsonPath("$.organizerId").value(nullValue()));

            assertThat(statusOf(reservation.getId()))
                    .isEqualTo(ReservationStatus.CANCELLED);
        }

        @Test
        void shouldReturnConflictWhenReservationIsAlreadyCancelled()
                throws Exception {
            Reservation reservation = createOnlineReservation(NEXT_MONDAY);

            postCancel(scheduleSlot.getId(), reservation.getId())
                    .andExpect(status().isOk());

            postCancel(scheduleSlot.getId(), reservation.getId())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.error").value("Conflict"))
                    .andExpect(jsonPath("$.message").value(
                            "Only confirmed reservations can be cancelled"
                    ));

            assertThat(statusOf(reservation.getId()))
                    .isEqualTo(ReservationStatus.CANCELLED);
        }

        @Test
        void shouldReturnNotFoundForUnknownReservation() throws Exception {
            postCancel(scheduleSlot.getId(), 999_999L)
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldReturnNotFoundWhenReservationBelongsToAnotherSlot()
                throws Exception {
            Reservation reservation = createOnlineReservation(NEXT_MONDAY);
            ScheduleSlot otherSlot = scheduleSlotRepository.save(
                    new ScheduleSlot(
                            sportField,
                            DayOfWeek.MONDAY,
                            LocalTime.of(20, 0),
                            new BigDecimal("150.50")
                    )
            );

            postCancel(otherSlot.getId(), reservation.getId())
                    .andExpect(status().isNotFound());

            assertThat(statusOf(reservation.getId()))
                    .isEqualTo(ReservationStatus.CONFIRMED);
        }

        @Test
        void shouldReturnNotFoundForUnknownScheduleSlot() throws Exception {
            Reservation reservation = createOnlineReservation(NEXT_MONDAY);

            postCancel(999_999L, reservation.getId())
                    .andExpect(status().isNotFound());

            assertThat(statusOf(reservation.getId()))
                    .isEqualTo(ReservationStatus.CONFIRMED);
        }

        @Test
        void shouldCancelEvenWhenScheduleSlotWasDeactivated() throws Exception {
            Reservation reservation = createOnlineReservation(NEXT_MONDAY);
            jdbcTemplate.update(
                    "UPDATE schedule_slots SET active = false WHERE id = ?",
                    scheduleSlot.getId()
            );

            postCancel(scheduleSlot.getId(), reservation.getId())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"));
        }

        @Test
        void shouldNotAffectOtherReservations() throws Exception {
            Reservation first = createOnlineReservation(NEXT_MONDAY);
            Reservation second = createManualReservation(FOLLOWING_MONDAY);

            postCancel(scheduleSlot.getId(), first.getId())
                    .andExpect(status().isOk());

            assertThat(statusOf(first.getId())).isEqualTo(ReservationStatus.CANCELLED);
            assertThat(statusOf(second.getId())).isEqualTo(ReservationStatus.CONFIRMED);
        }
    }

    @Nested
    @DisplayName("Effect on occupancy")
    class OccupancyEffect {

        @Test
        void shouldFreeTheSlotInPublicAvailability() throws Exception {
            Reservation reservation = createOnlineReservation(NEXT_MONDAY);

            getAvailability(NEXT_MONDAY)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].available").value(false));

            postCancel(scheduleSlot.getId(), reservation.getId())
                    .andExpect(status().isOk());

            getAvailability(NEXT_MONDAY)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].available").value(true));
        }

        @Test
        void shouldAllowANewReservationAfterCancellation() throws Exception {
            Reservation reservation = createOnlineReservation(NEXT_MONDAY);

            postCancel(scheduleSlot.getId(), reservation.getId())
                    .andExpect(status().isOk());

            createManualReservation(NEXT_MONDAY);

            assertThat(reservationRepository.count()).isEqualTo(2);
            assertThat(countByStatus(ReservationStatus.CANCELLED)).isEqualTo(1);
            assertThat(countByStatus(ReservationStatus.CONFIRMED)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Schedule slot lock races")
    class ScheduleSlotLockRaces {

        @Test
        @Timeout(15)
        void newReservationWaitsForCancellationAndThenSucceeds()
                throws Exception {
            Reservation reservation = createOnlineReservation(NEXT_MONDAY);
            CountDownLatch cancelled = new CountDownLatch(1);
            CountDownLatch allowCancellationCommit = new CountDownLatch(1);

            Future<?> cancellationResult = executor.submit(() ->
                    inTransaction(() -> {
                        cancelReservation(reservation.getId());
                        cancelled.countDown();
                        await(allowCancellationCommit);
                    })
            );

            await(cancelled);

            String waitingConnection = "cancel-reservation-waiter";
            Future<?> reservationResult = executor.submit(() ->
                    inNamedTransaction(
                            waitingConnection,
                            () -> createManualReservation(NEXT_MONDAY)
                    )
            );

            awaitConnectionWaitingForLock(waitingConnection);
            allowCancellationCommit.countDown();

            cancellationResult.get(CONCURRENCY_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            reservationResult.get(CONCURRENCY_TIMEOUT.toSeconds(), TimeUnit.SECONDS);

            assertThat(reservationRepository.count()).isEqualTo(2);
            assertThat(countByStatus(ReservationStatus.CANCELLED)).isEqualTo(1);
            assertThat(countByStatus(ReservationStatus.CONFIRMED)).isEqualTo(1);
        }

        @Test
        @Timeout(15)
        void secondCancellationIsRejectedAfterWaitingForTheLock()
                throws Exception {
            Reservation reservation = createOnlineReservation(NEXT_MONDAY);
            CountDownLatch cancelled = new CountDownLatch(1);
            CountDownLatch allowCancellationCommit = new CountDownLatch(1);

            Future<?> firstCancellation = executor.submit(() ->
                    inTransaction(() -> {
                        cancelReservation(reservation.getId());
                        cancelled.countDown();
                        await(allowCancellationCommit);
                    })
            );

            await(cancelled);

            String waitingConnection = "cancel-cancel-waiter";
            Future<?> secondCancellation = executor.submit(() ->
                    inNamedTransaction(
                            waitingConnection,
                            () -> cancelReservation(reservation.getId())
                    )
            );

            awaitConnectionWaitingForLock(waitingConnection);
            allowCancellationCommit.countDown();

            firstCancellation.get(CONCURRENCY_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            assertFutureFailedWith(
                    secondCancellation,
                    ReservationInvalidStateException.class
            );

            assertThat(statusOf(reservation.getId()))
                    .isEqualTo(ReservationStatus.CANCELLED);
        }
    }

    private ResultActions postCancel(Long scheduleSlotId, Long reservationId)
            throws Exception {
        return mockMvc.perform(post(
                CANCEL_PATH,
                establishment.getId(),
                sportField.getId(),
                scheduleSlotId,
                reservationId
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

    private Reservation createOnlineReservation(LocalDate reservationDate) {
        return reservationService.createOnlineReservation(
                establishment.getId(),
                sportField.getId(),
                scheduleSlot.getId(),
                organizer.getId(),
                reservationDate
        );
    }

    private Reservation createManualReservation(LocalDate reservationDate) {
        return reservationService.createManualReservation(
                establishment.getId(),
                sportField.getId(),
                scheduleSlot.getId(),
                reservationDate,
                "Cliente avulso",
                "11988887777"
        );
    }

    private void cancelReservation(Long reservationId) {
        reservationService.cancelReservation(
                establishment.getId(),
                sportField.getId(),
                scheduleSlot.getId(),
                reservationId
        );
    }

    private ReservationStatus statusOf(Long reservationId) {
        return reservationRepository.findById(reservationId)
                .orElseThrow()
                .getStatus();
    }

    private long countByStatus(ReservationStatus reservationStatus) {
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reservations WHERE status = ?",
                Long.class,
                reservationStatus.name()
        );

        return total == null ? 0 : total;
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
                    Instant.parse("2026-10-19T21:30:00Z"),
                    SAO_PAULO
            );
        }
    }
}
