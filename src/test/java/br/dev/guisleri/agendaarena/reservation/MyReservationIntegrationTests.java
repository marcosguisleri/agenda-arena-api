package br.dev.guisleri.agendaarena.reservation;

import br.dev.guisleri.agendaarena.auth.service.JwtTokenService;
import br.dev.guisleri.agendaarena.establishment.entity.Establishment;
import br.dev.guisleri.agendaarena.establishment.entity.EstablishmentAdmin;
import br.dev.guisleri.agendaarena.establishment.repository.EstablishmentAdminRepository;
import br.dev.guisleri.agendaarena.establishment.repository.EstablishmentRepository;
import br.dev.guisleri.agendaarena.reservation.model.ReservationStatus;
import br.dev.guisleri.agendaarena.reservation.repository.ReservationRepository;
import br.dev.guisleri.agendaarena.reservation.service.ReservationService;
import br.dev.guisleri.agendaarena.schedule.entity.ScheduleSlot;
import br.dev.guisleri.agendaarena.schedule.repository.ScheduleSlotRepository;
import br.dev.guisleri.agendaarena.sportfield.entity.SportField;
import br.dev.guisleri.agendaarena.sportfield.model.SportType;
import br.dev.guisleri.agendaarena.sportfield.repository.SportFieldRepository;
import br.dev.guisleri.agendaarena.support.MutableClock;
import br.dev.guisleri.agendaarena.user.entity.User;
import br.dev.guisleri.agendaarena.user.model.UserRole;
import br.dev.guisleri.agendaarena.user.service.UserService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.web.context.WebApplicationContext;
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
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "Minhas reservas" e pedido de cancelamento. Usa tokens reais (o decoder usa o relógio do sistema),
 * então o relógio da aplicação é um {@link MutableClock} que cada teste reinicia para a hora real;
 * só os testes de limite do prazo o ajustam (sempre perto da hora real, para o token seguir válido).
 */
@SpringBootTest
@Testcontainers
@Import(MyReservationIntegrationTests.TestClockConfiguration.class)
class MyReservationIntegrationTests {

    private static final String PASSWORD = "password-123";
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final MutableClock TEST_CLOCK = new MutableClock(Instant.now(), SAO_PAULO);

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
    private EstablishmentRepository establishmentRepository;
    @Autowired
    private EstablishmentAdminRepository establishmentAdminRepository;
    @Autowired
    private SportFieldRepository sportFieldRepository;
    @Autowired
    private ScheduleSlotRepository scheduleSlotRepository;
    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private ReservationService reservationService;

    private MockMvc mockMvc;
    private LocalDate baseDate;
    private Establishment establishment;
    private Establishment otherEstablishment;
    private SportField sportField;
    private ScheduleSlot scheduleSlot;
    private User organizer;
    private User otherOrganizer;
    private User admin;
    private User otherAdmin;

    @DynamicPropertySource
    static void configurePostgreSQL(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void setUp() {
        TEST_CLOCK.set(Instant.now());
        jdbcTemplate.execute("TRUNCATE TABLE establishments, users RESTART IDENTITY CASCADE");

        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        baseDate = LocalDate.now(clock)
                .plusDays(14)
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));

        organizer = user("organizer@example.com", UserRole.ORGANIZER);
        otherOrganizer = user("other.organizer@example.com", UserRole.ORGANIZER);
        admin = user("admin@example.com", UserRole.ESTABLISHMENT_ADMIN);
        otherAdmin = user("other.admin@example.com", UserRole.ESTABLISHMENT_ADMIN);

        establishment = establishmentRepository.save(new Establishment("Arena Central"));
        otherEstablishment = establishmentRepository.save(new Establishment("Arena Vizinha"));
        establishmentAdminRepository.save(
                new EstablishmentAdmin(admin, establishment, Instant.now(clock)));
        establishmentAdminRepository.save(
                new EstablishmentAdmin(otherAdmin, otherEstablishment, Instant.now(clock)));

