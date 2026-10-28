package br.dev.guisleri.agendaarena.reservation;

import br.dev.guisleri.agendaarena.establishment.entity.Establishment;
import br.dev.guisleri.agendaarena.establishment.repository.EstablishmentRepository;
import br.dev.guisleri.agendaarena.reservation.entity.RecurringReservation;
import br.dev.guisleri.agendaarena.reservation.exception.RecurringReservationConflictException;
import br.dev.guisleri.agendaarena.reservation.exception.ReservationAlreadyExistsException;
import br.dev.guisleri.agendaarena.reservation.repository.RecurringReservationRepository;
import br.dev.guisleri.agendaarena.reservation.repository.ReservationRepository;
import br.dev.guisleri.agendaarena.reservation.service.RecurringReservationService;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(RecurringReservationIntegrationTests.TestClockConfiguration.class)
class RecurringReservationIntegrationTests {

    private static final String RECURRING_RESERVATIONS_PATH =
            "/establishments/{establishmentId}/sports-fields/{sportFieldId}"
                    + "/schedule-slots/{scheduleSlotId}/recurring-reservations";
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate FIRST_MONDAY = LocalDate.of(2026, 10, 26);
    private static final LocalDate SECOND_MONDAY = LocalDate.of(2026, 11, 2);
    private static final LocalDate THIRD_MONDAY = LocalDate.of(2026, 11, 9);
    private static final LocalDate FOURTH_MONDAY = LocalDate.of(2026, 11, 16);
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
    private ReservationRepository reservationRepository;

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
        void shouldCreateRecurringReservationWithOrganizer()
                throws Exception {
            postRecurringReservation(
                    scheduleSlot.getId(),
                    organizer.getId(),
                    "Responsavel pela turma",
                    "(11) 99999-1234",
                    FIRST_MONDAY,
                    THIRD_MONDAY
            )
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNumber())
                    .andExpect(jsonPath("$.scheduleSlotId")
                            .value(scheduleSlot.getId().intValue()))
                    .andExpect(jsonPath("$.organizerId")
                            .value(organizer.getId().intValue()))
                    .andExpect(jsonPath("$.responsibleName")
                            .value("Responsavel pela turma"))
                    .andExpect(jsonPath("$.responsiblePhone")
                            .value("(11) 99999-1234"))
                    .andExpect(jsonPath("$.startDate").value("2026-10-26"))
                    .andExpect(jsonPath("$.endDate").value("2026-11-09"));

            RecurringReservation persisted =
                    recurringReservationRepository.findAll().get(0);

