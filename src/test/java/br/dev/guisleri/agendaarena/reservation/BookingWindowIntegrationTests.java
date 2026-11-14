package br.dev.guisleri.agendaarena.reservation;

import br.dev.guisleri.agendaarena.establishment.entity.Establishment;
import br.dev.guisleri.agendaarena.establishment.repository.EstablishmentRepository;
import br.dev.guisleri.agendaarena.reservation.exception.ReservationAlreadyExistsException;
import br.dev.guisleri.agendaarena.reservation.repository.ReservationRepository;
import br.dev.guisleri.agendaarena.reservation.service.ReservationService;
import br.dev.guisleri.agendaarena.schedule.entity.ScheduleSlot;
import br.dev.guisleri.agendaarena.schedule.repository.ScheduleSlotRepository;
import br.dev.guisleri.agendaarena.sportfield.entity.SportField;
import br.dev.guisleri.agendaarena.sportfield.model.SportType;
import br.dev.guisleri.agendaarena.sportfield.repository.SportFieldRepository;
import br.dev.guisleri.agendaarena.user.entity.User;
import br.dev.guisleri.agendaarena.user.repository.UserRepository;
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

/**
 * Janela de datas para reservar: online até {@code max-advance-days} à frente e nunca no passado;
 * manual retroativa até {@code manual-max-past-days} atrás e sem limite à frente. Os limites
 * (14 e 30) são fixados aqui; os demais testes usam um limite à frente bem alto.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(BookingWindowIntegrationTests.TestClockConfiguration.class)
class BookingWindowIntegrationTests {

    private static final String BASE_PATH =
            "/establishments/{establishmentId}/sports-fields/{sportFieldId}"
                    + "/schedule-slots/{scheduleSlotId}/reservations";
    private static final String AVAILABILITY_PATH =
            "/establishments/{establishmentId}/sports-fields/{sportFieldId}/availability";
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final Duration CONCURRENCY_TIMEOUT = Duration.ofSeconds(10);

    // Relógio fixo: segunda-feira 2026-10-19, 18:30 em São Paulo.
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 19);
    private static final LocalDate DAY_14 = TODAY.plusDays(14);      // segunda 2026-11-02
    private static final LocalDate DAY_15 = TODAY.plusDays(15);      // terça 2026-11-03
    private static final LocalDate PAST_30 = TODAY.minusDays(30);    // sábado 2026-09-19
    private static final LocalDate PAST_31 = TODAY.minusDays(31);    // sexta 2026-09-18

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ReservationRepository reservationRepository;

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
    private ScheduleSlot mondaySlot;
    private User organizer;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.open-in-view", () -> false);
        registry.add("app.reservation.max-advance-days", () -> 14);
        registry.add("app.reservation.manual-max-past-days", () -> 30);
    }

    @BeforeEach
    void prepareDatabase() {
        executor = Executors.newFixedThreadPool(2);

        reservationRepository.deleteAllInBatch();
        scheduleSlotRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        sportFieldRepository.deleteAllInBatch();
        establishmentRepository.deleteAllInBatch();

        establishment = establishmentRepository.save(new Establishment("Arena Central"));
        sportField = sportFieldRepository.save(
                new SportField(establishment, "Campo 1", SportType.SOCIETY)
        );
        mondaySlot = slot(DayOfWeek.MONDAY, LocalTime.of(19, 0));
        organizer = userRepository.save(
                new User("Maria da Silva", "maria@example.com", "11999991234", "password-hash")
        );
    }

    @AfterEach
    void stopExecutor() throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    @Nested
    @DisplayName("Reserva online: de agora até 14 dias à frente")
    class OnlineWindow {

        @Test
        void acceptsTheLastDayOfTheWindow() throws Exception {
            online(mondaySlot, DAY_14)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.reservationDate").value("2026-11-02"));

            assertThat(reservationRepository.count()).isEqualTo(1);
        }

        @Test
        void rejectsTheDayAfterTheWindow() throws Exception {
            ScheduleSlot tuesdaySlot = slot(DayOfWeek.TUESDAY, LocalTime.of(19, 0));

            online(tuesdaySlot, DAY_15)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message")
                            .value("Reservation date is too far in the future"));

            assertThat(reservationRepository.count()).isZero();
        }

        @Test
        void stillRejectsYesterday() throws Exception {
            ScheduleSlot sundaySlot = slot(DayOfWeek.SUNDAY, LocalTime.of(19, 0));

            online(sundaySlot, TODAY.minusDays(1))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message")
                            .value("Reservation date cannot be in the past"));

            assertThat(reservationRepository.count()).isZero();
        }

        @Test
        void stillRejectsAnHourThatAlreadyStartedToday() throws Exception {
            ScheduleSlot earlierSlot = slot(DayOfWeek.MONDAY, LocalTime.of(18, 0));

            online(earlierSlot, TODAY)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message")
                            .value("Reservation time cannot be in the past"));

            assertThat(reservationRepository.count()).isZero();
        }
    }

    @Nested
    @DisplayName("Reserva manual: retroativa até 30 dias, sem limite à frente")
    class ManualWindow {

        @Test
        void acceptsAnHourThatAlreadyStartedToday() throws Exception {
            ScheduleSlot earlierSlot = slot(DayOfWeek.MONDAY, LocalTime.of(18, 0));

            manual(earlierSlot, TODAY)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.origin").value("MANUAL"))
                    .andExpect(jsonPath("$.status").value("CONFIRMED"))
                    .andExpect(jsonPath("$.reservationDate").value("2026-10-19"));

            assertThat(reservationRepository.count()).isEqualTo(1);
        }

        @Test
        void acceptsTheOldestDayOfTheWindow() throws Exception {
            ScheduleSlot saturdaySlot = slot(DayOfWeek.SATURDAY, LocalTime.of(19, 0));

            manual(saturdaySlot, PAST_30)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.reservationDate").value("2026-09-19"));

            assertThat(reservationRepository.count()).isEqualTo(1);
        }

        @Test
        void rejectsTheDayBeforeTheWindow() throws Exception {
            ScheduleSlot fridaySlot = slot(DayOfWeek.FRIDAY, LocalTime.of(19, 0));

            manual(fridaySlot, PAST_31)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message")
                            .value("Reservation date is too far in the past"));

            assertThat(reservationRepository.count()).isZero();
        }

        @Test
        void hasNoLimitAheadOfTime() throws Exception {
            manual(mondaySlot, LocalDate.of(2027, 1, 4))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.reservationDate").value("2027-01-04"));

            assertThat(reservationRepository.count()).isEqualTo(1);
        }

        @Test
        void stillRespectsTheDayOfTheWeekOfTheSlot() throws Exception {
            manual(mondaySlot, TODAY.minusDays(1))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message")
                            .value("Reservation date does not match schedule slot day of week"));

            assertThat(reservationRepository.count()).isZero();
        }

        @Test
        void stillConflictsWithAnExistingReservationOnThePastDate() throws Exception {
            LocalDate pastMonday = TODAY.minusDays(14);

            manual(mondaySlot, pastMonday).andExpect(status().isCreated());
            manual(mondaySlot, pastMonday).andExpect(status().isConflict());

            assertThat(reservationRepository.count()).isEqualTo(1);
        }

        @Test
        void retroactiveReservationCanBeCompletedByTheAdmin() throws Exception {
            LocalDate pastMonday = TODAY.minusDays(7);

            String response = manual(mondaySlot, pastMonday)
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            Number reservationId = JsonPath.read(response, "$.id");

            mockMvc.perform(post(
                            BASE_PATH + "/{reservationId}/complete",
                            establishment.getId(),
                            sportField.getId(),
                            mondaySlot.getId(),
                            reservationId
                    ))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("COMPLETED"));
        }
    }

    @Nested
    @DisplayName("Disponibilidade pública: até 14 dias à frente")
    class AvailabilityWindow {

        @Test
        void acceptsTheLastDayOfTheWindow() throws Exception {
            availability(DAY_14).andExpect(status().isOk());
        }

        @Test
        void rejectsTheDayAfterTheWindow() throws Exception {
            availability(DAY_15)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message")
                            .value("Availability date is too far in the future"));
        }
    }

    @Nested
    @DisplayName("Schedule slot lock races")
    class ScheduleSlotLockRaces {

        @Test
        @Timeout(15)
        void secondRetroactiveManualReservationIsRejectedAfterWaitingForTheLock()
                throws Exception {
            LocalDate pastMonday = TODAY.minusDays(7);
            CountDownLatch created = new CountDownLatch(1);
            CountDownLatch allowCommit = new CountDownLatch(1);

            Future<?> first = executor.submit(() ->
                    inTransaction(() -> {
                        createManualReservation(pastMonday);
                        created.countDown();
                        await(allowCommit);
                    })
            );

            await(created);

            String waitingConnection = "manual-retroactive-waiter";
            Future<?> second = executor.submit(() ->
                    inNamedTransaction(
                            waitingConnection,
                            () -> createManualReservation(pastMonday)
                    )
            );

            awaitConnectionWaitingForLock(waitingConnection);
            allowCommit.countDown();

            first.get(CONCURRENCY_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            assertThatThrownBy(() ->
                    second.get(CONCURRENCY_TIMEOUT.toSeconds(), TimeUnit.SECONDS)
            )
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(ReservationAlreadyExistsException.class);

            assertThat(reservationRepository.count()).isEqualTo(1);
        }
    }

    private ScheduleSlot slot(DayOfWeek dayOfWeek, LocalTime startTime) {
        return scheduleSlotRepository.save(
                new ScheduleSlot(sportField, dayOfWeek, startTime, new BigDecimal("150.50"))
        );
    }

    private ResultActions online(ScheduleSlot slot, LocalDate date) throws Exception {
        return mockMvc.perform(post(
                        BASE_PATH,
                        establishment.getId(),
                        sportField.getId(),
                        slot.getId()
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"organizerId": %d, "reservationDate": "%s"}
                        """.formatted(organizer.getId(), date)));
    }

    private ResultActions manual(ScheduleSlot slot, LocalDate date) throws Exception {
        return mockMvc.perform(post(
                        BASE_PATH + "/manual",
                        establishment.getId(),
                        sportField.getId(),
                        slot.getId()
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"reservationDate": "%s", "customerName": "Joao da Silva"}
                        """.formatted(date)));
    }

    private ResultActions availability(LocalDate date) throws Exception {
        return mockMvc.perform(get(
                        AVAILABILITY_PATH,
                        establishment.getId(),
                        sportField.getId()
                )
                .param("date", date.toString()));
    }

    private void createManualReservation(LocalDate date) {
        reservationService.createManualReservation(
                establishment.getId(),
                sportField.getId(),
                mondaySlot.getId(),
                date,
                "Joao da Silva",
                null
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
                "Connection " + connectionName + " did not wait for the schedule slot lock"
        );
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(CONCURRENCY_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
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

    @TestConfiguration(proxyBeanMethods = false)
    static class TestClockConfiguration {

        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(Instant.parse("2026-10-19T21:30:00Z"), SAO_PAULO);
        }
    }
}
