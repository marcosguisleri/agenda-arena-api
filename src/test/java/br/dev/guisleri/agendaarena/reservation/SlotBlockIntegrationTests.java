package br.dev.guisleri.agendaarena.reservation;

import br.dev.guisleri.agendaarena.establishment.entity.Establishment;
import br.dev.guisleri.agendaarena.establishment.repository.EstablishmentRepository;
import br.dev.guisleri.agendaarena.reservation.entity.RecurringReservation;
import br.dev.guisleri.agendaarena.reservation.entity.Reservation;
import br.dev.guisleri.agendaarena.reservation.entity.SlotBlock;
import br.dev.guisleri.agendaarena.reservation.exception.RecurringReservationConflictException;
import br.dev.guisleri.agendaarena.reservation.exception.SlotBlockConflictException;
import br.dev.guisleri.agendaarena.reservation.repository.RecurringReservationReleaseRepository;
import br.dev.guisleri.agendaarena.reservation.repository.RecurringReservationRepository;
import br.dev.guisleri.agendaarena.reservation.repository.ReservationRepository;
import br.dev.guisleri.agendaarena.reservation.repository.SlotBlockRepository;
import br.dev.guisleri.agendaarena.reservation.service.RecurringReservationService;
import br.dev.guisleri.agendaarena.reservation.service.ReservationService;
import br.dev.guisleri.agendaarena.reservation.service.SlotBlockService;
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
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(SlotBlockIntegrationTests.TestClockConfiguration.class)
class SlotBlockIntegrationTests {

    private static final String BLOCKS_PATH =
            "/establishments/{establishmentId}/sports-fields/{sportFieldId}"
                    + "/schedule-slots/{scheduleSlotId}/blocks";
    private static final String BLOCK_PATH = BLOCKS_PATH + "/{slotBlockId}";
    private static final String CANCEL_PATH =
            "/establishments/{establishmentId}/sports-fields/{sportFieldId}"
                    + "/schedule-slots/{scheduleSlotId}/reservations/{reservationId}/cancel";
    private static final String AVAILABILITY_PATH =
            "/establishments/{establishmentId}/sports-fields/{sportFieldId}/availability";
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    // Relógio fixo: segunda-feira 2026-10-19, 18:30 em São Paulo (o slot começa às 19:00).
    private static final LocalDate PAST_MONDAY = LocalDate.of(2026, 10, 12);
    private static final LocalDate FIRST_MONDAY = LocalDate.of(2026, 10, 26);
    private static final LocalDate SECOND_MONDAY = LocalDate.of(2026, 11, 2);
    private static final LocalDate THIRD_MONDAY = LocalDate.of(2026, 11, 9);
    private static final LocalDate FOURTH_MONDAY = LocalDate.of(2026, 11, 16);
    private static final LocalDate TUESDAY = LocalDate.of(2026, 10, 27);
    private static final Duration CONCURRENCY_TIMEOUT = Duration.ofSeconds(10);

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SlotBlockService slotBlockService;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private RecurringReservationService recurringReservationService;

    @Autowired
    private SlotBlockRepository slotBlockRepository;

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
        void shouldCreateBlockWithReason() throws Exception {
            postBlock(scheduleSlot.getId(), FIRST_MONDAY, "Manutencao da grama")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNumber())
                    .andExpect(jsonPath("$.scheduleSlotId")
                            .value(scheduleSlot.getId().intValue()))
                    .andExpect(jsonPath("$.blockDate").value("2026-10-26"))
                    .andExpect(jsonPath("$.reason").value("Manutencao da grama"))
                    .andExpect(jsonPath("$.createdAt").isNotEmpty());

