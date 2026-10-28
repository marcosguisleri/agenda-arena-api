package br.dev.guisleri.agendaarena.reservation;

import br.dev.guisleri.agendaarena.establishment.entity.Establishment;
import br.dev.guisleri.agendaarena.establishment.repository.EstablishmentRepository;
import br.dev.guisleri.agendaarena.reservation.entity.Reservation;
import br.dev.guisleri.agendaarena.reservation.model.ReservationOrigin;
import br.dev.guisleri.agendaarena.reservation.model.ReservationStatus;
import br.dev.guisleri.agendaarena.reservation.repository.ReservationRepository;
import br.dev.guisleri.agendaarena.schedule.entity.ScheduleSlot;
import br.dev.guisleri.agendaarena.schedule.repository.ScheduleSlotRepository;
import br.dev.guisleri.agendaarena.sportfield.entity.SportField;
import br.dev.guisleri.agendaarena.sportfield.model.SportType;
import br.dev.guisleri.agendaarena.sportfield.repository.SportFieldRepository;
import br.dev.guisleri.agendaarena.user.entity.User;
import br.dev.guisleri.agendaarena.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.stream.Stream;

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
@Import(ReservationIntegrationTests.TestClockConfiguration.class)
class ReservationIntegrationTests {

    private static final String BASE_PATH =
            "/establishments/{establishmentId}/sports-fields/{sportFieldId}"
                    + "/schedule-slots/{scheduleSlotId}/reservations";
    private static final String AVAILABILITY_PATH =
            "/establishments/{establishmentId}/sports-fields/{sportFieldId}/availability";
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 19);
    private static final LocalDate NEXT_MONDAY = LocalDate.of(2026, 10, 26);
    private static final LocalDate FOLLOWING_MONDAY = LocalDate.of(2026, 11, 2);
    private static final MutableClock TEST_CLOCK = new MutableClock(SAO_PAULO);

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

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
        reservationRepository.deleteAllInBatch();
        scheduleSlotRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        sportFieldRepository.deleteAllInBatch();
        establishmentRepository.deleteAllInBatch();

        TEST_CLOCK.setLocalDateTime(LocalDateTime.of(TODAY, LocalTime.of(18, 30)));

        establishment = establishmentRepository.save(new Establishment("Arena Central"));
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
    }

    @Test
    void shouldCreateConfirmedReservationWithScheduleSlotSnapshot() throws Exception {
        String response = createReservation(scheduleSlot, organizer.getId(), NEXT_MONDAY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.organizerId").value(organizer.getId()))
                .andExpect(jsonPath("$.origin").value("ONLINE"))
                .andExpect(jsonPath("$.customerName").value(nullValue()))
                .andExpect(jsonPath("$.customerPhone").value(nullValue()))
                .andExpect(jsonPath("$.scheduleSlotId").value(scheduleSlot.getId()))
                .andExpect(jsonPath("$.reservationDate").value("2026-10-26"))
                .andExpect(jsonPath("$.startTime").value("19:00:00"))
                .andExpect(jsonPath("$.endTime").value("20:00:00"))
                .andExpect(jsonPath("$.price").value(150.50))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Number returnedId = JsonPath.read(response, "$.id");
        Reservation persisted = reservationRepository.findById(returnedId.longValue()).orElseThrow();

        assertThat(reservationRepository.count()).isEqualTo(1);
        assertThat(persisted.getOrganizer().getId()).isEqualTo(organizer.getId());
        assertThat(persisted.getOrigin()).isEqualTo(ReservationOrigin.ONLINE);
        assertThat(persisted.getCustomerName()).isNull();
        assertThat(persisted.getCustomerPhone()).isNull();
        assertThat(persisted.getScheduleSlot().getId()).isEqualTo(scheduleSlot.getId());
        assertThat(persisted.getReservationDate()).isEqualTo(NEXT_MONDAY);
        assertThat(persisted.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(persisted.getStartTime()).isEqualTo(scheduleSlot.getStartTime());
        assertThat(persisted.getEndTime()).isEqualTo(scheduleSlot.getEndTime());
        assertThat(persisted.getPrice()).isEqualByComparingTo(scheduleSlot.getPrice());
    }

    @Test
    void shouldCreateConfirmedManualReservationWithScheduleSlotSnapshot() throws Exception {
        String response = createManualReservation(
                NEXT_MONDAY,
                "Joao da Silva",
                "11999991234"
        )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.organizerId").value(nullValue()))
                .andExpect(jsonPath("$.origin").value("MANUAL"))
                .andExpect(jsonPath("$.customerName").value("Joao da Silva"))
                .andExpect(jsonPath("$.customerPhone").value("11999991234"))
                .andExpect(jsonPath("$.scheduleSlotId").value(scheduleSlot.getId()))
                .andExpect(jsonPath("$.reservationDate").value("2026-10-26"))
                .andExpect(jsonPath("$.startTime").value("19:00:00"))
                .andExpect(jsonPath("$.endTime").value("20:00:00"))
                .andExpect(jsonPath("$.price").value(150.50))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Number returnedId = JsonPath.read(response, "$.id");
        Reservation persisted = reservationRepository.findById(returnedId.longValue()).orElseThrow();

        assertThat(reservationRepository.count()).isEqualTo(1);
        assertThat(persisted.getOrganizer()).isNull();
        assertThat(persisted.getOrigin()).isEqualTo(ReservationOrigin.MANUAL);
        assertThat(persisted.getCustomerName()).isEqualTo("Joao da Silva");
        assertThat(persisted.getCustomerPhone()).isEqualTo("11999991234");
        assertThat(persisted.getScheduleSlot().getId()).isEqualTo(scheduleSlot.getId());
        assertThat(persisted.getReservationDate()).isEqualTo(NEXT_MONDAY);
        assertThat(persisted.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(persisted.getStartTime()).isEqualTo(scheduleSlot.getStartTime());
        assertThat(persisted.getEndTime()).isEqualTo(scheduleSlot.getEndTime());
        assertThat(persisted.getPrice()).isEqualByComparingTo(scheduleSlot.getPrice());
    }

    @Test
    void shouldNormalizeManualReservationCustomerNameAndPhone() throws Exception {
        createManualReservation(
                NEXT_MONDAY,
                "  Joao da Silva  ",
                "(11) 99999-1234"
        )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerName").value("Joao da Silva"))
                .andExpect(jsonPath("$.customerPhone").value("11999991234"));

        Reservation persisted = reservationRepository.findAll().getFirst();
        assertThat(persisted.getCustomerName()).isEqualTo("Joao da Silva");
        assertThat(persisted.getCustomerPhone()).isEqualTo("11999991234");
    }

    @Test
    void shouldCreateManualReservationWithoutCustomerPhone() throws Exception {
        createManualReservation(NEXT_MONDAY, "Joao da Silva", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerPhone").value(nullValue()));

        Reservation persisted = reservationRepository.findAll().getFirst();
        assertThat(persisted.getCustomerPhone()).isNull();
    }

    @Test
    void shouldTreatEmptyManualReservationPhoneAsNull() throws Exception {
        createManualReservation(NEXT_MONDAY, "Joao da Silva", "")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerPhone").value(nullValue()));

        Reservation persisted = reservationRepository.findAll().getFirst();
        assertThat(persisted.getCustomerPhone()).isNull();
    }

    @Test
    void shouldMarkScheduleSlotAsUnavailableAfterManualReservation() throws Exception {
        createManualReservation(NEXT_MONDAY, "Joao da Silva", null)
                .andExpect(status().isCreated());

        mockMvc.perform(get(
                        AVAILABILITY_PATH,
                        establishment.getId(),
                        sportField.getId()
                )
                        .param("date", NEXT_MONDAY.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].scheduleSlotId").value(scheduleSlot.getId()))
                .andExpect(jsonPath("$[0].available").value(false));
    }

    @Test
    void shouldRejectManualReservationWhenConfirmedReservationExists() throws Exception {
        createReservation(scheduleSlot, organizer.getId(), NEXT_MONDAY)
                .andExpect(status().isCreated());

        createManualReservation(NEXT_MONDAY, "Joao da Silva", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"));

        assertThat(countReservations(scheduleSlot, NEXT_MONDAY, "CONFIRMED")).isEqualTo(1);
    }

    @Test
    void shouldRejectDuplicateManualReservation() throws Exception {
        createManualReservation(NEXT_MONDAY, "Joao da Silva", null)
                .andExpect(status().isCreated());

        createManualReservation(NEXT_MONDAY, "Maria Souza", "11988887777")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"));

        assertThat(countReservations(scheduleSlot, NEXT_MONDAY, "CONFIRMED")).isEqualTo(1);
    }

    @Test
    void shouldRejectPastReservationDate() throws Exception {
        assertBadRequest(createReservation(
                scheduleSlot,
                organizer.getId(),
                LocalDate.of(2026, 10, 12)
        ));
        assertThat(reservationRepository.count()).isZero();
    }

    @Test
    void shouldRejectReservationDateWithDifferentDayOfWeek() throws Exception {
        assertBadRequest(createReservation(
                scheduleSlot,
                organizer.getId(),
                LocalDate.of(2026, 10, 27)
        ));
        assertThat(reservationRepository.count()).isZero();
    }

    @Test
    void shouldRejectReservationForTodayWhenSlotHasAlreadyStarted() throws Exception {
        TEST_CLOCK.setLocalDateTime(LocalDateTime.of(TODAY, LocalTime.of(19, 0)));

        assertBadRequest(createReservation(scheduleSlot, organizer.getId(), TODAY));
        assertThat(reservationRepository.count()).isZero();
    }

    @Test
    void shouldCreateReservationForTodayWhenSlotIsInTheFuture() throws Exception {
        createReservation(scheduleSlot, organizer.getId(), TODAY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservationDate").value("2026-10-19"))
                .andExpect(jsonPath("$.startTime").value("19:00:00"));

        assertThat(reservationRepository.count()).isEqualTo(1);
    }

    @Test
    void shouldReturnNotFoundForUnknownOrganizer() throws Exception {
        createReservation(scheduleSlot, Long.MAX_VALUE, NEXT_MONDAY)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));

        assertThat(reservationRepository.count()).isZero();
    }

    @Test
    void shouldReturnNotFoundForUnknownScheduleSlot() throws Exception {
        createReservation(Long.MAX_VALUE, organizer.getId(), NEXT_MONDAY)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));

        assertThat(reservationRepository.count()).isZero();
    }

    @Test
    void shouldRejectInactiveOrganizer() throws Exception {
        jdbcTemplate.update("UPDATE users SET active = false WHERE id = ?", organizer.getId());

        assertBadRequest(createReservation(scheduleSlot, organizer.getId(), NEXT_MONDAY));
        assertThat(reservationRepository.count()).isZero();
    }

    @Test
    void shouldRejectInactiveEstablishment() throws Exception {
        establishment.deactivate();
        establishmentRepository.save(establishment);

        assertBadRequest(createReservation(scheduleSlot, organizer.getId(), NEXT_MONDAY));
        assertThat(reservationRepository.count()).isZero();
    }

    @Test
    void shouldRejectInactiveSportField() throws Exception {
        jdbcTemplate.update("UPDATE sports_fields SET active = false WHERE id = ?", sportField.getId());

        assertBadRequest(createReservation(scheduleSlot, organizer.getId(), NEXT_MONDAY));
        assertThat(reservationRepository.count()).isZero();
    }

    @Test
    void shouldRejectInactiveScheduleSlot() throws Exception {
        jdbcTemplate.update("UPDATE schedule_slots SET active = false WHERE id = ?", scheduleSlot.getId());

        assertBadRequest(createReservation(scheduleSlot, organizer.getId(), NEXT_MONDAY));
        assertThat(reservationRepository.count()).isZero();
    }

    @Test
    void shouldRejectDuplicateConfirmedReservation() throws Exception {
        createReservation(scheduleSlot, organizer.getId(), NEXT_MONDAY)
                .andExpect(status().isCreated());

        createReservation(scheduleSlot, organizer.getId(), NEXT_MONDAY)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"));

        assertThat(countReservations(scheduleSlot, NEXT_MONDAY, "CONFIRMED")).isEqualTo(1);
    }

    @Test
    void shouldAllowConfirmedReservationWhenCanceledHistoryExists() throws Exception {
        insertReservation(scheduleSlot, organizer, NEXT_MONDAY, "CANCELLED");

        createReservation(scheduleSlot, organizer.getId(), NEXT_MONDAY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        assertThat(reservationRepository.count()).isEqualTo(2);
        assertThat(countReservations(scheduleSlot, NEXT_MONDAY, "CANCELLED")).isEqualTo(1);
        assertThat(countReservations(scheduleSlot, NEXT_MONDAY, "CONFIRMED")).isEqualTo(1);
    }

    @Test
    void shouldEnforcePartialUniqueIndexForConfirmedReservations() {
        reservationRepository.saveAndFlush(
                Reservation.online(organizer, scheduleSlot, NEXT_MONDAY)
        );

        try {
            reservationRepository.saveAndFlush(
                    Reservation.online(organizer, scheduleSlot, NEXT_MONDAY)
            );
        } catch (DataIntegrityViolationException exception) {
            exception.printStackTrace();
        }

        assertThat(countReservations(scheduleSlot, NEXT_MONDAY, "CONFIRMED")).isEqualTo(1);
    }

    @Test
    void shouldAllowSameScheduleSlotOnDifferentMatchingDates() throws Exception {
        createReservation(scheduleSlot, organizer.getId(), NEXT_MONDAY)
                .andExpect(status().isCreated());
        createReservation(scheduleSlot, organizer.getId(), FOLLOWING_MONDAY)
                .andExpect(status().isCreated());

        assertThat(reservationRepository.count()).isEqualTo(2);
        assertThat(countReservations(scheduleSlot, NEXT_MONDAY, "CONFIRMED")).isEqualTo(1);
        assertThat(countReservations(scheduleSlot, FOLLOWING_MONDAY, "CONFIRMED")).isEqualTo(1);
    }

    @Test
    void shouldAllowDifferentScheduleSlotsOnSameDate() throws Exception {
        ScheduleSlot secondSlot = scheduleSlotRepository.save(
                new ScheduleSlot(
                        sportField,
                        DayOfWeek.MONDAY,
                        LocalTime.of(20, 0),
                        new BigDecimal("175.00")
                )
        );

        createReservation(scheduleSlot, organizer.getId(), NEXT_MONDAY)
                .andExpect(status().isCreated());
        createReservation(secondSlot, organizer.getId(), NEXT_MONDAY)
                .andExpect(status().isCreated());

        assertThat(reservationRepository.count()).isEqualTo(2);
        assertThat(countReservations(scheduleSlot, NEXT_MONDAY, "CONFIRMED")).isEqualTo(1);
        assertThat(countReservations(secondSlot, NEXT_MONDAY, "CONFIRMED")).isEqualTo(1);
    }

    @ParameterizedTest(name = "should reject invalid request: {0}")
    @MethodSource("invalidRequests")
    void shouldRejectInvalidRequest(String scenario, String expectedField, String requestBody) throws Exception {
        mockMvc.perform(post(
                        BASE_PATH,
                        establishment.getId(),
                        sportField.getId(),
                        scheduleSlot.getId()
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.errors." + expectedField).isNotEmpty());

        assertThat(reservationRepository.count()).isZero();
    }

    @ParameterizedTest(name = "should reject invalid manual request: {0}")
    @MethodSource("invalidManualRequests")
    void shouldRejectInvalidManualRequest(
            String scenario,
            String expectedField,
            String requestBody
    ) throws Exception {
        mockMvc.perform(post(
                        BASE_PATH + "/manual",
                        establishment.getId(),
                        sportField.getId(),
                        scheduleSlot.getId()
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.errors." + expectedField).isNotEmpty());

        assertThat(reservationRepository.count()).isZero();
    }

    private ResultActions createReservation(
            ScheduleSlot slot,
            Long organizerId,
            LocalDate reservationDate
    ) throws Exception {
        return createReservation(slot.getId(), organizerId, reservationDate);
    }

    private ResultActions createReservation(
            Long scheduleSlotId,
            Long organizerId,
            LocalDate reservationDate
    ) throws Exception {
        return mockMvc.perform(post(
                        BASE_PATH,
                        establishment.getId(),
                        sportField.getId(),
                        scheduleSlotId
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "organizerId": %d,
                          "reservationDate": "%s"
                        }
                        """.formatted(organizerId, reservationDate)));
    }

    private ResultActions createManualReservation(
            LocalDate reservationDate,
            String customerName,
            String customerPhone
    ) throws Exception {
        String serializedPhone = customerPhone == null
                ? "null"
                : "\"" + customerPhone + "\"";

        return mockMvc.perform(post(
                        BASE_PATH + "/manual",
                        establishment.getId(),
                        sportField.getId(),
                        scheduleSlot.getId()
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "reservationDate": "%s",
                          "customerName": "%s",
                          "customerPhone": %s
                        }
                        """.formatted(
                        reservationDate,
                        customerName,
                        serializedPhone
                )));
    }

    private void assertBadRequest(ResultActions result) throws Exception {
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    private void insertReservation(
            ScheduleSlot slot,
            User reservationOrganizer,
            LocalDate reservationDate,
            String status
    ) {
        jdbcTemplate.update("""
                        INSERT INTO reservations (
                            organizer_id,
                            schedule_slot_id,
                            reservation_date,
                            start_time,
                            end_time,
                            price,
                            origin,
                            status
                        ) VALUES (?, ?, ?, ?, ?, ?, 'ONLINE', ?)
                        """,
                reservationOrganizer.getId(),
                slot.getId(),
                Date.valueOf(reservationDate),
                Time.valueOf(slot.getStartTime()),
                Time.valueOf(slot.getEndTime()),
                slot.getPrice(),
                status
        );
    }

    private long countReservations(
            ScheduleSlot slot,
            LocalDate reservationDate,
            String status
    ) {
        Long count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM reservations
                        WHERE schedule_slot_id = ?
                          AND reservation_date = ?
                          AND status = ?
                        """,
                Long.class,
                slot.getId(),
                Date.valueOf(reservationDate),
                status
        );

        return count == null ? 0 : count;
    }

    private static Stream<Arguments> invalidRequests() {
        return Stream.of(
                Arguments.of(
                        "null organizerId",
                        "organizerId",
                        """
                                {"organizerId":null,"reservationDate":"2026-10-26"}
                                """
                ),
                Arguments.of(
                        "zero organizerId",
                        "organizerId",
                        """
                                {"organizerId":0,"reservationDate":"2026-10-26"}
                                """
                ),
                Arguments.of(
                        "negative organizerId",
                        "organizerId",
                        """
                                {"organizerId":-1,"reservationDate":"2026-10-26"}
                                """
                ),
                Arguments.of(
                        "null reservationDate",
                        "reservationDate",
                        """
                                {"organizerId":1,"reservationDate":null}
                                """
                )
        );
    }

    private static Stream<Arguments> invalidManualRequests() {
        return Stream.of(
                Arguments.of(
                        "null reservationDate",
                        "reservationDate",
                        """
                                {"reservationDate":null,"customerName":"Joao da Silva"}
                                """
                ),
                Arguments.of(
                        "null customerName",
                        "customerName",
                        """
                                {"reservationDate":"2026-10-26","customerName":null}
                                """
                ),
                Arguments.of(
                        "blank customerName",
                        "customerName",
                        """
                                {"reservationDate":"2026-10-26","customerName":"   "}
                                """
                ),
                Arguments.of(
                        "customerName longer than 120 characters",
                        "customerName",
                        """
                                {"reservationDate":"2026-10-26","customerName":"%s"}
                                """.formatted("a".repeat(121))
                ),
                Arguments.of(
                        "phone containing letters",
                        "customerPhone",
                        """
                                {"reservationDate":"2026-10-26","customerName":"Joao","customerPhone":"11 9999A-1234"}
                                """
                ),
                Arguments.of(
                        "phone with fewer than 10 digits",
                        "customerPhone",
                        """
                                {"reservationDate":"2026-10-26","customerName":"Joao","customerPhone":"119999912"}
                                """
                ),
                Arguments.of(
                        "phone with more than 11 digits",
                        "customerPhone",
                        """
                                {"reservationDate":"2026-10-26","customerName":"Joao","customerPhone":"119999912345"}
                                """
                ),
                Arguments.of(
                        "formatted phone longer than 20 characters",
                        "customerPhone",
                        """
                                {"reservationDate":"2026-10-26","customerName":"Joao","customerPhone":"( 11 ) 9 9 9 9 9 - 1 2 3 4"}
                                """
                )
        );
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