        sportField = sportFieldRepository.save(
                new SportField(establishment, "Campo 1", SportType.SOCIETY));
        scheduleSlot = scheduleSlotRepository.save(
                new ScheduleSlot(
                        sportField,
                        DayOfWeek.MONDAY,
                        LocalTime.of(19, 0),
                        new BigDecimal("150.00")
                ));
    }

    // ---------- Minhas reservas ----------

    @Test
    void shouldListOnlyMyReservationsNewestFirst() throws Exception {
        long mine = reserve(organizer, baseDate);
        long later = reserve(organizer, baseDate.plusDays(7));
        reserve(otherOrganizer, baseDate.plusDays(14));

        mockMvc.perform(get("/me/reservations").header("Authorization", bearer(organizer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(later))
                .andExpect(jsonPath("$.content[1].id").value(mine))
                .andExpect(jsonPath("$.content[0].organizerId").value(organizer.getId()))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void shouldPaginateMyReservationsKeepingTheOrder() throws Exception {
        long first = reserve(organizer, baseDate);
        long second = reserve(organizer, baseDate.plusDays(7));
        long third = reserve(organizer, baseDate.plusDays(14));

        mockMvc.perform(get("/me/reservations")
                        .param("page", "0").param("size", "2")
                        .header("Authorization", bearer(organizer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(third))
                .andExpect(jsonPath("$.content[1].id").value(second))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get("/me/reservations")
                        .param("page", "1").param("size", "2")
                        .header("Authorization", bearer(organizer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(first))
                .andExpect(jsonPath("$.page").value(1));

        mockMvc.perform(get("/me/reservations")
                        .param("page", "5").param("size", "2")
                        .header("Authorization", bearer(organizer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void shouldNotCountOrLeakReservationsOfOtherOrganizersWhenPaginating() throws Exception {
        reserve(organizer, baseDate);
        reserve(otherOrganizer, baseDate.plusDays(7));
        reserve(otherOrganizer, baseDate.plusDays(14));

        mockMvc.perform(get("/me/reservations")
                        .param("size", "1")
                        .header("Authorization", bearer(organizer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @ParameterizedTest(name = "page={0}, size={1} é recusado")
    @CsvSource({"-1,20", "0,0", "0,101", "0,-5"})
    void shouldRejectInvalidPagination(String page, String size) throws Exception {
        mockMvc.perform(get("/me/reservations")
                        .param("page", page).param("size", size)
                        .header("Authorization", bearer(organizer)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnEmptyListWhenUserHasNoReservations() throws Exception {
        reserve(otherOrganizer, baseDate);

        mockMvc.perform(get("/me/reservations").header("Authorization", bearer(organizer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void shouldReturnMyReservationById() throws Exception {
        long reservationId = reserve(organizer, baseDate);

        mockMvc.perform(get("/me/reservations/{id}", reservationId)
                        .header("Authorization", bearer(organizer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(reservationId))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.reservationDate").value(baseDate.toString()));
    }

    @Test
    void shouldHideReservationOfAnotherOrganizer() throws Exception {
        long reservationId = reserve(otherOrganizer, baseDate);

        mockMvc.perform(get("/me/reservations/{id}", reservationId)
                        .header("Authorization", bearer(organizer)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void shouldRequireAuthenticationOnMyReservations() throws Exception {
        long reservationId = reserve(organizer, baseDate);

        mockMvc.perform(get("/me/reservations"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/me/reservations/{id}", reservationId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/me/reservations/{id}/cancellation-request", reservationId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRequireCallerTransactionToLockReservationSlot() throws Exception {
        long reservationId = reserve(organizer, baseDate);

        // Fora de transação o lock do slot seria liberado no retorno: o método recusa (MANDATORY).
        assertThatThrownBy(() -> reservationService.findOrganizerReservationForUpdate(
                organizer.getId(), reservationId))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    // ---------- Pedido de cancelamento ----------

    @Test
    void shouldRequestCancellationAndKeepSlotOccupied() throws Exception {
        long reservationId = reserve(organizer, baseDate);

        requestCancellation(organizer, reservationId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLATION_REQUESTED"));

        assertThat(statusOf(reservationId)).isEqualTo(ReservationStatus.CANCELLATION_REQUESTED);

        // O horário continua ocupado até o estabelecimento decidir.
        onlineReservation(otherOrganizer, baseDate)
                .andExpect(status().isConflict());
    }

    @Test
    void shouldNotLetAnotherOrganizerRequestCancellation() throws Exception {
        long reservationId = reserve(otherOrganizer, baseDate);

        requestCancellation(organizer, reservationId)
                .andExpect(status().isNotFound());

        assertThat(statusOf(reservationId)).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void shouldNotAllowRequestingCancellationTwice() throws Exception {
        long reservationId = reserve(organizer, baseDate);

        requestCancellation(organizer, reservationId).andExpect(status().isOk());
        requestCancellation(organizer, reservationId).andExpect(status().isConflict());
    }

    @Test
    void shouldNotAllowRequestingCancellationAfterTheReservationStarted() throws Exception {
        long reservationId = reserve(organizer, baseDate);
        jdbcTemplate.update(
                "UPDATE reservations SET reservation_date = ? WHERE id = ?",
                java.sql.Date.valueOf(LocalDate.now(clock).minusDays(7)),
                reservationId
        );

        requestCancellation(organizer, reservationId)
                .andExpect(status().isConflict());

        assertThat(statusOf(reservationId)).isEqualTo(ReservationStatus.CONFIRMED);
    }

    // Prazo: o pedido vale até o instante do início (reserva das 16h: 15:59 vale, 16:00:00 não).

    @Test
    void shouldAllowRequestingCancellationOneMinuteBeforeTheStart() throws Exception {
        Instant now = freezeClockAtWholeMinute();
        long reservationId = reserve(organizer, baseDate);
        startReservationAt(reservationId, now.plus(1, ChronoUnit.MINUTES));

        requestCancellation(organizer, reservationId).andExpect(status().isOk());

        assertThat(statusOf(reservationId))
                .isEqualTo(ReservationStatus.CANCELLATION_REQUESTED);
    }

    @Test
    void shouldNotAllowRequestingCancellationExactlyAtTheStart() throws Exception {
        Instant now = freezeClockAtWholeMinute();
        long reservationId = reserve(organizer, baseDate);
        startReservationAt(reservationId, now);

        requestCancellation(organizer, reservationId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Reservation has already started"));

        assertThat(statusOf(reservationId)).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void shouldNotAllowRequestingCancellationOneMinuteAfterTheStart() throws Exception {
        Instant now = freezeClockAtWholeMinute();
        long reservationId = reserve(organizer, baseDate);
        startReservationAt(reservationId, now.minus(1, ChronoUnit.MINUTES));

        requestCancellation(organizer, reservationId).andExpect(status().isConflict());

        assertThat(statusOf(reservationId)).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void shouldLetAdminApproveRequestByCancellingAndFreeTheSlot() throws Exception {
        long reservationId = reserve(organizer, baseDate);
        requestCancellation(organizer, reservationId).andExpect(status().isOk());

        adminPost(admin, reservationId, "/cancel")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        onlineReservation(otherOrganizer, baseDate)
                .andExpect(status().isCreated());
    }

    @Test
    void shouldLetAdminRefuseRequestAndKeepReservationConfirmed() throws Exception {
        long reservationId = reserve(organizer, baseDate);
        requestCancellation(organizer, reservationId).andExpect(status().isOk());

        adminPost(admin, reservationId, "/refuse-cancellation")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        // Depois de recusado, o organizador pode pedir de novo.
        requestCancellation(organizer, reservationId).andExpect(status().isOk());
    }

    @Test
    void shouldRejectRefusalWithoutPendingRequest() throws Exception {
        long reservationId = reserve(organizer, baseDate);

        adminPost(admin, reservationId, "/refuse-cancellation")
                .andExpect(status().isConflict());
    }

    @Test
    void shouldNotCompleteReservationWithPendingCancellationRequest() throws Exception {
        long reservationId = reserve(organizer, baseDate);
        requestCancellation(organizer, reservationId).andExpect(status().isOk());
        jdbcTemplate.update(
                "UPDATE reservations SET reservation_date = ? WHERE id = ?",
                java.sql.Date.valueOf(LocalDate.now(clock).minusDays(7)),
                reservationId
        );

        adminPost(admin, reservationId, "/complete")
                .andExpect(status().isConflict());

        assertThat(statusOf(reservationId)).isEqualTo(ReservationStatus.CANCELLATION_REQUESTED);
    }

    @Test
    void shouldKeepRefusalRestrictedToTheEstablishmentAdmin() throws Exception {
        long reservationId = reserve(organizer, baseDate);
        requestCancellation(organizer, reservationId).andExpect(status().isOk());

        adminPost(otherAdmin, reservationId, "/refuse-cancellation")
                .andExpect(status().isNotFound());
        adminPost(otherAdmin, reservationId, "/cancel")
                .andExpect(status().isNotFound());
        adminPost(organizer, reservationId, "/refuse-cancellation")
                .andExpect(status().isForbidden());

        assertThat(statusOf(reservationId)).isEqualTo(ReservationStatus.CANCELLATION_REQUESTED);
    }

    // ---------- Apoio ----------

    private ResultActions requestCancellation(User user, long reservationId) throws Exception {
        return mockMvc.perform(post("/me/reservations/{id}/cancellation-request", reservationId)
                .header("Authorization", bearer(user)));
    }

    private ResultActions adminPost(User user, long reservationId, String action)
            throws Exception {
        return mockMvc.perform(post(slotPath() + "/reservations/{id}" + action, reservationId)
                .header("Authorization", bearer(user)));
    }

    private ResultActions onlineReservation(User user, LocalDate date) throws Exception {
        return mockMvc.perform(post(slotPath() + "/reservations")
                .header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reservationDate\": \"%s\"}".formatted(date)));
    }

    private long reserve(User user, LocalDate date) throws Exception {
        String response = onlineReservation(user, date)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Number id = JsonPath.read(response, "$.id");

        return id.longValue();
    }

    /**
     * Fixa o relógio da aplicação no minuto cheio da hora real (no máximo 1 min no passado, então o
     * token emitido segue válido para o decoder, que usa o relógio do sistema).
     */
    private Instant freezeClockAtWholeMinute() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        TEST_CLOCK.set(now);

        return now;
    }

    /**
     * Move a reserva para começar em {@code start}. O fim vai para 23:59:59 só para respeitar o
     * CHECK {@code end_time > start_time} do banco (o prazo olha apenas a data e o início).
     */
    private void startReservationAt(long reservationId, Instant start) {
        LocalDateTime local = LocalDateTime.ofInstant(start, SAO_PAULO);

        jdbcTemplate.update(
                "UPDATE reservations SET reservation_date = ?, start_time = ?, end_time = ? WHERE id = ?",
                java.sql.Date.valueOf(local.toLocalDate()),
                java.sql.Time.valueOf(local.toLocalTime()),
                java.sql.Time.valueOf(LocalTime.of(23, 59, 59)),
                reservationId
        );
    }

    private ReservationStatus statusOf(long reservationId) {
        return reservationRepository.findById(reservationId).orElseThrow().getStatus();
    }

    private String slotPath() {
        return "/establishments/%d/sports-fields/%d/schedule-slots/%d".formatted(
                establishment.getId(), sportField.getId(), scheduleSlot.getId());
    }

    private User user(String email, UserRole role) {
        return userService.createUser("Usuario " + email, email, "11999991234", PASSWORD, role);
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenService.issue(user).accessToken();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestClockConfiguration {

        @Bean
        @Primary
        Clock testClock() {
            return TEST_CLOCK;
        }
    }
}