            assertThat(slotBlockRepository.count()).isEqualTo(1);
        }

        @Test
        void shouldCreateBlockWithoutReason() throws Exception {
            postBlockBody(scheduleSlot.getId(), "{\"blockDate\": \"2026-10-26\"}")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.reason").value(nullValue()));

            assertThat(slotBlockRepository.count()).isEqualTo(1);
        }

        @Test
        void shouldReturnConflictWhenDateIsAlreadyBlocked() throws Exception {
            postBlock(scheduleSlot.getId(), FIRST_MONDAY, "Evento")
                    .andExpect(status().isCreated());

            postBlock(scheduleSlot.getId(), FIRST_MONDAY, "Outro motivo")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.message").value(
                            "Schedule slot is already blocked for this date"
                    ));

            assertThat(slotBlockRepository.count()).isEqualTo(1);
        }

        @Test
        void shouldReturnConflictWhenDateHasConfirmedReservation()
                throws Exception {
            createOnlineReservation(FIRST_MONDAY);

            postBlock(scheduleSlot.getId(), FIRST_MONDAY, "Evento")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(
                            "Schedule slot is already occupied by a reservation on this date"
                    ));

            assertThat(slotBlockRepository.count()).isZero();
            assertThat(reservationRepository.count()).isEqualTo(1);
        }

        @Test
        void shouldReturnConflictWhenDateIsCoveredByRecurringReservation()
                throws Exception {
            createRecurringReservation(FIRST_MONDAY, THIRD_MONDAY);

            postBlock(scheduleSlot.getId(), SECOND_MONDAY, "Evento")
                    .andExpect(status().isConflict());

            assertThat(slotBlockRepository.count()).isZero();
        }

        @Test
        void shouldAllowBlockingAReleasedRecurringOccurrence() throws Exception {
            RecurringReservation recurring =
                    createRecurringReservation(FIRST_MONDAY, THIRD_MONDAY);
            recurringReservationService.releaseOccurrence(
                    establishment.getId(),
                    sportField.getId(),
                    scheduleSlot.getId(),
                    recurring.getId(),
                    SECOND_MONDAY
            );

            postBlock(scheduleSlot.getId(), SECOND_MONDAY, "Campeonato")
                    .andExpect(status().isCreated());

            assertThat(slotBlockRepository.count()).isEqualTo(1);
        }

        @Test
        void shouldRejectDateInThePast() throws Exception {
            postBlock(scheduleSlot.getId(), PAST_MONDAY, "Evento")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message")
                            .value("Block date cannot be in the past"));

            assertThat(slotBlockRepository.count()).isZero();
        }

        @Test
        void shouldRejectDateThatDoesNotMatchScheduleSlotDayOfWeek()
                throws Exception {
            postBlock(scheduleSlot.getId(), TUESDAY, "Evento")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(
                            "Block date does not match schedule slot day of week"
                    ));

            assertThat(slotBlockRepository.count()).isZero();
        }

        @Test
        void shouldRejectMissingBlockDate() throws Exception {
            postBlockBody(scheduleSlot.getId(), "{\"reason\": \"Evento\"}")
                    .andExpect(status().isBadRequest());

            assertThat(slotBlockRepository.count()).isZero();
        }

        @Test
        void shouldRejectMalformedBlockDate() throws Exception {
            postBlockBody(scheduleSlot.getId(), "{\"blockDate\": \"not-a-date\"}")
                    .andExpect(status().isBadRequest());

            assertThat(slotBlockRepository.count()).isZero();
        }

        @Test
        void shouldRejectTooLongReason() throws Exception {
            postBlock(scheduleSlot.getId(), FIRST_MONDAY, "x".repeat(256))
                    .andExpect(status().isBadRequest());

            assertThat(slotBlockRepository.count()).isZero();
        }

        @Test
        void shouldReturnNotFoundForUnknownScheduleSlot() throws Exception {
            postBlock(999_999L, FIRST_MONDAY, "Evento")
                    .andExpect(status().isNotFound());

            assertThat(slotBlockRepository.count()).isZero();
        }

        @Test
        void shouldDeleteBlock() throws Exception {
            SlotBlock block = createBlock(FIRST_MONDAY);

            deleteBlock(scheduleSlot.getId(), block.getId())
                    .andExpect(status().isNoContent());

            assertThat(slotBlockRepository.count()).isZero();

            deleteBlock(scheduleSlot.getId(), block.getId())
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldReturnNotFoundWhenDeletingBlockOfAnotherSlot() throws Exception {
            SlotBlock block = createBlock(FIRST_MONDAY);
            ScheduleSlot otherSlot = scheduleSlotRepository.save(
                    new ScheduleSlot(
                            sportField,
                            DayOfWeek.MONDAY,
                            LocalTime.of(20, 0),
                            new BigDecimal("150.50")
                    )
            );

            deleteBlock(otherSlot.getId(), block.getId())
                    .andExpect(status().isNotFound());

            assertThat(slotBlockRepository.count()).isEqualTo(1);
        }

        @Test
        void shouldListUpcomingBlocksInDateOrder() throws Exception {
            createBlock(SECOND_MONDAY);
            createBlock(FIRST_MONDAY);

            getBlocks(scheduleSlot.getId(), null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].blockDate").value("2026-10-26"))
                    .andExpect(jsonPath("$[1].blockDate").value("2026-11-02"));
        }

        @Test
        void shouldFilterBlocksByFromDateAndSlot() throws Exception {
            ScheduleSlot otherSlot = scheduleSlotRepository.save(
                    new ScheduleSlot(
                            sportField,
                            DayOfWeek.MONDAY,
                            LocalTime.of(20, 0),
                            new BigDecimal("150.50")
                    )
            );
            createBlock(FIRST_MONDAY);
            createBlock(SECOND_MONDAY);
            slotBlockService.createBlock(
                    establishment.getId(),
                    sportField.getId(),
                    otherSlot.getId(),
                    SECOND_MONDAY,
                    null
            );

            getBlocks(scheduleSlot.getId(), SECOND_MONDAY)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].blockDate").value("2026-11-02"))
                    .andExpect(jsonPath("$[0].scheduleSlotId")
                            .value(scheduleSlot.getId().intValue()));
        }
    }

    @Nested
    @DisplayName("Effect on occupancy")
    class OccupancyEffect {

        @Test
        void shouldMakeBlockedDateUnavailableAndOnlyThatDate() throws Exception {
            SlotBlock block = createBlock(FIRST_MONDAY);

            getAvailability(FIRST_MONDAY)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].available").value(false));
            getAvailability(SECOND_MONDAY)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].available").value(true));

            deleteBlock(scheduleSlot.getId(), block.getId())
                    .andExpect(status().isNoContent());

            getAvailability(FIRST_MONDAY)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].available").value(true));
        }

        @Test
        void shouldRejectOnlineAndManualReservationsOnBlockedDate() {
            createBlock(FIRST_MONDAY);

            assertThatThrownBy(() -> createOnlineReservation(FIRST_MONDAY))
                    .isInstanceOf(SlotBlockConflictException.class)
                    .hasMessage("Schedule slot is blocked for this date");
            assertThatThrownBy(() -> createManualReservation(FIRST_MONDAY))
                    .isInstanceOf(SlotBlockConflictException.class);

            assertThat(reservationRepository.count()).isZero();
        }

        @Test
        void shouldAllowReservationOnAnotherDate() {
            createBlock(FIRST_MONDAY);

            createOnlineReservation(SECOND_MONDAY);

            assertThat(reservationRepository.count()).isEqualTo(1);
        }

        @Test
        void shouldAllowReservationAfterBlockIsRemoved() throws Exception {
            SlotBlock block = createBlock(FIRST_MONDAY);

            deleteBlock(scheduleSlot.getId(), block.getId())
                    .andExpect(status().isNoContent());

            createOnlineReservation(FIRST_MONDAY);

            assertThat(reservationRepository.count()).isEqualTo(1);
        }

        @Test
        void shouldRejectRecurringReservationCoveringBlockedDate() {
            createBlock(SECOND_MONDAY);

            assertThatThrownBy(() ->
                    createRecurringReservation(FIRST_MONDAY, THIRD_MONDAY)
            ).isInstanceOf(RecurringReservationConflictException.class);
            assertThatThrownBy(() ->
                    createRecurringReservation(FIRST_MONDAY, null)
            ).isInstanceOf(RecurringReservationConflictException.class);

            assertThat(recurringReservationRepository.count()).isZero();
        }

        @Test
        void shouldAllowRecurringReservationOutsideBlockedDate() {
            createBlock(FOURTH_MONDAY);

            createRecurringReservation(FIRST_MONDAY, THIRD_MONDAY);

            assertThat(recurringReservationRepository.count()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Cancel and keep the slot blocked")
    class CancelAndKeepBlocked {

        @Test
        void shouldCancelReservationAndBlockTheDate() throws Exception {
            Reservation reservation = createOnlineReservation(FIRST_MONDAY);

            postCancel(reservation.getId(), true)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"));

            assertThat(slotBlockRepository.count()).isEqualTo(1);
            SlotBlock block = slotBlockRepository.findAll().get(0);
            assertThat(block.getBlockDate()).isEqualTo(FIRST_MONDAY);
            assertThat(block.getReason())
                    .isEqualTo("Reservation cancelled by the administrator");

            getAvailability(FIRST_MONDAY)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].available").value(false));
            assertThatThrownBy(() -> createManualReservation(FIRST_MONDAY))
                    .isInstanceOf(SlotBlockConflictException.class);
        }

        @Test
        void shouldNotBlockWhenKeepBlockedIsNotRequested() throws Exception {
            Reservation reservation = createOnlineReservation(FIRST_MONDAY);

            postCancel(reservation.getId(), false)
                    .andExpect(status().isOk());

            assertThat(slotBlockRepository.count()).isZero();
            getAvailability(FIRST_MONDAY)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].available").value(true));
        }

        @Test
        void shouldNotCreateBlockWhenReservationWasAlreadyCancelled()
                throws Exception {
            Reservation reservation = createOnlineReservation(FIRST_MONDAY);
            postCancel(reservation.getId(), false).andExpect(status().isOk());

            postCancel(reservation.getId(), true)
                    .andExpect(status().isConflict());

            assertThat(slotBlockRepository.count()).isZero();
        }
    }

    @Nested
    @DisplayName("Schedule slot lock races")
    class ScheduleSlotLockRaces {

        @Test
        @Timeout(15)
        void blockWaitsForReservationAndIsRejected() throws Exception {
            CountDownLatch reservationSaved = new CountDownLatch(1);
            CountDownLatch allowReservationCommit = new CountDownLatch(1);

            Future<?> reservationResult = executor.submit(() ->
                    inTransaction(() -> {
                        createOnlineReservation(FIRST_MONDAY);
                        reservationSaved.countDown();
                        await(allowReservationCommit);
                    })
            );

            await(reservationSaved);

            String waitingConnection = "block-waits-reservation";
            Future<?> blockResult = executor.submit(() ->
                    inNamedTransaction(
                            waitingConnection,
                            () -> createBlock(FIRST_MONDAY)
                    )
            );

            awaitConnectionWaitingForLock(waitingConnection);
            allowReservationCommit.countDown();

            reservationResult.get(CONCURRENCY_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            assertFutureFailedWith(blockResult, SlotBlockConflictException.class);

            assertThat(reservationRepository.count()).isEqualTo(1);
            assertThat(slotBlockRepository.count()).isZero();
        }

        @Test
        @Timeout(15)
        void reservationWaitsForBlockAndIsRejected() throws Exception {
            CountDownLatch blockSaved = new CountDownLatch(1);
            CountDownLatch allowBlockCommit = new CountDownLatch(1);

            Future<?> blockResult = executor.submit(() ->
                    inTransaction(() -> {
                        createBlock(FIRST_MONDAY);
                        blockSaved.countDown();
                        await(allowBlockCommit);
                    })
            );

            await(blockSaved);

            String waitingConnection = "reservation-waits-block";
            Future<?> reservationResult = executor.submit(() ->
                    inNamedTransaction(
                            waitingConnection,
                            () -> createOnlineReservation(FIRST_MONDAY)
                    )
            );

            awaitConnectionWaitingForLock(waitingConnection);
            allowBlockCommit.countDown();

            blockResult.get(CONCURRENCY_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            assertFutureFailedWith(reservationResult, SlotBlockConflictException.class);

            assertThat(slotBlockRepository.count()).isEqualTo(1);
            assertThat(reservationRepository.count()).isZero();
        }

        @Test
        @Timeout(15)
        void secondBlockOfTheSameDateIsRejectedAfterWaitingForTheLock()
                throws Exception {
            CountDownLatch blockSaved = new CountDownLatch(1);
            CountDownLatch allowBlockCommit = new CountDownLatch(1);

            Future<?> firstBlock = executor.submit(() ->
                    inTransaction(() -> {
                        createBlock(FIRST_MONDAY);
                        blockSaved.countDown();
                        await(allowBlockCommit);
                    })
            );

            await(blockSaved);

            String waitingConnection = "block-waits-block";
            Future<?> secondBlock = executor.submit(() ->
                    inNamedTransaction(
                            waitingConnection,
                            () -> createBlock(FIRST_MONDAY)
                    )
            );

            awaitConnectionWaitingForLock(waitingConnection);
            allowBlockCommit.countDown();

            firstBlock.get(CONCURRENCY_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            assertFutureFailedWith(secondBlock, SlotBlockConflictException.class);

            assertThat(slotBlockRepository.count()).isEqualTo(1);
        }
    }

    private ResultActions postBlock(Long scheduleSlotId, LocalDate blockDate, String reason)
            throws Exception {
        return postBlockBody(
                scheduleSlotId,
                "{\"blockDate\": \"" + blockDate + "\", \"reason\": \"" + reason + "\"}"
        );
    }

    private ResultActions postBlockBody(Long scheduleSlotId, String body)
            throws Exception {
        return mockMvc.perform(post(
                        BLOCKS_PATH,
                        establishment.getId(),
                        sportField.getId(),
                        scheduleSlotId
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body));
    }

    private ResultActions deleteBlock(Long scheduleSlotId, Long slotBlockId)
            throws Exception {
        return mockMvc.perform(delete(
                BLOCK_PATH,
                establishment.getId(),
                sportField.getId(),
                scheduleSlotId,
                slotBlockId
        ));
    }

    private ResultActions getBlocks(Long scheduleSlotId, LocalDate from)
            throws Exception {
        var request = get(
                BLOCKS_PATH,
                establishment.getId(),
                sportField.getId(),
                scheduleSlotId
        );

        if (from != null) {
            request = request.param("from", from.toString());
        }

        return mockMvc.perform(request);
    }

    private ResultActions postCancel(Long reservationId, boolean keepBlocked)
            throws Exception {
        return mockMvc.perform(post(
                        CANCEL_PATH,
                        establishment.getId(),
                        sportField.getId(),
                        scheduleSlot.getId(),
                        reservationId
                )
                        .param("keepBlocked", String.valueOf(keepBlocked)));
    }

    private ResultActions getAvailability(LocalDate date) throws Exception {
        return mockMvc.perform(get(
                        AVAILABILITY_PATH,
                        establishment.getId(),
                        sportField.getId()
                )
                        .param("date", date.toString()));
    }

    private SlotBlock createBlock(LocalDate blockDate) {
        return slotBlockService.createBlock(
                establishment.getId(),
                sportField.getId(),
                scheduleSlot.getId(),
                blockDate,
                "Manutencao"
        );
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
