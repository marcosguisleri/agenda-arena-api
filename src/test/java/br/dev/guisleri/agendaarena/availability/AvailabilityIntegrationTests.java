package br.dev.guisleri.agendaarena.availability;

import br.dev.guisleri.agendaarena.establishment.entity.Establishment;
import br.dev.guisleri.agendaarena.establishment.repository.EstablishmentRepository;
import br.dev.guisleri.agendaarena.reservation.entity.Reservation;
import br.dev.guisleri.agendaarena.reservation.model.ReservationStatus;
import br.dev.guisleri.agendaarena.reservation.repository.ReservationRepository;
import br.dev.guisleri.agendaarena.schedule.entity.ScheduleSlot;
import br.dev.guisleri.agendaarena.schedule.repository.ScheduleSlotRepository;
import br.dev.guisleri.agendaarena.sportfield.entity.SportField;
import br.dev.guisleri.agendaarena.sportfield.model.SportType;
import br.dev.guisleri.agendaarena.sportfield.repository.SportFieldRepository;
import br.dev.guisleri.agendaarena.user.entity.User;
import br.dev.guisleri.agendaarena.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(AvailabilityIntegrationTests.TestClockConfiguration.class)
class AvailabilityIntegrationTests {

    private static final String AVAILABILITY_PATH =
            "/establishments/{establishmentId}/sports-fields/{sportFieldId}/availability";
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 19);
    private static final LocalDate NEXT_MONDAY = LocalDate.of(2026, 10, 26);
    private static final MutableClock TEST_CLOCK = new MutableClock(SAO_PAULO);

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

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
    private JdbcTemplate jdbcTemplate;

    private Establishment establishment;
    private SportField sportField;

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
        reservationRepository.deleteAllInBatch();
        scheduleSlotRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        sportFieldRepository.deleteAllInBatch();
        establishmentRepository.deleteAllInBatch();

        TEST_CLOCK.setLocalDateTime(LocalDateTime.of(TODAY, LocalTime.of(18, 30)));
        establishment = establishmentRepository.save(
                new Establishment("Arena Central")
        );
        sportField = sportFieldRepository.save(
                new SportField(establishment, "Campo 1", SportType.SOCIETY)
        );
    }

    @Test
    void shouldReturnAvailableScheduleSlotsForFutureDate() throws Exception {
        ScheduleSlot firstSlot = createSlot(DayOfWeek.MONDAY, "19:00", "150.00");
        ScheduleSlot secondSlot = createSlot(DayOfWeek.MONDAY, "20:00", "175.50");

        availabilityFor(NEXT_MONDAY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].scheduleSlotId").value(firstSlot.getId().intValue()))
                .andExpect(jsonPath("$[0].date").value("2026-10-26"))
                .andExpect(jsonPath("$[0].startTime").value("19:00:00"))
                .andExpect(jsonPath("$[0].endTime").value("20:00:00"))
                .andExpect(jsonPath("$[0].price").value(150.00))
                .andExpect(jsonPath("$[0].available").value(true))
                .andExpect(jsonPath("$[1].scheduleSlotId").value(secondSlot.getId().intValue()))
                .andExpect(jsonPath("$[1].date").value("2026-10-26"))
                .andExpect(jsonPath("$[1].startTime").value("20:00:00"))
                .andExpect(jsonPath("$[1].endTime").value("21:00:00"))
                .andExpect(jsonPath("$[1].price").value(175.50))
                .andExpect(jsonPath("$[1].available").value(true));
    }

    @Test
    void shouldMarkConfirmedReservationAsUnavailable() throws Exception {
        ScheduleSlot reservedSlot = createSlot(DayOfWeek.MONDAY, "19:00");
        ScheduleSlot availableSlot = createSlot(DayOfWeek.MONDAY, "20:00");
        createReservation(reservedSlot, NEXT_MONDAY);

        availabilityFor(NEXT_MONDAY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].scheduleSlotId").value(reservedSlot.getId().intValue()))
                .andExpect(jsonPath("$[0].available").value(false))
                .andExpect(jsonPath("$[1].scheduleSlotId").value(availableSlot.getId().intValue()))
                .andExpect(jsonPath("$[1].available").value(true));
    }

    @Test
    void shouldIgnoreCancelledReservation() throws Exception {
        ScheduleSlot slot = createSlot(DayOfWeek.MONDAY, "19:00");
        Reservation reservation = createReservation(slot, NEXT_MONDAY);
        jdbcTemplate.update(
                "UPDATE reservations SET status = ? WHERE id = ?",
                ReservationStatus.CANCELLED.name(),
                reservation.getId()
        );

        availabilityFor(NEXT_MONDAY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].scheduleSlotId").value(slot.getId().intValue()))
                .andExpect(jsonPath("$[0].available").value(true));
    }

    @Test
    void shouldReturnSlotsOrderedByStartTime() throws Exception {
        ScheduleSlot lateSlot = createSlot(DayOfWeek.MONDAY, "21:00");
        ScheduleSlot earlySlot = createSlot(DayOfWeek.MONDAY, "19:00");
        ScheduleSlot middleSlot = createSlot(DayOfWeek.MONDAY, "20:00");

        availabilityFor(NEXT_MONDAY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].scheduleSlotId").value(earlySlot.getId().intValue()))
                .andExpect(jsonPath("$[1].scheduleSlotId").value(middleSlot.getId().intValue()))
                .andExpect(jsonPath("$[2].scheduleSlotId").value(lateSlot.getId().intValue()));
    }

    @Test
    void shouldReturnOnlySlotsForRequestedDayOfWeek() throws Exception {
        ScheduleSlot mondaySlot = createSlot(DayOfWeek.MONDAY, "19:00");
        createSlot(DayOfWeek.TUESDAY, "19:00");

        availabilityFor(NEXT_MONDAY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].scheduleSlotId").value(mondaySlot.getId().intValue()));
    }

    @Test
    void shouldIgnoreInactiveScheduleSlots() throws Exception {
        ScheduleSlot activeSlot = createSlot(DayOfWeek.MONDAY, "19:00");
        ScheduleSlot inactiveSlot = createSlot(DayOfWeek.MONDAY, "20:00");
        jdbcTemplate.update(
                "UPDATE schedule_slots SET active = false WHERE id = ?",
                inactiveSlot.getId()
        );

        availabilityFor(NEXT_MONDAY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].scheduleSlotId").value(activeSlot.getId().intValue()));
    }

    @Test
    void shouldHidePastSlotsWhenQueryingToday() throws Exception {
        createSlot(DayOfWeek.MONDAY, "17:00");
        createSlot(DayOfWeek.MONDAY, "18:00");
        ScheduleSlot firstFutureSlot = createSlot(DayOfWeek.MONDAY, "19:00");
        ScheduleSlot secondFutureSlot = createSlot(DayOfWeek.MONDAY, "20:00");

        availabilityFor(TODAY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].scheduleSlotId").value(firstFutureSlot.getId().intValue()))
                .andExpect(jsonPath("$[1].scheduleSlotId").value(secondFutureSlot.getId().intValue()));
    }

    @Test
    void shouldHideSlotStartingExactlyNow() throws Exception {
        TEST_CLOCK.setLocalDateTime(LocalDateTime.of(TODAY, LocalTime.of(19, 0)));
        createSlot(DayOfWeek.MONDAY, "19:00");

        availabilityFor(TODAY)
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void shouldNotApplyTimeFilterForFutureDate() throws Exception {
        TEST_CLOCK.setLocalDateTime(LocalDateTime.of(TODAY, LocalTime.of(20, 0)));
        ScheduleSlot earlySlot = createSlot(DayOfWeek.MONDAY, "08:00");

        availabilityFor(NEXT_MONDAY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].scheduleSlotId").value(earlySlot.getId().intValue()));
    }

    @Test
    void shouldRejectPastAvailabilityDate() throws Exception {
        availabilityFor(TODAY.minusDays(1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.errors").exists());
    }

    @Test
    void shouldRejectInactiveEstablishment() throws Exception {
        jdbcTemplate.update(
                "UPDATE establishments SET active = false WHERE id = ?",
                establishment.getId()
        );

        availabilityFor(NEXT_MONDAY)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void shouldRejectInactiveSportField() throws Exception {
        jdbcTemplate.update(
                "UPDATE sports_fields SET active = false WHERE id = ?",
                sportField.getId()
        );

        availabilityFor(NEXT_MONDAY)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void shouldReturnNotFoundForUnknownEstablishment() throws Exception {
        mockMvc.perform(get(
                        AVAILABILITY_PATH,
                        Long.MAX_VALUE,
                        sportField.getId()
                )
                        .param("date", NEXT_MONDAY.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void shouldReturnNotFoundForUnknownSportField() throws Exception {
        mockMvc.perform(get(
                        AVAILABILITY_PATH,
                        establishment.getId(),
                        Long.MAX_VALUE
                )
                        .param("date", NEXT_MONDAY.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void shouldReturnNotFoundWhenSportFieldBelongsToAnotherEstablishment() throws Exception {
        Establishment anotherEstablishment = establishmentRepository.save(
                new Establishment("Arena Norte")
        );
        SportField otherSportField = sportFieldRepository.save(
                new SportField(anotherEstablishment, "Campo 2", SportType.SOCIETY)
        );

        mockMvc.perform(get(
                        AVAILABILITY_PATH,
                        establishment.getId(),
                        otherSportField.getId()
                )
                        .param("date", NEXT_MONDAY.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void shouldReturnEmptyListWhenNoScheduleSlotsExistForDate() throws Exception {
        availabilityFor(NEXT_MONDAY)
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void shouldRejectRequestWithoutDateParameter() throws Exception {
        mockMvc.perform(get(
                AVAILABILITY_PATH,
                establishment.getId(),
                sportField.getId()
        )).andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectDateWithInvalidFormat() throws Exception {
        mockMvc.perform(get(
                        AVAILABILITY_PATH,
                        establishment.getId(),
                        sportField.getId()
                )
                        .param("date", "19-10-2026"))
                .andExpect(status().isBadRequest());
    }

    private ScheduleSlot createSlot(DayOfWeek dayOfWeek, String startTime) {
        return createSlot(dayOfWeek, startTime, "150.00");
    }

    private ScheduleSlot createSlot(
            DayOfWeek dayOfWeek,
            String startTime,
            String price
    ) {
        return scheduleSlotRepository.save(new ScheduleSlot(
                sportField,
                dayOfWeek,
                LocalTime.parse(startTime),
                new BigDecimal(price)
        ));
    }

    private Reservation createReservation(ScheduleSlot slot, LocalDate date) {
        User organizer = userRepository.save(new User(
                "Maria da Silva",
                "maria@example.com",
                "11999991234",
                "password-hash"
        ));
        return reservationRepository.save(Reservation.online(organizer, slot, date));
    }

    private ResultActions availabilityFor(LocalDate date) throws Exception {
        return mockMvc.perform(get(
                        AVAILABILITY_PATH,
                        establishment.getId(),
                        sportField.getId()
                )
                        .param("date", date.toString()));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestClockConfiguration {

        @Bean
        @Primary
        Clock testClock() {
            return TEST_CLOCK;
        }
    }

    private static final class MutableClock extends Clock {

        private final ZoneId zoneId;
        private Instant currentInstant;

        private MutableClock(ZoneId zoneId) {
            this.zoneId = zoneId;
        }

        private void setLocalDateTime(LocalDateTime dateTime) {
            currentInstant = dateTime.atZone(zoneId).toInstant();
        }

        @Override
        public ZoneId getZone() {
            return zoneId;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(currentInstant, zone);
        }

        @Override
        public Instant instant() {
            return currentInstant;
        }
    }
}
