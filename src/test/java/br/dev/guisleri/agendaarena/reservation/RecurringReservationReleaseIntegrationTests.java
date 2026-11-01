package br.dev.guisleri.agendaarena.reservation;

import br.dev.guisleri.agendaarena.establishment.entity.Establishment;
import br.dev.guisleri.agendaarena.establishment.repository.EstablishmentRepository;
import br.dev.guisleri.agendaarena.reservation.entity.RecurringReservation;
import br.dev.guisleri.agendaarena.reservation.exception.RecurringReservationConflictException;
import br.dev.guisleri.agendaarena.reservation.exception.ReservationAlreadyExistsException;
import br.dev.guisleri.agendaarena.reservation.repository.RecurringReservationReleaseRepository;
import br.dev.guisleri.agendaarena.reservation.repository.RecurringReservationRepository;
import br.dev.guisleri.agendaarena.reservation.repository.ReservationRepository;
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
import org.springframework.http.MediaType;
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
@Import(RecurringReservationReleaseIntegrationTests.TestClockConfiguration.class)
class RecurringReservationReleaseIntegrationTests {

    private static final String RELEASES_PATH =
            "/establishments/{establishmentId}/sports-fields/{sportFieldId}"
                    + "/schedule-slots/{scheduleSlotId}"
                    + "/recurring-reservations/{recurringReservationId}/releases";
    private static final String AVAILABILITY_PATH =
            "/establishments/{establishmentId}/sports-fields/{sportFieldId}/availability";
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    // Relógio fixo: segunda-feira 2026-10-19, 18:30 em São Paulo (o slot começa às 19:00).
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 19);
    private static final LocalDate PAST_MONDAY = LocalDate.of(2026, 10, 12);
    private static final LocalDate FIRST_MONDAY = LocalDate.of(2026, 10, 26);
    private static final LocalDate SECOND_MONDAY = LocalDate.of(2026, 11, 2);
    private static final LocalDate THIRD_MONDAY = LocalDate.of(2026, 11, 9);
    private static final LocalDate FOURTH_MONDAY = LocalDate.of(2026, 11, 16);
    private static final LocalDate TUESDAY_IN_PERIOD = LocalDate.of(2026, 11, 3);
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
    private RecurringReservationRepository recurringReservationRepository;

    @Autowired
    private RecurringReservationReleaseRepository recurringReservationReleaseRepository;

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
        // A FK das liberações é ON DELETE RESTRICT: apague as liberações antes das recorrências.
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
        void shouldReleaseOccurrenceWithoutDeletingTheSeries() throws Exception {
            RecurringReservation recurring =
                    createRecurringReservation(FIRST_MONDAY, THIRD_MONDAY);

            postRelease(scheduleSlot.getId(), recurring.getId(), SECOND_MONDAY)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNumber())
                    .andExpect(jsonPath("$.recurringReservationId")
                            .value(recurring.getId().intValue()))
                    .andExpect(jsonPath("$.occurrenceDate").value("2026-11-02"))
                    .andExpect(jsonPath("$.releasedAt").isNotEmpty());

            assertThat(recurringReservationReleaseRepository.count()).isEqualTo(1);
            assertThat(recurringReservationRepository.count()).isEqualTo(1);
        }

        @Test
        void shouldReleaseOccurrenceOfOpenEndedRecurringReservation()
                throws Exception {
            RecurringReservation recurring =
                    createRecurringReservation(FIRST_MONDAY, null);

            postRelease(scheduleSlot.getId(), recurring.getId(), FOURTH_MONDAY)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.occurrenceDate").value("2026-11-16"));

            assertThat(recurringReservationReleaseRepository.count()).isEqualTo(1);
        }

        @Test
        void shouldAllowReleasingFirstAndLastOccurrences() throws Exception {
            RecurringReservation recurring =
                    createRecurringReservation(FIRST_MONDAY, THIRD_MONDAY);

            postRelease(scheduleSlot.getId(), recurring.getId(), FIRST_MONDAY)
                    .andExpect(status().isCreated());
            postRelease(scheduleSlot.getId(), recurring.getId(), THIRD_MONDAY)
                    .andExpect(status().isCreated());

            assertThat(recurringReservationReleaseRepository.count()).isEqualTo(2);
        }

        @Test
        void shouldReturnConflictWhenOccurrenceWasAlreadyReleased()
                throws Exception {
            RecurringReservation recurring =
                    createRecurringReservation(FIRST_MONDAY, THIRD_MONDAY);

            postRelease(scheduleSlot.getId(), recurring.getId(), SECOND_MONDAY)
                    .andExpect(status().isCreated());

            postRelease(scheduleSlot.getId(), recurring.getId(), SECOND_MONDAY)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.error").value("Conflict"));

            assertThat(recurringReservationReleaseRepository.count()).isEqualTo(1);
        }

        @Test
        void shouldRejectDateAfterRecurringReservationEnd() throws Exception {
            RecurringReservation recurring =
                    createRecurringReservation(FIRST_MONDAY, THIRD_MONDAY);

            postRelease(scheduleSlot.getId(), recurring.getId(), FOURTH_MONDAY)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(
                            "Occurrence date is outside the recurring reservation period"
                    ));

            assertThat(recurringReservationReleaseRepository.count()).isZero();
        }

        @Test
        void shouldRejectDateBeforeRecurringReservationStart() throws Exception {
            RecurringReservation recurring =
                    createRecurringReservation(FIRST_MONDAY, THIRD_MONDAY);

            postRelease(scheduleSlot.getId(), recurring.getId(), TODAY)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(
                            "Occurrence date is outside the recurring reservation period"
                    ));

            assertThat(recurringReservationReleaseRepository.count()).isZero();
        }

        @Test
        void shouldRejectDateThatDoesNotMatchScheduleSlotDayOfWeek()
                throws Exception {
            RecurringReservation recurring =
                    createRecurringReservation(FIRST_MONDAY, THIRD_MONDAY);

            postRelease(scheduleSlot.getId(), recurring.getId(), TUESDAY_IN_PERIOD)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(
                            "Occurrence date does not match schedule slot day of week"
                    ));

            assertThat(recurringReservationReleaseRepository.count()).isZero();
        }

        @Test
        void shouldRejectDateInThePast() throws Exception {
            RecurringReservation recurring =
                    createRecurringReservation(FIRST_MONDAY, THIRD_MONDAY);

            postRelease(scheduleSlot.getId(), recurring.getId(), PAST_MONDAY)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(
                            "Occurrence date cannot be in the past"
                    ));

            assertThat(recurringReservationReleaseRepository.count()).isZero();
        }

        @Test
        void shouldReturnNotFoundForUnknownRecurringReservation()
                throws Exception {
            postRelease(scheduleSlot.getId(), 999_999L, SECOND_MONDAY)
                    .andExpect(status().isNotFound());

            assertThat(recurringReservationReleaseRepository.count()).isZero();
        }

        @Test
        void shouldReturnNotFoundWhenRecurringReservationBelongsToAnotherSlot()
                throws Exception {
            RecurringReservation recurring =
                    createRecurringReservation(FIRST_MONDAY, THIRD_MONDAY);
            ScheduleSlot otherSlot = scheduleSlotRepository.save(
                    new ScheduleSlot(
                            sportField,
                            DayOfWeek.MONDAY,
                            LocalTime.of(20, 0),
                            new BigDecimal("150.50")
                    )
            );

            postRelease(otherSlot.getId(), recurring.getId(), SECOND_MONDAY)
                    .andExpect(status().isNotFound());

            assertThat(recurringReservationReleaseRepository.count()).isZero();
        }

        @Test
        void shouldReturnNotFoundForUnknownScheduleSlot() throws Exception {
            RecurringReservation recurring =
                    createRecurringReservation(FIRST_MONDAY, THIRD_MONDAY);

            postRelease(999_999L, recurring.getId(), SECOND_MONDAY)
                    .andExpect(status().isNotFound());

            assertThat(recurringReservationReleaseRepository.count()).isZero();
        }

        @Test
        void shouldRejectMissingOccurrenceDate() throws Exception {
            RecurringReservation recurring =
                    createRecurringReservation(FIRST_MONDAY, THIRD_MONDAY);

            postReleaseBody(
                    scheduleSlot.getId(),
                    recurring.getId(),
                    "{}"
            )
                    .andExpect(status().isBadRequest());

            assertThat(recurringReservationReleaseRepository.count()).isZero();
        }

        @Test
        void shouldRejectMalformedOccurrenceDate() throws Exception {
            RecurringReservation recurring =
                    createRecurringReservation(FIRST_MONDAY, THIRD_MONDAY);

            postReleaseBody(
                    scheduleSlot.getId(),
                    recurring.getId(),
                    "{\"occurrenceDate\": \"not-a-date\"}"
            )
                    .andExpect(status().isBadRequest());

            assertThat(recurringReservationReleaseRepository.count()).isZero();
        }
    }

    @Nested
    @DisplayName("Effect on occupancy")
    class OccupancyEffect {

        @Test
        void shouldFreeOnlyTheReleasedDate() throws Exception {
            RecurringReservation recurring =
                    createRecurringReservation(FIRST_MONDAY, THIRD_MONDAY);

            assertThat(slotOccupancyService.isOccupied(
                    scheduleSlot.getId(), SECOND_MONDAY)).isTrue();

            releaseOccurrence(recurring.getId(), SECOND_MONDAY);

            assertThat(slotOccupancyService.isOccupied(
                    scheduleSlot.getId(), SECOND_MONDAY)).isFalse();
            assertThat(slotOccupancyService.isOccupied(
                    scheduleSlot.getId(), FIRST_MONDAY)).isTrue();
            assertThat(slotOccupancyService.isOccupied(
                    scheduleSlot.getId(), THIRD_MONDAY)).isTrue();
        }

        @Test
        void shouldExcludeReleasedDateFromBatchOccupancy() {
            RecurringReservation recurring =
                    createRecurringReservation(FIRST_MONDAY, THIRD_MONDAY);

            releaseOccurrence(recurring.getId(), SECOND_MONDAY);

            assertThat(slotOccupancyService.findOccupiedScheduleSlotIds(
                    sportField.getId(), SECOND_MONDAY)).isEmpty();
            assertThat(slotOccupancyService.findOccupiedScheduleSlotIds(
                    sportField.getId(), THIRD_MONDAY))
                    .containsExactly(scheduleSlot.getId());
        }

        @Test
        void shouldShowReleasedDateAsAvailableInPublicAvailability()
                throws Exception {
            RecurringReservation recurring =
                    createRecurringReservation(FIRST_MONDAY, THIRD_MONDAY);

            releaseOccurrence(recurring.getId(), SECOND_MONDAY);

            getAvailability(SECOND_MONDAY)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].available").value(true));
            getAvailability(THIRD_MONDAY)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].available").value(false));
        }

        @Test
        void shouldAllowReservationOnReleasedDateButNotOnOtherOccurrences() {
            RecurringReservation recurring =
                    createRecurringReservation(FIRST_MONDAY, THIRD_MONDAY);

            releaseOccurrence(recurring.getId(), SECOND_MONDAY);

            createOnlineReservation(SECOND_MONDAY);

            assertThat(reservationRepository.count()).isEqualTo(1);
            assertThatThrownBy(() -> createOnlineReservation(THIRD_MONDAY))
                    .isInstanceOf(ReservationAlreadyExistsException.class);
            assertThat(reservationRepository.count()).isEqualTo(1);
        }

        @Test
        void shouldKeepRecurringOverlapRuleIndependentOfReleases() {
            RecurringReservation recurring =
                    createRecurringReservation(FIRST_MONDAY, THIRD_MONDAY);

            releaseOccurrence(recurring.getId(), SECOND_MONDAY);

            assertThatThrownBy(() ->
                    createRecurringReservation(SECOND_MONDAY, FOURTH_MONDAY)
            ).isInstanceOf(RecurringReservationConflictException.class);

            assertThat(recurringReservationRepository.count()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Schedule slot lock races")
    class ScheduleSlotLockRaces {

        @Test
        @Timeout(15)
        void reservationWaitsForReleaseAndThenSucceedsOnReleasedDate()
                throws Exception {
            RecurringReservation recurring =
                    createRecurringReservation(FIRST_MONDAY, THIRD_MONDAY);
            CountDownLatch released = new CountDownLatch(1);
            CountDownLatch allowReleaseCommit = new CountDownLatch(1);

            Future<?> releaseResult = executor.submit(() ->
                    inTransaction(() -> {
                        releaseOccurrence(recurring.getId(), SECOND_MONDAY);
                        released.countDown();
                        await(allowReleaseCommit);
                    })
            );

            await(released);

            String waitingConnection = "release-reservation-waiter";
            Future<?> reservationResult = executor.submit(() ->
                    inNamedTransaction(
                            waitingConnection,
                            () -> createOnlineReservation(SECOND_MONDAY)
                    )
            );

            awaitConnectionWaitingForLock(waitingConnection);
            allowReleaseCommit.countDown();

            releaseResult.get(CONCURRENCY_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            reservationResult.get(CONCURRENCY_TIMEOUT.toSeconds(), TimeUnit.SECONDS);

            assertThat(recurringReservationReleaseRepository.count()).isEqualTo(1);
            assertThat(reservationRepository.count()).isEqualTo(1);
        }

        @Test
        @Timeout(15)
        void secondReleaseOfTheSameDateIsRejectedAfterWaitingForTheLock()
                throws Exception {
            RecurringReservation recurring =
                    createRecurringReservation(FIRST_MONDAY, THIRD_MONDAY);
            CountDownLatch released = new CountDownLatch(1);
            CountDownLatch allowReleaseCommit = new CountDownLatch(1);

            Future<?> firstRelease = executor.submit(() ->
                    inTransaction(() -> {
                        releaseOccurrence(recurring.getId(), SECOND_MONDAY);
                        released.countDown();
                        await(allowReleaseCommit);
                    })
            );

            await(released);

            String waitingConnection = "release-release-waiter";
            Future<?> secondRelease = executor.submit(() ->
                    inNamedTransaction(
                            waitingConnection,
                            () -> releaseOccurrence(
                                    recurring.getId(),
                                    SECOND_MONDAY
                            )
                    )
            );

            awaitConnectionWaitingForLock(waitingConnection);
            allowReleaseCommit.countDown();

            firstRelease.get(CONCURRENCY_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            assertFutureFailedWith(
                    secondRelease,
                    RecurringReservationConflictException.class
            );

            assertThat(recurringReservationReleaseRepository.count()).isEqualTo(1);
        }
    }

    private ResultActions postRelease(
            Long scheduleSlotId,
            Long recurringReservationId,
            LocalDate occurrenceDate
    ) throws Exception {
        return postReleaseBody(
                scheduleSlotId,
                recurringReservationId,
                "{\"occurrenceDate\": \"" + occurrenceDate + "\"}"
        );
    }

    private ResultActions postReleaseBody(
            Long scheduleSlotId,
            Long recurringReservationId,
            String body
    ) throws Exception {
        return mockMvc.perform(post(
                        RELEASES_PATH,
                        establishment.getId(),
                        sportField.getId(),
                        scheduleSlotId,
                        recurringReservationId
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body));
    }

    private ResultActions getAvailability(LocalDate date) throws Exception {
        return mockMvc.perform(get(
                        AVAILABILITY_PATH,
                        establishment.getId(),
                        sportField.getId()
                )
                        .param("date", date.toString()));
    }

    private RecurringReservation createRecurringReservation(
            LocalDate startDate,
            LocalDate endDate
    ) {
        return recurringReservationService.createRecurringReservation(
                establishment.getId(),
                sportField.getId(),
                scheduleSlot.getId(),
                organizer.getId(),
                organizer.getName(),
                organizer.getPhone(),
                startDate,
                endDate
        );
    }

    private void releaseOccurrence(
            Long recurringReservationId,
            LocalDate occurrenceDate
    ) {
        recurringReservationService.releaseOccurrence(
                establishment.getId(),
                sportField.getId(),
                scheduleSlot.getId(),
                recurringReservationId,
                occurrenceDate
        );
    }

    private void createOnlineReservation(LocalDate reservationDate) {
        reservationService.createOnlineReservation(
                establishment.getId(),
                sportField.getId(),
                scheduleSlot.getId(),
                organizer.getId(),
                reservationDate
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
                    Instant.parse("2026-10-19T21:30:00Z"),
                    SAO_PAULO
            );
        }
    }
}