            assertThat(recurringReservationRepository.count()).isEqualTo(1);
            assertThat(persisted.getScheduleSlot().getId())
                    .isEqualTo(scheduleSlot.getId());
            assertThat(persisted.getOrganizer().getId())
                    .isEqualTo(organizer.getId());
            assertThat(persisted.getResponsibleName())
                    .isEqualTo("Responsavel pela turma");
            assertThat(persisted.getResponsiblePhone())
                    .isEqualTo("(11) 99999-1234");
            assertThat(persisted.getStartDate()).isEqualTo(FIRST_MONDAY);
            assertThat(persisted.getEndDate()).isEqualTo(THIRD_MONDAY);
        }

        @Test
        void shouldCreateRecurringReservationWithoutOrganizer()
                throws Exception {
            mockMvc.perform(post(
                            RECURRING_RESERVATIONS_PATH,
                            establishment.getId(),
                            sportField.getId(),
                            scheduleSlot.getId()
                    )
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "responsibleName": "Responsavel legado",
                                      "responsiblePhone": "11999991234",
                                      "startDate": "2026-10-26",
                                      "endDate": "2026-11-09"
                                    }
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNumber())
                    .andExpect(jsonPath("$.organizerId").value(nullValue()))
                    .andExpect(jsonPath("$.endDate").value("2026-11-09"));

            RecurringReservation persisted =
                    recurringReservationRepository.findAll().get(0);

            assertThat(persisted.getOrganizer()).isNull();
            assertThat(persisted.getEndDate()).isEqualTo(THIRD_MONDAY);
        }

        @Test
        void shouldCreateRecurringReservationWithoutEndDate()
                throws Exception {
            mockMvc.perform(post(
                            RECURRING_RESERVATIONS_PATH,
                            establishment.getId(),
                            sportField.getId(),
                            scheduleSlot.getId()
                    )
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "organizerId": null,
                                      "responsibleName": "Carlos Eduardo",
                                      "responsiblePhone": "(11) 98765-4321",
                                      "startDate": "2026-10-26"
                                    }
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNumber())
                    .andExpect(jsonPath("$.scheduleSlotId")
                            .value(scheduleSlot.getId().intValue()))
                    .andExpect(jsonPath("$.organizerId").value(nullValue()))
                    .andExpect(jsonPath("$.responsibleName")
                            .value("Carlos Eduardo"))
                    .andExpect(jsonPath("$.responsiblePhone")
                            .value("(11) 98765-4321"))
                    .andExpect(jsonPath("$.startDate").value("2026-10-26"))
                    .andExpect(jsonPath("$.endDate").value(nullValue()));

            RecurringReservation persisted =
                    recurringReservationRepository.findAll().get(0);

            assertThat(persisted.getEndDate()).isNull();
        }

        @Test
        void shouldReturnConflictWhenRecurringReservationOverlaps()
                throws Exception {
            postRecurringReservation(
                    scheduleSlot.getId(),
                    organizer.getId(),
                    "Primeiro responsavel",
                    "11999991234",
                    FIRST_MONDAY,
                    THIRD_MONDAY
            ).andExpect(status().isCreated());

            postRecurringReservation(
                    scheduleSlot.getId(),
                    organizer.getId(),
                    "Segundo responsavel",
                    "11988887777",
                    SECOND_MONDAY,
                    FOURTH_MONDAY
            )
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.error").value("Conflict"));

            assertThat(recurringReservationRepository.count()).isEqualTo(1);
        }

        @Test
        void shouldReturnConflictWhenConfirmedReservationExists()
                throws Exception {
            createOnlineReservation(SECOND_MONDAY);

            postRecurringReservation(
                    scheduleSlot.getId(),
                    organizer.getId(),
                    "Responsavel pela turma",
                    "11999991234",
                    FIRST_MONDAY,
                    THIRD_MONDAY
            )
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.error").value("Conflict"));

            assertThat(reservationRepository.count()).isEqualTo(1);
            assertThat(recurringReservationRepository.count()).isZero();
        }

        @Test
        void shouldRejectStartDateThatDoesNotMatchScheduleSlotDayOfWeek()
                throws Exception {
            postRecurringReservation(
                    scheduleSlot.getId(),
                    organizer.getId(),
                    "Responsavel pela turma",
                    "11999991234",
                    FIRST_MONDAY.plusDays(1),
                    THIRD_MONDAY
            )
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("Bad Request"))
                    .andExpect(jsonPath("$.message").value(
                            "Start date must match the schedule slot day of week"
                    ));

            assertThat(recurringReservationRepository.count()).isZero();
        }

        @Test
        void shouldReturnBadRequestWhenEndDateIsBeforeStartDate()
                throws Exception {
            postRecurringReservation(
                    scheduleSlot.getId(),
                    organizer.getId(),
                    "Responsavel pela turma",
                    "11999991234",
                    FIRST_MONDAY,
                    FIRST_MONDAY.minusDays(1)
            )
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("Bad Request"));

            assertThat(recurringReservationRepository.count()).isZero();
        }

        @Test
        void shouldReturnNotFoundWhenOrganizerDoesNotExist() throws Exception {
            postRecurringReservation(
                    scheduleSlot.getId(),
                    999999L,
                    "Responsavel pela turma",
                    "11999991234",
                    FIRST_MONDAY,
                    THIRD_MONDAY
            )
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.error").value("Not Found"));

            assertThat(recurringReservationRepository.count()).isZero();
        }

        @Test
        void shouldReturnNotFoundForUnknownScheduleSlot() throws Exception {
            postRecurringReservation(
                    Long.MAX_VALUE,
                    organizer.getId(),
                    "Responsavel pela turma",
                    "11999991234",
                    FIRST_MONDAY,
                    THIRD_MONDAY
            )
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.error").value("Not Found"));

            assertThat(recurringReservationRepository.count()).isZero();
        }

        @Test
        void shouldReturnNotFoundWhenScheduleSlotBelongsToAnotherSportField()
                throws Exception {
            SportField anotherSportField = sportFieldRepository.save(
                    new SportField(
                            establishment,
                            "Campo 2",
                            SportType.SOCIETY
                    )
            );

            mockMvc.perform(post(
                            RECURRING_RESERVATIONS_PATH,
                            establishment.getId(),
                            anotherSportField.getId(),
                            scheduleSlot.getId()
                    )
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "organizerId": %d,
                                      "responsibleName": "Responsavel pela turma",
                                      "responsiblePhone": "11999991234",
                                      "startDate": "2026-10-26",
                                      "endDate": "2026-11-09"
                                    }
                                    """.formatted(organizer.getId())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.error").value("Not Found"));

            assertThat(recurringReservationRepository.count()).isZero();
        }

        @Test
        void shouldRejectMalformedDate() throws Exception {
            mockMvc.perform(post(
                            RECURRING_RESERVATIONS_PATH,
                            establishment.getId(),
                            sportField.getId(),
                            scheduleSlot.getId()
                    )
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "responsibleName": "Responsavel",
                                      "responsiblePhone": "11999991234",
                                      "startDate": "26-10-2026"
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("Bad Request"));

            assertThat(recurringReservationRepository.count()).isZero();
        }

        @ParameterizedTest(name = "should reject invalid request: {0}")
        @MethodSource("invalidRequests")
        void shouldRejectInvalidRecurringReservationRequest(
                String scenario,
                String expectedField,
                String requestBody
        ) throws Exception {
            mockMvc.perform(post(
                            RECURRING_RESERVATIONS_PATH,
                            establishment.getId(),
                            sportField.getId(),
                            scheduleSlot.getId()
                    )
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("Bad Request"))
                    .andExpect(jsonPath("$.message").value("Validation failed"))
                    .andExpect(jsonPath("$.errors." + expectedField).isNotEmpty());

            assertThat(recurringReservationRepository.count()).isZero();
        }

        private static Stream<Arguments> invalidRequests() {
            String validFields = """
                    "responsibleName":"Responsavel",
                    "responsiblePhone":"11999991234",
                    "startDate":"2026-10-26"
                    """;

            return Stream.of(
                    Arguments.of(
                            "zero organizerId",
                            "organizerId",
                            "{" + validFields + ",\"organizerId\":0}"
                    ),
                    Arguments.of(
                            "blank responsibleName",
                            "responsibleName",
                            """
                                    {
                                      "responsibleName": "   ",
                                      "responsiblePhone": "11999991234",
                                      "startDate": "2026-10-26"
                                    }
                                    """
                    ),
                    Arguments.of(
                            "responsibleName longer than 120 characters",
                            "responsibleName",
                            """
                                    {
                                      "responsibleName": "%s",
                                      "responsiblePhone": "11999991234",
                                      "startDate": "2026-10-26"
                                    }
                                    """.formatted("a".repeat(121))
                    ),
                    Arguments.of(
                            "blank responsiblePhone",
                            "responsiblePhone",
                            """
                                    {
                                      "responsibleName": "Responsavel",
                                      "responsiblePhone": "   ",
                                      "startDate": "2026-10-26"
                                    }
                                    """
                    ),
                    Arguments.of(
                            "responsiblePhone containing letters",
                            "responsiblePhone",
                            """
                                    {
                                      "responsibleName": "Responsavel",
                                      "responsiblePhone": "11 9999A-1234",
                                      "startDate": "2026-10-26"
                                    }
                                    """
                    ),
                    Arguments.of(
                            "responsiblePhone with fewer than 10 digits",
                            "responsiblePhone",
                            """
                                    {
                                      "responsibleName": "Responsavel",
                                      "responsiblePhone": "119999912",
                                      "startDate": "2026-10-26"
                                    }
                                    """
                    ),
                    Arguments.of(
                            "null startDate",
                            "startDate",
                            """
                                    {
                                      "responsibleName": "Responsavel",
                                      "responsiblePhone": "11999991234",
                                      "startDate": null
                                    }
                                    """
                    )
            );
        }
    }

    @Nested
    @DisplayName("Normal conflict rules")
    class NormalConflictRules {

        @Test
        void shouldRejectReservationWhenRecurringReservationCoversDate() {
            createRecurringReservation(FIRST_MONDAY, THIRD_MONDAY);

            assertThatThrownBy(() -> createOnlineReservation(SECOND_MONDAY))
                    .isInstanceOf(ReservationAlreadyExistsException.class);

            assertThat(recurringReservationRepository.count()).isEqualTo(1);
            assertThat(reservationRepository.count()).isZero();
        }

        @Test
        void shouldRejectManualReservationWhenRecurringReservationCoversDate() {
            createRecurringReservation(FIRST_MONDAY, THIRD_MONDAY);

            assertThatThrownBy(() -> createManualReservation(SECOND_MONDAY))
                    .isInstanceOf(ReservationAlreadyExistsException.class);

            assertThat(recurringReservationRepository.count()).isEqualTo(1);
            assertThat(reservationRepository.count()).isZero();
        }

        @Test
        void shouldRejectReservationOnRecurringReservationEndDate() {
            createRecurringReservation(FIRST_MONDAY, THIRD_MONDAY);

            assertThatThrownBy(() -> createOnlineReservation(THIRD_MONDAY))
                    .isInstanceOf(ReservationAlreadyExistsException.class);

            assertThat(recurringReservationRepository.count()).isEqualTo(1);
            assertThat(reservationRepository.count()).isZero();
        }

        @Test
        void shouldRejectReservationOnRecurringReservationStartDate() {
            createRecurringReservation(FIRST_MONDAY, THIRD_MONDAY);

            assertThatThrownBy(() -> createOnlineReservation(FIRST_MONDAY))
                    .isInstanceOf(ReservationAlreadyExistsException.class);

            assertThat(recurringReservationRepository.count()).isEqualTo(1);
            assertThat(reservationRepository.count()).isZero();
        }

        @Test
        void shouldAllowReservationOutsideRecurringReservationPeriod() {
            createRecurringReservation(FIRST_MONDAY, SECOND_MONDAY);

            createOnlineReservation(THIRD_MONDAY);

            assertThat(recurringReservationRepository.count()).isEqualTo(1);
            assertThat(reservationRepository.count()).isEqualTo(1);
        }

        @Test
        void shouldRejectRecurringReservationWhenConfirmedReservationExists() {
            createOnlineReservation(SECOND_MONDAY);

            assertThatThrownBy(() ->
                    createRecurringReservation(FIRST_MONDAY, THIRD_MONDAY)
            ).isInstanceOf(RecurringReservationConflictException.class);

            assertThat(reservationRepository.count()).isEqualTo(1);
            assertThat(recurringReservationRepository.count()).isZero();
        }

        @Test
        void shouldRejectOverlappingRecurringReservations() {
            createRecurringReservation(FIRST_MONDAY, null);

            assertThatThrownBy(() ->
                    createRecurringReservation(SECOND_MONDAY, FOURTH_MONDAY)
            ).isInstanceOf(RecurringReservationConflictException.class);

            assertThat(recurringReservationRepository.count()).isEqualTo(1);
            assertThat(reservationRepository.count()).isZero();
        }
    }

    @Nested
    @DisplayName("Schedule slot lock races")
    class ScheduleSlotLockRaces {

        @Test
        @Timeout(15)
        void recurringReservationWinsRaceAndOnlineReservationIsRejected()
                throws Exception {
            CountDownLatch recurringSaved = new CountDownLatch(1);
            CountDownLatch allowRecurringCommit = new CountDownLatch(1);

            Future<?> recurringResult = executor.submit(() ->
                    inTransaction(() -> {
                        createRecurringReservation(FIRST_MONDAY, THIRD_MONDAY);
                        recurringSaved.countDown();
                        await(allowRecurringCommit);
                    })
            );

            await(recurringSaved);

            String losingConnection = "aa009-reservation-loser";
            Future<?> reservationResult = executor.submit(() ->
                    inNamedTransaction(
                            losingConnection,
                            () -> createOnlineReservation(SECOND_MONDAY)
                    )
            );

            awaitConnectionWaitingForLock(losingConnection);
            allowRecurringCommit.countDown();

            recurringResult.get(CONCURRENCY_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            assertFutureFailedWith(
                    reservationResult,
                    ReservationAlreadyExistsException.class
            );

            assertThat(recurringReservationRepository.count()).isEqualTo(1);
            assertThat(reservationRepository.count()).isZero();
            assertThat(totalOccupations()).isEqualTo(1);
        }

        @Test
        @Timeout(15)
        void onlineReservationWinsRaceAndRecurringReservationIsRejected()
                throws Exception {
            CountDownLatch reservationSaved = new CountDownLatch(1);
            CountDownLatch allowReservationCommit = new CountDownLatch(1);

            Future<?> reservationResult = executor.submit(() ->
                    inTransaction(() -> {
                        createOnlineReservation(SECOND_MONDAY);
                        reservationSaved.countDown();
                        await(allowReservationCommit);
                    })
            );

            await(reservationSaved);

            String losingConnection = "aa009-recurring-loser";
            Future<?> recurringResult = executor.submit(() ->
                    inNamedTransaction(
                            losingConnection,
                            () -> createRecurringReservation(
                                    FIRST_MONDAY,
                                    THIRD_MONDAY
                            )
                    )
            );

            awaitConnectionWaitingForLock(losingConnection);
            allowReservationCommit.countDown();

            reservationResult.get(CONCURRENCY_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            assertFutureFailedWith(
                    recurringResult,
                    RecurringReservationConflictException.class
            );

            assertThat(reservationRepository.count()).isEqualTo(1);
            assertThat(recurringReservationRepository.count()).isZero();
            assertThat(totalOccupations()).isEqualTo(1);
        }
    }

    private ResultActions postRecurringReservation(
            Long scheduleSlotId,
            Long organizerId,
            String responsibleName,
            String responsiblePhone,
            LocalDate startDate,
            LocalDate endDate
    ) throws Exception {
        String serializedOrganizerId = organizerId == null
                ? "null"
                : organizerId.toString();
        String serializedEndDate = endDate == null
                ? "null"
                : "\"" + endDate + "\"";

        return mockMvc.perform(post(
                        RECURRING_RESERVATIONS_PATH,
                        establishment.getId(),
                        sportField.getId(),
                        scheduleSlotId
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizerId": %s,
                                  "responsibleName": "%s",
                                  "responsiblePhone": "%s",
                                  "startDate": "%s",
                                  "endDate": %s
                                }
                                """.formatted(
                                serializedOrganizerId,
                                responsibleName,
                                responsiblePhone,
                                startDate,
                                serializedEndDate
                        )));
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

    private void createManualReservation(LocalDate reservationDate) {
        reservationService.createManualReservation(
                establishment.getId(),
                sportField.getId(),
                scheduleSlot.getId(),
                reservationDate,
                "Cliente avulso",
                "11988887777"
        );
    }

    private void createRecurringReservation(
            LocalDate startDate,
            LocalDate endDate
    ) {
        recurringReservationService.createRecurringReservation(
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

    private long totalOccupations() {
        return reservationRepository.count()
                + recurringReservationRepository.count();
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
