package br.dev.guisleri.agendaarena.reservation;

import br.dev.guisleri.agendaarena.establishment.entity.Establishment;
import br.dev.guisleri.agendaarena.establishment.repository.EstablishmentRepository;
import br.dev.guisleri.agendaarena.reservation.entity.RecurringReservation;
import br.dev.guisleri.agendaarena.reservation.exception.RecurringReservationConflictException;
import br.dev.guisleri.agendaarena.reservation.repository.RecurringReservationRepository;
import br.dev.guisleri.agendaarena.reservation.service.RecurringReservationService;
import br.dev.guisleri.agendaarena.schedule.entity.ScheduleSlot;
import br.dev.guisleri.agendaarena.schedule.repository.ScheduleSlotRepository;
import br.dev.guisleri.agendaarena.sportfield.entity.SportField;
import br.dev.guisleri.agendaarena.sportfield.model.SportType;
import br.dev.guisleri.agendaarena.sportfield.repository.SportFieldRepository;
import br.dev.guisleri.agendaarena.user.entity.User;
import br.dev.guisleri.agendaarena.user.model.UserRole;
import br.dev.guisleri.agendaarena.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "Minhas recorrências", próximas ocorrências e vínculo de organizador pelo administrador.
 * Relógio fixo: segunda-feira 2026-10-19, 18:30 em São Paulo (o slot das 19:00 ainda não começou).
 */
