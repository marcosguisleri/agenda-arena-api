package br.dev.guisleri.agendaarena.reservation;

import br.dev.guisleri.agendaarena.establishment.entity.Establishment;
import br.dev.guisleri.agendaarena.establishment.repository.EstablishmentRepository;
import br.dev.guisleri.agendaarena.reservation.entity.RecurringReservation;
import br.dev.guisleri.agendaarena.reservation.entity.Reservation;
import br.dev.guisleri.agendaarena.reservation.repository.RecurringReservationRepository;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Listagens do administrador: reservas e séries recorrentes de um estabelecimento. O
 * {@code MockMvc} autoconfigurado já chama como PLATFORM_ADMIN; a autorização por
 * estabelecimento (404/403/401) é coberta em {@code EstablishmentAuthorizationIntegrationTests}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(AdminReservationListIntegrationTests.TestClockConfiguration.class)
class AdminReservationListIntegrationTests {

    private static final String RESERVATIONS = "/establishments/{establishmentId}/reservations";
    private static final String RECURRING =
            "/establishments/{establishmentId}/recurring-reservations";
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    // Relógio fixo: segunda-feira 2026-10-19, 18:30 em São Paulo.
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 19);

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

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

    private Establishment establishment;
    private Establishment otherEstablishment;
    private SportField field1;
    private SportField field2;
    private SportField otherField;
    private ScheduleSlot slot1a;
    private ScheduleSlot slot1b;
    private ScheduleSlot slot2;
    private ScheduleSlot otherSlot;
    private User maria;

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

        establishment = establishmentRepository.save(new Establishment("Arena Central"));
        otherEstablishment = establishmentRepository.save(new Establishment("Arena Norte"));
        field1 = sportFieldRepository.save(
                new SportField(establishment, "Campo 1", SportType.SOCIETY));
        field2 = sportFieldRepository.save(
                new SportField(establishment, "Campo 2", SportType.SOCIETY));
        otherField = sportFieldRepository.save(
                new SportField(otherEstablishment, "Campo Norte", SportType.SOCIETY));
        slot1a = slot(field1, LocalTime.of(19, 0));
        slot1b = slot(field1, LocalTime.of(20, 0));
        slot2 = slot(field2, LocalTime.of(19, 0));
        otherSlot = slot(otherField, LocalTime.of(19, 0));
        maria = userRepository.save(
                new User("Maria da Silva", "maria@example.com", "11999991234", "password-hash"));
    }

    // ---------- Reservas ----------

    @Nested
    @DisplayName("GET /establishments/{id}/reservations")
    class ListReservations {

        private Reservation r1;
        private Reservation r2;
        private Reservation r3;
        private Reservation r4;
        private Reservation r5;
        private Reservation r6;
        private Reservation r7;

        @BeforeEach
        void createReservations() {
            r1 = save(Reservation.online(maria, slot1a, TODAY));
            r2 = save(Reservation.manual(slot1b, TODAY, "Joao Souza", "11988887777"));
            Reservation requested = Reservation.online(maria, slot2, TODAY.plusDays(7));
            requested.requestCancellation();
            r3 = save(requested);
            Reservation cancelled = Reservation.online(maria, slot1a, TODAY.plusDays(14));
            cancelled.cancel();
            r4 = save(cancelled);
            r5 = save(Reservation.online(maria, slot1a, TODAY.plusDays(28)));
            r6 = save(Reservation.online(maria, slot1a, TODAY.plusDays(35)));
            r7 = save(Reservation.online(maria, slot1a, TODAY.minusDays(7)));

            // De outro estabelecimento: nunca pode aparecer.
            save(Reservation.online(maria, otherSlot, TODAY));
        }

        @Test
        void defaultsToTodayPlus30DaysInDateAndTimeOrder() throws Exception {
            assertThat(idsOf(list(RESERVATIONS)))
                    .containsExactly(r1.getId(), r2.getId(), r3.getId(), r4.getId(), r5.getId());
        }

        @Test
        void returnsContactAndFieldOfEachReservation() throws Exception {
            list(RESERVATIONS)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(r1.getId()))
                    .andExpect(jsonPath("$.content[0].origin").value("ONLINE"))
                    .andExpect(jsonPath("$.content[0].status").value("CONFIRMED"))
                    .andExpect(jsonPath("$.content[0].reservationDate").value("2026-10-19"))
                    .andExpect(jsonPath("$.content[0].startTime").value("19:00:00"))
                    .andExpect(jsonPath("$.content[0].endTime").value("20:00:00"))
                    .andExpect(jsonPath("$.content[0].price").value(150.50))
                    .andExpect(jsonPath("$.content[0].scheduleSlotId").value(slot1a.getId()))
                    .andExpect(jsonPath("$.content[0].sportField.id").value(field1.getId()))
                    .andExpect(jsonPath("$.content[0].sportField.name").value("Campo 1"))
                    .andExpect(jsonPath("$.content[0].organizerId").value(maria.getId()))
                    .andExpect(jsonPath("$.content[0].customerName").value("Maria da Silva"))
                    .andExpect(jsonPath("$.content[0].customerPhone").value("11999991234"))
                    .andExpect(jsonPath("$.content[1].id").value(r2.getId()))
                    .andExpect(jsonPath("$.content[1].origin").value("MANUAL"))
                    .andExpect(jsonPath("$.content[1].organizerId").value(nullValue()))
                    .andExpect(jsonPath("$.content[1].customerName").value("Joao Souza"))
                    .andExpect(jsonPath("$.content[1].customerPhone").value("11988887777"));
        }

        @Test
        void neverIncludesReservationsOfAnotherEstablishment() throws Exception {
            ResultActions other = list(RESERVATIONS, otherEstablishment.getId());

            assertThat(idsOf(other)).hasSize(1);
            assertThat(idsOf(list(RESERVATIONS)))
                    .doesNotContainAnyElementsOf(idsOf(list(RESERVATIONS, otherEstablishment.getId())));
        }

        @Test
        void acceptsAnExplicitPeriodIncludingThePast() throws Exception {
            assertThat(idsOf(list(RESERVATIONS, "from", "2026-10-12", "to", "2026-11-23")))
                    .containsExactly(r7.getId(), r1.getId(), r2.getId(), r3.getId(),
                            r4.getId(), r5.getId(), r6.getId());
        }

        @Test
        void completesAMissingEndOrStartWith30Days() throws Exception {
            assertThat(idsOf(list(RESERVATIONS, "from", "2026-11-16")))
                    .containsExactly(r5.getId(), r6.getId());
            assertThat(idsOf(list(RESERVATIONS, "to", "2026-10-19")))
                    .containsExactly(r7.getId(), r1.getId(), r2.getId());
        }

        @Test
        void filtersBySportField() throws Exception {
            assertThat(idsOf(list(RESERVATIONS, "sportFieldId", field2.getId().toString())))
                    .containsExactly(r3.getId());
        }

        @Test
        void filtersByOneOrMoreStatuses() throws Exception {
            assertThat(idsOf(list(RESERVATIONS, "status", "CANCELLATION_REQUESTED")))
                    .containsExactly(r3.getId());
            assertThat(idsOf(list(RESERVATIONS,
                    "status", "CANCELLED", "status", "CANCELLATION_REQUESTED")))
                    .containsExactly(r3.getId(), r4.getId());
        }

        @Test
        void filtersByOrigin() throws Exception {
            assertThat(idsOf(list(RESERVATIONS, "origin", "MANUAL")))
                    .containsExactly(r2.getId());
            assertThat(idsOf(list(RESERVATIONS, "origin", "ONLINE")))
                    .containsExactly(r1.getId(), r3.getId(), r4.getId(), r5.getId());
        }

        @Test
        void combinesFilters() throws Exception {
            assertThat(idsOf(list(RESERVATIONS,
                    "sportFieldId", field1.getId().toString(),
                    "origin", "ONLINE",
                    "status", "CONFIRMED")))
                    .containsExactly(r1.getId(), r5.getId());
        }

        @Test
        void paginatesWithStableOrder() throws Exception {
            list(RESERVATIONS, "size", "2")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(2))
                    .andExpect(jsonPath("$.totalElements").value(5))
                    .andExpect(jsonPath("$.totalPages").value(3));

            assertThat(idsOf(list(RESERVATIONS, "size", "2", "page", "0")))
                    .containsExactly(r1.getId(), r2.getId());
            assertThat(idsOf(list(RESERVATIONS, "size", "2", "page", "1")))
                    .containsExactly(r3.getId(), r4.getId());
            assertThat(idsOf(list(RESERVATIONS, "size", "2", "page", "2")))
                    .containsExactly(r5.getId());
            list(RESERVATIONS, "size", "2", "page", "3")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isEmpty())
                    .andExpect(jsonPath("$.totalElements").value(5));
        }

        @Test
        void returnsEmptyPageWhenNothingMatches() throws Exception {
            list(RESERVATIONS, "from", "2027-03-01", "to", "2027-03-31")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isEmpty())
                    .andExpect(jsonPath("$.totalElements").value(0))
                    .andExpect(jsonPath("$.totalPages").value(0));
        }

        @Test
        void acceptsAPeriodOf92DaysAndRejects93() throws Exception {
            list(RESERVATIONS, "from", "2026-10-19", "to", "2027-01-18")
                    .andExpect(status().isOk());
            list(RESERVATIONS, "from", "2026-10-19", "to", "2027-01-19")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.to").value("Period cannot exceed 92 days"));
        }

        @Test
        void returnsNotFoundForSportFieldOfAnotherEstablishment() throws Exception {
            list(RESERVATIONS, "sportFieldId", otherField.getId().toString())
                    .andExpect(status().isNotFound());
            list(RESERVATIONS, "sportFieldId", "999999")
                    .andExpect(status().isNotFound());
        }

        @Test
        void returnsNotFoundForUnknownEstablishment() throws Exception {
            list(RESERVATIONS, 999999L).andExpect(status().isNotFound());
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("br.dev.guisleri.agendaarena.reservation.AdminReservationListIntegrationTests#invalidReservationQueries")
        void rejectsInvalidQuery(String name, String[] params, String field) throws Exception {
            list(RESERVATIONS, params)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.errors." + field).isNotEmpty());
        }

        @Test
        void rejectsUnknownStatusAndMalformedDateWithTheStandardErrorBody() throws Exception {
            list(RESERVATIONS, "status", "NOPE")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("Bad Request"));
            list(RESERVATIONS, "from", "not-a-date")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("Bad Request"));
        }

        @Test
        void doesNotChangeAnything() throws Exception {
            long before = reservationRepository.count();

            list(RESERVATIONS).andExpect(status().isOk());

            assertThat(reservationRepository.count()).isEqualTo(before);
        }
    }

    static Stream<Arguments> invalidReservationQueries() {
        return Stream.of(
                Arguments.of("from after to",
                        new String[]{"from", "2026-10-20", "to", "2026-10-19"}, "to"),
                Arguments.of("size zero", new String[]{"size", "0"}, "size"),
                Arguments.of("size above the maximum", new String[]{"size", "101"}, "size"),
                Arguments.of("negative page", new String[]{"page", "-1"}, "page")
        );
    }

    // ---------- Recorrências ----------

    @Nested
    @DisplayName("GET /establishments/{id}/recurring-reservations")
    class ListRecurringReservations {

        private RecurringReservation c1;
        private RecurringReservation c2;
        private RecurringReservation c3;
        private RecurringReservation c4;

        @BeforeEach
        void createSeries() {
            // Em andamento, sem fim, com organizador.
            c1 = save(new RecurringReservation(slot1a, maria, "Maria da Silva", "11999991234",
                    LocalDate.of(2026, 10, 5), null));
            // Começa hoje, com fim, sem organizador vinculado.
            c2 = save(new RecurringReservation(slot1b, null, "Time do Joao", "11988887777",
                    TODAY, LocalDate.of(2026, 12, 28)));
            // Futura, em outro campo.
            c3 = save(new RecurringReservation(slot2, maria, "Maria da Silva", "11999991234",
                    LocalDate.of(2026, 11, 2), null));
            // Encerrada (no mesmo horário de c2, antes dela).
            c4 = save(new RecurringReservation(slot1b, null, "Turma antiga", "11977776666",
                    LocalDate.of(2026, 8, 31), LocalDate.of(2026, 10, 12)));

            // De outro estabelecimento: nunca pode aparecer.
            save(new RecurringReservation(otherSlot, null, "Outro", "11966665555",
                    TODAY, null));
        }

        @Test
        void defaultsToActiveAndUpcomingNewestFirst() throws Exception {
            assertThat(idsOf(list(RECURRING)))
                    .containsExactly(c3.getId(), c2.getId(), c1.getId());
        }

        @Test
        void returnsSeriesDetails() throws Exception {
            list(RECURRING)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(c3.getId()))
                    .andExpect(jsonPath("$.content[0].status").value("UPCOMING"))
                    .andExpect(jsonPath("$.content[0].scheduleSlotId").value(slot2.getId()))
                    .andExpect(jsonPath("$.content[0].sportField.id").value(field2.getId()))
                    .andExpect(jsonPath("$.content[0].sportField.name").value("Campo 2"))
                    .andExpect(jsonPath("$.content[0].dayOfWeek").value("MONDAY"))
                    .andExpect(jsonPath("$.content[0].startTime").value("19:00:00"))
                    .andExpect(jsonPath("$.content[0].endTime").value("20:00:00"))
                    .andExpect(jsonPath("$.content[0].price").value(150.50))
                    .andExpect(jsonPath("$.content[0].startDate").value("2026-11-02"))
                    .andExpect(jsonPath("$.content[0].endDate").value(nullValue()))
                    .andExpect(jsonPath("$.content[0].organizerId").value(maria.getId()))
                    .andExpect(jsonPath("$.content[0].responsibleName").value("Maria da Silva"))
                    .andExpect(jsonPath("$.content[0].responsiblePhone").value("11999991234"))
                    .andExpect(jsonPath("$.content[1].id").value(c2.getId()))
                    .andExpect(jsonPath("$.content[1].status").value("ACTIVE"))
                    .andExpect(jsonPath("$.content[1].organizerId").value(nullValue()));
        }

        @Test
        void neverIncludesSeriesOfAnotherEstablishment() throws Exception {
            assertThat(idsOf(list(RECURRING, otherEstablishment.getId()))).hasSize(1);
            assertThat(idsOf(list(RECURRING)))
                    .doesNotContainAnyElementsOf(
                            idsOf(list(RECURRING, otherEstablishment.getId())));
        }

        @Test
        void filtersByStatus() throws Exception {
            assertThat(idsOf(list(RECURRING, "status", "ENDED"))).containsExactly(c4.getId());
            assertThat(idsOf(list(RECURRING, "status", "UPCOMING")))
                    .containsExactly(c3.getId());
            assertThat(idsOf(list(RECURRING, "status", "ACTIVE", "status", "ENDED")))
                    .containsExactly(c2.getId(), c1.getId(), c4.getId());
            list(RECURRING, "status", "ENDED")
                    .andExpect(jsonPath("$.content[0].status").value("ENDED"));
        }

        @Test
        void filtersBySportField() throws Exception {
            assertThat(idsOf(list(RECURRING, "sportFieldId", field2.getId().toString())))
                    .containsExactly(c3.getId());
        }

        @Test
        void filtersBySeriesWithOrWithoutLinkedOrganizer() throws Exception {
            assertThat(idsOf(list(RECURRING, "hasOrganizer", "false")))
                    .containsExactly(c2.getId());
            assertThat(idsOf(list(RECURRING, "hasOrganizer", "true")))
                    .containsExactly(c3.getId(), c1.getId());
        }

        @Test
        void paginates() throws Exception {
            list(RECURRING, "size", "2")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(3))
                    .andExpect(jsonPath("$.totalPages").value(2));

            assertThat(idsOf(list(RECURRING, "size", "2", "page", "0")))
                    .containsExactly(c3.getId(), c2.getId());
            assertThat(idsOf(list(RECURRING, "size", "2", "page", "1")))
                    .containsExactly(c1.getId());
        }

        @Test
        void returnsNotFoundForSportFieldOfAnotherEstablishmentAndUnknownEstablishment()
                throws Exception {
            list(RECURRING, "sportFieldId", otherField.getId().toString())
                    .andExpect(status().isNotFound());
            list(RECURRING, 999999L).andExpect(status().isNotFound());
        }

        @Test
        void rejectsInvalidPageParametersAndUnknownStatus() throws Exception {
            list(RECURRING, "size", "0")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.size").isNotEmpty());
            list(RECURRING, "size", "101")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.size").isNotEmpty());
            list(RECURRING, "page", "-1")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.page").isNotEmpty());
            list(RECURRING, "status", "NOPE")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Bad Request"));
        }
    }

    // ---------- Apoio ----------

    private ScheduleSlot slot(SportField sportField, LocalTime startTime) {
        return scheduleSlotRepository.save(
                new ScheduleSlot(sportField, DayOfWeek.MONDAY, startTime, new BigDecimal("150.50")));
    }

    private Reservation save(Reservation reservation) {
        return reservationRepository.saveAndFlush(reservation);
    }

    private RecurringReservation save(RecurringReservation recurringReservation) {
        return recurringReservationRepository.saveAndFlush(recurringReservation);
    }

    private ResultActions list(String path, String... params) throws Exception {
        return list(path, establishment.getId(), params);
    }

    private ResultActions list(String path, Long establishmentId, String... params)
            throws Exception {
        MockHttpServletRequestBuilder request = get(path, establishmentId);

        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }

        return mockMvc.perform(request);
    }

    private List<Long> idsOf(ResultActions result) throws Exception {
        String body = result.andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<Number> ids = JsonPath.read(body, "$.content[*].id");

        return ids.stream().map(Number::longValue).toList();
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