@SpringBootTest
@Testcontainers
@Import(MyRecurringReservationIntegrationTests.TestClockConfiguration.class)
class MyRecurringReservationIntegrationTests {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 19);
    private static final LocalDate NEXT_MONDAY = TODAY.plusWeeks(1);
    private static final LocalDate PAST_MONDAY = TODAY.minusWeeks(1);
    private static final Duration CONCURRENCY_TIMEOUT = Duration.ofSeconds(10);
    private static final String LINK_PATH =
            "/establishments/{e}/sports-fields/{f}/schedule-slots/{s}/recurring-reservations/{r}";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private RecurringReservationService recurringReservationService;
    @Autowired
    private RecurringReservationRepository recurringReservationRepository;
    @Autowired
    private ScheduleSlotRepository scheduleSlotRepository;
    @Autowired
    private SportFieldRepository sportFieldRepository;
    @Autowired
    private EstablishmentRepository establishmentRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;
    private ExecutorService executor;
    private Establishment establishment;
    private SportField sportField;
    private ScheduleSlot eveningSlot;
    private ScheduleSlot earlySlot;
    private ScheduleSlot tuesdaySlot;
    private User organizer;
    private User otherOrganizer;

    @DynamicPropertySource
    static void configurePostgreSQL(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE establishments, users RESTART IDENTITY CASCADE");

        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        establishment = establishmentRepository.save(new Establishment("Arena Central"));
        sportField = sportFieldRepository.save(
                new SportField(establishment, "Campo 1", SportType.SOCIETY));
        eveningSlot = slot(DayOfWeek.MONDAY, LocalTime.of(19, 0), "150.50");
        earlySlot = slot(DayOfWeek.MONDAY, LocalTime.of(18, 0), "120.00");
        tuesdaySlot = slot(DayOfWeek.TUESDAY, LocalTime.of(19, 0), "100.00");

        organizer = user("organizer@example.com");
        otherOrganizer = user("other.organizer@example.com");

        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void stopExecutor() throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    @Nested
    @DisplayName("GET /me/recurring-reservations")
    class MyRecurringReservations {

        @Test
        void shouldListOnlyMySeriesNewestStartFirstWithContext() throws Exception {
            RecurringReservation first = series(eveningSlot, organizer, TODAY, TODAY.plusWeeks(2));
            RecurringReservation second = series(eveningSlot, organizer, TODAY.plusWeeks(3), null);
            series(earlySlot, otherOrganizer, TODAY, null);
            series(tuesdaySlot, null, TODAY.plusDays(1), null);

            asOrganizer(organizer, get("/me/recurring-reservations"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].id").value(second.getId()))
                    .andExpect(jsonPath("$[1].id").value(first.getId()))
                    .andExpect(jsonPath("$[0].establishmentName").value("Arena Central"))
                    .andExpect(jsonPath("$[0].sportFieldName").value("Campo 1"))
                    .andExpect(jsonPath("$[0].scheduleSlotId").value(eveningSlot.getId()))
                    .andExpect(jsonPath("$[0].dayOfWeek").value("MONDAY"))
                    .andExpect(jsonPath("$[0].startTime").value("19:00:00"))
                    .andExpect(jsonPath("$[0].endTime").value("20:00:00"))
                    .andExpect(jsonPath("$[0].price").value(150.50))
                    .andExpect(jsonPath("$[0].startDate").value(TODAY.plusWeeks(3).toString()))
                    .andExpect(jsonPath("$[0].endDate").doesNotExist())
                    .andExpect(jsonPath("$[1].endDate").value(TODAY.plusWeeks(2).toString()));
        }

        @Test
        void shouldReturnEmptyListWhenUserHasNoSeries() throws Exception {
            series(eveningSlot, otherOrganizer, TODAY, null);

            asOrganizer(organizer, get("/me/recurring-reservations"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        void shouldRequireAuthentication() throws Exception {
            mockMvc.perform(get("/me/recurring-reservations"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /me/recurring-reservations/{id}/occurrences")
    class Occurrences {

        @Test
        void shouldListEightWeeksStartingTodayWhenTheSlotHasNotStarted() throws Exception {
            RecurringReservation series = series(eveningSlot, organizer, TODAY, null);

            asOrganizer(organizer, get(occurrencesPath(series)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(8))
                    .andExpect(jsonPath("$[0].date").value("2026-10-19"))
                    .andExpect(jsonPath("$[7].date").value("2026-12-07"))
                    .andExpect(jsonPath("$[0].released").value(false))
                    .andExpect(jsonPath("$[0].matchId").doesNotExist())
                    .andExpect(jsonPath("$[0].matchPublicToken").doesNotExist());
        }

        @Test
        void shouldSkipTodayWhenTheSlotAlreadyStarted() throws Exception {
            RecurringReservation series = series(earlySlot, organizer, PAST_MONDAY, null);

            asOrganizer(organizer, get(occurrencesPath(series)).param("weeks", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].date").value(NEXT_MONDAY.toString()))
                    .andExpect(jsonPath("$[1].date").value(NEXT_MONDAY.plusWeeks(1).toString()));
        }

        @Test
        void shouldStartAtTheSeriesStartDateWhenItIsInTheFuture() throws Exception {
            RecurringReservation series = series(
                    eveningSlot, organizer, TODAY.plusWeeks(3), null);

            asOrganizer(organizer, get(occurrencesPath(series)).param("weeks", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].date").value(TODAY.plusWeeks(3).toString()));
        }

        @Test
        void shouldStopAtTheSeriesEndDate() throws Exception {
            RecurringReservation series = series(
                    eveningSlot, organizer, TODAY, TODAY.plusWeeks(2));

            asOrganizer(organizer, get(occurrencesPath(series)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(3))
                    .andExpect(jsonPath("$[2].date").value(TODAY.plusWeeks(2).toString()));
        }

        @Test
        void shouldReturnEmptyListWhenTheSeriesAlreadyEnded() throws Exception {
            RecurringReservation series = series(
                    eveningSlot, organizer, PAST_MONDAY.minusWeeks(2), PAST_MONDAY);

            asOrganizer(organizer, get(occurrencesPath(series)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        void shouldFlagReleasedOccurrencesOnly() throws Exception {
            RecurringReservation series = series(eveningSlot, organizer, TODAY, null);
            jdbcTemplate.update("""
                    INSERT INTO recurring_reservations_releases
                        (recurring_reservation_id, occurrence_date, released_at)
                    VALUES (?, ?, now())
                    """, series.getId(), NEXT_MONDAY);

            asOrganizer(organizer, get(occurrencesPath(series)).param("weeks", "3"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].released").value(false))
                    .andExpect(jsonPath("$[1].date").value(NEXT_MONDAY.toString()))
                    .andExpect(jsonPath("$[1].released").value(true))
                    .andExpect(jsonPath("$[2].released").value(false));
        }

        @Test
        void shouldShowTheMatchOfAnOccurrence() throws Exception {
            RecurringReservation series = series(eveningSlot, organizer, TODAY, null);
            long matchId = insertOccurrenceMatch(series, NEXT_MONDAY, "A");

            asOrganizer(organizer, get(occurrencesPath(series)).param("weeks", "3"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].matchId").doesNotExist())
                    .andExpect(jsonPath("$[1].matchId").value(matchId))
                    .andExpect(jsonPath("$[1].matchPublicToken").value("A".repeat(43)))
                    .andExpect(jsonPath("$[2].matchId").doesNotExist());
        }

        @ParameterizedTest(name = "weeks={0} é rejeitado")
        @ValueSource(strings = {"0", "-1", "27"})
        void shouldRejectWeeksOutOfRange(String weeks) throws Exception {
            RecurringReservation series = series(eveningSlot, organizer, TODAY, null);

            asOrganizer(organizer, get(occurrencesPath(series)).param("weeks", weeks))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void shouldRejectNonNumericWeeksWithTheStandardErrorBody() throws Exception {
            RecurringReservation series = series(eveningSlot, organizer, TODAY, null);

            asOrganizer(organizer, get(occurrencesPath(series)).param("weeks", "abc"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("Bad Request"))
                    .andExpect(jsonPath("$.message").value("Invalid value for parameter 'weeks'"));
        }

        @Test
        void shouldHideSeriesOfAnotherOrganizer() throws Exception {
            RecurringReservation series = series(eveningSlot, otherOrganizer, TODAY, null);

            asOrganizer(organizer, get(occurrencesPath(series)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldHideSeriesWithoutOrganizer() throws Exception {
            RecurringReservation series = series(tuesdaySlot, null, TODAY.plusDays(1), null);

            asOrganizer(organizer, get(occurrencesPath(series)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldReturnNotFoundForUnknownSeries() throws Exception {
            asOrganizer(organizer, get("/me/recurring-reservations/999999/occurrences"))
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldRequireAuthentication() throws Exception {
            RecurringReservation series = series(eveningSlot, organizer, TODAY, null);

            mockMvc.perform(get(occurrencesPath(series)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("PATCH .../recurring-reservations/{id} (vincular organizador)")
    class LinkOrganizer {

        @Test
        void shouldLinkOrganizerToSeriesWithoutOne() throws Exception {
            RecurringReservation series = series(eveningSlot, null, TODAY, null);

            link(series, organizer.getId())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(series.getId()))
                    .andExpect(jsonPath("$.organizerId").value(organizer.getId()));

            assertThat(organizerOf(series)).isEqualTo(organizer.getId());

            // A partir daqui a série aparece em "minhas recorrências" do organizador.
            asOrganizer(organizer, get("/me/recurring-reservations"))
                    .andExpect(jsonPath("$.length()").value(1));
        }

        @Test
        void shouldBeIdempotentWhenLinkingTheSameOrganizerAgain() throws Exception {
            RecurringReservation series = series(eveningSlot, organizer, TODAY, null);
            insertOccurrenceMatch(series, NEXT_MONDAY, "A");

            link(series, organizer.getId())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.organizerId").value(organizer.getId()));

            assertThat(organizerOf(series)).isEqualTo(organizer.getId());
        }

        @Test
        void shouldChangeOrganizerWhenTheSeriesHasNoMatches() throws Exception {
            RecurringReservation series = series(eveningSlot, organizer, TODAY, null);

            link(series, otherOrganizer.getId())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.organizerId").value(otherOrganizer.getId()));

            assertThat(organizerOf(series)).isEqualTo(otherOrganizer.getId());
        }

        @Test
        void shouldRejectChangingOrganizerWhenTheSeriesAlreadyHasMatches() throws Exception {
            RecurringReservation series = series(eveningSlot, organizer, TODAY, null);
            insertOccurrenceMatch(series, NEXT_MONDAY, "A");

            link(series, otherOrganizer.getId())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.error").value("Conflict"));

            assertThat(organizerOf(series)).isEqualTo(organizer.getId());
        }

        @Test
        void shouldReturnNotFoundForUnknownOrganizer() throws Exception {
            RecurringReservation series = series(eveningSlot, null, TODAY, null);

            link(series, 999_999L).andExpect(status().isNotFound());

            assertThat(organizerOf(series)).isNull();
        }

        @Test
        void shouldRejectInactiveOrganizer() throws Exception {
            RecurringReservation series = series(eveningSlot, null, TODAY, null);
            jdbcTemplate.update("UPDATE users SET active = false WHERE id = ?", organizer.getId());

            link(series, organizer.getId()).andExpect(status().isBadRequest());

            assertThat(organizerOf(series)).isNull();
        }

        @ParameterizedTest(name = "usuário {0} não pode ser vinculado como organizador")
        @ValueSource(strings = {"ESTABLISHMENT_ADMIN", "PLATFORM_ADMIN"})
        void shouldRejectUserThatIsNotAnOrganizer(String role) throws Exception {
            RecurringReservation series = series(eveningSlot, null, TODAY, null);
            jdbcTemplate.update("UPDATE users SET role = ? WHERE id = ?", role, organizer.getId());

            link(series, organizer.getId())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("User is not an organizer"));

            assertThat(organizerOf(series)).isNull();
        }

        @Test
        void shouldKeepCurrentOrganizerWhenTheNewUserIsNotAnOrganizer() throws Exception {
            RecurringReservation series = series(eveningSlot, organizer, TODAY, null);
            jdbcTemplate.update(
                    "UPDATE users SET role = 'ESTABLISHMENT_ADMIN' WHERE id = ?",
                    otherOrganizer.getId());

            link(series, otherOrganizer.getId()).andExpect(status().isBadRequest());

            assertThat(organizerOf(series)).isEqualTo(organizer.getId());
        }

        @Test
        void shouldRequireOrganizerId() throws Exception {
            RecurringReservation series = series(eveningSlot, null, TODAY, null);

            mockMvc.perform(withPlatformAdmin(
                            patch(LINK_PATH, establishment.getId(), sportField.getId(),
                                    eveningSlot.getId(), series.getId())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{}")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.organizerId").isNotEmpty());
        }

        @Test
        void shouldReturnNotFoundWhenSeriesBelongsToAnotherSlot() throws Exception {
            RecurringReservation series = series(eveningSlot, null, TODAY, null);

            mockMvc.perform(withPlatformAdmin(
                            patch(LINK_PATH, establishment.getId(), sportField.getId(),
                                    earlySlot.getId(), series.getId())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body(organizer.getId()))))
                    .andExpect(status().isNotFound());

            assertThat(organizerOf(series)).isNull();
        }
    }

    @Nested
    @DisplayName("Corrida no lock do slot")
    class SlotLockRace {

        @Test
        void shouldWaitForTheSlotLockHeldByAReleaseAndThenLink() throws Exception {
            RecurringReservation series = series(eveningSlot, null, TODAY, null);
            CountDownLatch released = new CountDownLatch(1);
            CountDownLatch allowReleaseCommit = new CountDownLatch(1);

            Future<?> release = executor.submit(() ->
                    inTransaction(() -> {
                        recurringReservationService.releaseOccurrence(
                                establishment.getId(), sportField.getId(), eveningSlot.getId(),
                                series.getId(), NEXT_MONDAY);
                        released.countDown();
                        await(allowReleaseCommit);
                    })
            );
            await(released);

            String waitingConnection = "link-organizer-waiter";
            Future<?> link = executor.submit(() ->
                    inTransaction(() -> {
                        jdbcTemplate.queryForObject(
                                "SELECT set_config('application_name', ?, true)",
                                String.class, waitingConnection);
                        recurringReservationService.linkOrganizer(
                                establishment.getId(), sportField.getId(), eveningSlot.getId(),
                                series.getId(), organizer.getId());
                    })
            );

            awaitConnectionWaitingForLock(waitingConnection);
            // Enquanto o lock está com a liberação, o vínculo ainda não aconteceu.
            assertThat(organizerOf(series)).isNull();

            allowReleaseCommit.countDown();
            release.get(CONCURRENCY_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            link.get(CONCURRENCY_TIMEOUT.toSeconds(), TimeUnit.SECONDS);

            assertThat(organizerOf(series)).isEqualTo(organizer.getId());
            Integer releases = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM recurring_reservations_releases", Integer.class);
            assertThat(releases).isEqualTo(1);
        }

        @Test
        void shouldRejectConcurrentChangeAfterAMatchWasCommitted() throws Exception {
            RecurringReservation series = series(eveningSlot, organizer, TODAY, null);
            CountDownLatch matchInserted = new CountDownLatch(1);
            CountDownLatch allowCommit = new CountDownLatch(1);

            // Quem segura o lock do slot cria a partida (como fará a fatia 4) e só então libera.
            Future<?> creator = executor.submit(() ->
                    inTransaction(() -> {
                        recurringReservationService.releaseOccurrence(
                                establishment.getId(), sportField.getId(), eveningSlot.getId(),
                                series.getId(), TODAY.plusWeeks(5));
                        insertOccurrenceMatch(series, NEXT_MONDAY, "A");
                        matchInserted.countDown();
                        await(allowCommit);
                    })
            );
            await(matchInserted);

            String waitingConnection = "link-organizer-after-match";
            Future<?> change = executor.submit(() ->
                    inTransaction(() -> {
                        jdbcTemplate.queryForObject(
                                "SELECT set_config('application_name', ?, true)",
                                String.class, waitingConnection);
                        recurringReservationService.linkOrganizer(
                                establishment.getId(), sportField.getId(), eveningSlot.getId(),
                                series.getId(), otherOrganizer.getId());
                    })
            );

            awaitConnectionWaitingForLock(waitingConnection);
            allowCommit.countDown();
            creator.get(CONCURRENCY_TIMEOUT.toSeconds(), TimeUnit.SECONDS);

            assertThatThrownBy(() -> change.get(CONCURRENCY_TIMEOUT.toSeconds(), TimeUnit.SECONDS))
                    .hasCauseInstanceOf(RecurringReservationConflictException.class);
            assertThat(organizerOf(series)).isEqualTo(organizer.getId());
        }
    }

    // ---------- Montagem dos cenários ----------

    private ScheduleSlot slot(DayOfWeek day, LocalTime start, String price) {
        return scheduleSlotRepository.save(
                new ScheduleSlot(sportField, day, start, new BigDecimal(price)));
    }

    private User user(String email) {
        return userRepository.save(new User(
                "Usuario " + email, email, "11999991234", "password-hash", UserRole.ORGANIZER));
    }

    private RecurringReservation series(
            ScheduleSlot slot,
            User owner,
            LocalDate startDate,
            LocalDate endDate
    ) {
        return recurringReservationService.createRecurringReservation(
                establishment.getId(),
                sportField.getId(),
                slot.getId(),
                owner != null ? owner.getId() : null,
                "Turma",
                "11999991234",
                startDate,
                endDate
        );
    }

    private long insertOccurrenceMatch(RecurringReservation series, LocalDate date, String token) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO matches (recurring_reservation_id, occurrence_date, organizer_id,
                    schedule_slot_id, match_date, start_time, end_time, price, public_token,
                    status, created_at)
                VALUES (?, ?, ?, ?, ?, '19:00', '20:00', 150.50, ?, 'OPEN', now())
                RETURNING id
                """, Long.class,
                series.getId(), date, organizerOf(series), series.getScheduleSlot().getId(),
                date, token.repeat(43));
    }

    private Long organizerOf(RecurringReservation series) {
        return jdbcTemplate.queryForObject(
                "SELECT organizer_id FROM recurring_reservations WHERE id = ?",
                Long.class, series.getId());
    }

    private static String occurrencesPath(RecurringReservation series) {
        return "/me/recurring-reservations/" + series.getId() + "/occurrences";
    }

    private static String body(Long organizerId) {
        return "{\"organizerId\": " + organizerId + "}";
    }

    private ResultActions link(RecurringReservation series, Long organizerId) throws Exception {
        return mockMvc.perform(withPlatformAdmin(
                patch(LINK_PATH, establishment.getId(), sportField.getId(),
                        series.getScheduleSlot().getId(), series.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(organizerId))));
    }

    private ResultActions asOrganizer(User user, MockHttpServletRequestBuilder request)
            throws Exception {
        return mockMvc.perform(request.with(
                jwt().jwt(token -> token
                                .subject(String.valueOf(user.getId()))
                                .claim("roles", List.of("ORGANIZER")))
                        .authorities(new SimpleGrantedAuthority("ROLE_ORGANIZER"))));
    }

    private static MockHttpServletRequestBuilder withPlatformAdmin(
            MockHttpServletRequestBuilder request
    ) {
        return request.with(
                jwt().jwt(token -> token
                                .subject("0")
                                .claim("roles", List.of("PLATFORM_ADMIN")))
                        .authorities(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN")));
    }

    private void inTransaction(Runnable operation) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                operation.run());
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
                "Connection " + connectionName + " did not wait for the schedule slot lock");
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(CONCURRENCY_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for concurrent operation");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting", exception);
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
