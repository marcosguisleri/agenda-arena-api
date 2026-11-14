package br.dev.guisleri.agendaarena.match;

import br.dev.guisleri.agendaarena.establishment.entity.Establishment;
import br.dev.guisleri.agendaarena.establishment.repository.EstablishmentRepository;
import br.dev.guisleri.agendaarena.match.entity.Match;
import br.dev.guisleri.agendaarena.match.entity.MatchParticipant;
import br.dev.guisleri.agendaarena.match.model.MatchTeam;
import br.dev.guisleri.agendaarena.match.model.PlayerPosition;
import br.dev.guisleri.agendaarena.match.model.ShirtColor;
import br.dev.guisleri.agendaarena.match.repository.MatchParticipantRepository;
import br.dev.guisleri.agendaarena.match.repository.MatchRepository;
import br.dev.guisleri.agendaarena.reservation.entity.RecurringReservation;
import br.dev.guisleri.agendaarena.reservation.entity.RecurringReservationRelease;
import br.dev.guisleri.agendaarena.reservation.entity.Reservation;
import br.dev.guisleri.agendaarena.reservation.repository.RecurringReservationReleaseRepository;
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
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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
 * Visão somente leitura das partidas para o administrador do estabelecimento. O {@code MockMvc}
 * autoconfigurado já chama como PLATFORM_ADMIN; a autorização por estabelecimento (404/403/401) é
 * coberta em {@code EstablishmentAuthorizationIntegrationTests}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(AdminMatchViewIntegrationTests.TestClockConfiguration.class)
class AdminMatchViewIntegrationTests {

    private static final String MATCHES = "/establishments/{establishmentId}/matches";
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    // Relógio fixo: segunda-feira 2026-10-19, 18:30 em São Paulo.
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 19);
    private static final Instant NOW = Instant.parse("2026-10-19T21:30:00Z");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EntityManagerFactory entityManagerFactory;
    @Autowired
    private MatchRepository matchRepository;
    @Autowired
    private MatchParticipantRepository matchParticipantRepository;
    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private RecurringReservationRepository recurringReservationRepository;
    @Autowired
    private RecurringReservationReleaseRepository releaseRepository;
    @Autowired
    private ScheduleSlotRepository scheduleSlotRepository;
    @Autowired
    private SportFieldRepository sportFieldRepository;
    @Autowired
    private EstablishmentRepository establishmentRepository;
    @Autowired
    private UserRepository userRepository;

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
    private User pedro;

    private Match openWithParticipants;
    private Match closed;
    private Match emptyOpen;
    private Match cancelledReservation;
    private Match releasedOccurrence;
    private Match activeOccurrence;
    private Match pastMatch;
    private Match farMatch;
    private Match otherEstablishmentMatch;
    private Reservation openReservation;
    private RecurringReservation series;
    private int tokenCounter;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.open-in-view", () -> false);
        registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> true);
    }

    @BeforeEach
    void setUp() {
        releaseRepository.deleteAllInBatch();
        matchParticipantRepository.deleteAllInBatch();
        matchRepository.deleteAllInBatch();
        recurringReservationRepository.deleteAllInBatch();
        reservationRepository.deleteAllInBatch();
        scheduleSlotRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        sportFieldRepository.deleteAllInBatch();
        establishmentRepository.deleteAllInBatch();
        tokenCounter = 0;

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
                new User("Maria da Silva", "maria@example.com", "11999991234", "hash"));
        pedro = userRepository.save(
                new User("Pedro Souza", "pedro@example.com", "11977776666", "hash"));

        // Aberta, com participantes: 3 de linha (pagam 50,00 cada) e 1 goleiro isento.
        openReservation = reservation(maria, slot1a, TODAY, false);
        openWithParticipants = matchOf(openReservation);
        MatchParticipant joao = participant(openWithParticipants, "Joao", PlayerPosition.FIELD);
        participant(openWithParticipants, "Carlos", PlayerPosition.FIELD);
        participant(openWithParticipants, "Andre", PlayerPosition.FIELD);
        participant(openWithParticipants, "Goleiro", PlayerPosition.GOALKEEPER);
        joao.markPaid(new BigDecimal("50.00"));
        joao.assignTeam(MatchTeam.A);
        matchParticipantRepository.save(joao);
        openWithParticipants.changeTeam(MatchTeam.A, "Os Pernas", ShirtColor.BLUE);
        matchRepository.save(openWithParticipants);

        // Fechada, cotas congeladas em 75,00; um já pagou.
        closed = matchOf(reservation(pedro, slot1b, TODAY, false));
        MatchParticipant first = participant(closed, "Lucas", PlayerPosition.FIELD);
        MatchParticipant second = participant(closed, "Rafael", PlayerPosition.FIELD);
        first.freezeShare(new BigDecimal("75.00"));
        second.freezeShare(new BigDecimal("75.00"));
        first.markPaid(new BigDecimal("75.00"));
        matchParticipantRepository.saveAll(List.of(first, second));
        closed.close(NOW);
        matchRepository.save(closed);

        // Aberta e vazia (sem linhas em match_participants).
        emptyOpen = matchOf(reservation(maria, slot2, TODAY.plusDays(7), false));
        // Reserva cancelada.
        cancelledReservation = matchOf(reservation(maria, slot1a, TODAY.plusDays(14), true));

        // Ocorrências de série: uma liberada (cancelada) e uma ativa.
        series = recurringReservationRepository.save(
                new RecurringReservation(slot1b, maria, "Turma", "11999991234",
                        TODAY.plusDays(21), null));
        releasedOccurrence = matchRepository.save(
                Match.forOccurrence(series, TODAY.plusDays(21), token(), NOW));
        releaseRepository.save(
                new RecurringReservationRelease(series, TODAY.plusDays(21), NOW));
        activeOccurrence = matchRepository.save(
                Match.forOccurrence(series, TODAY.plusDays(28), token(), NOW));

        // Fora da janela padrão (passado e além de 30 dias).
        pastMatch = matchOf(reservation(maria, slot1a, TODAY.minusDays(7), false));
        farMatch = matchOf(reservation(maria, slot2, TODAY.plusDays(35), false));

        // De outro estabelecimento: nunca pode aparecer.
        otherEstablishmentMatch = matchOf(reservation(maria, otherSlot, TODAY, false));
    }

    // ---------- Lista ----------

    @Nested
    @DisplayName("GET /establishments/{id}/matches")
    class ListMatches {

        @Test
        void defaultsToTodayPlus30DaysInDateAndTimeOrder() throws Exception {
            assertThat(idsOf(list()))
                    .containsExactly(
                            openWithParticipants.getId(), closed.getId(), emptyOpen.getId(),
                            cancelledReservation.getId(), releasedOccurrence.getId(),
                            activeOccurrence.getId());
        }

        @Test
        void returnsOrganizerContactFieldAndCountsOfEachMatch() throws Exception {
            String body = list()
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(openWithParticipants.getId()))
                    .andExpect(jsonPath("$.content[0].status").value("OPEN"))
                    .andExpect(jsonPath("$.content[0].cancelled").value(false))
                    .andExpect(jsonPath("$.content[0].date").value("2026-10-19"))
                    .andExpect(jsonPath("$.content[0].startTime").value("19:00:00"))
                    .andExpect(jsonPath("$.content[0].endTime").value("20:00:00"))
                    .andExpect(jsonPath("$.content[0].price").value(150.00))
                    .andExpect(jsonPath("$.content[0].scheduleSlotId").value(slot1a.getId()))
                    .andExpect(jsonPath("$.content[0].sportField.id").value(field1.getId()))
                    .andExpect(jsonPath("$.content[0].sportField.name").value("Campo 1"))
                    .andExpect(jsonPath("$.content[0].organizer.id").value(maria.getId()))
                    .andExpect(jsonPath("$.content[0].organizer.name").value("Maria da Silva"))
                    .andExpect(jsonPath("$.content[0].organizer.phone").value("11999991234"))
                    .andExpect(jsonPath("$.content[0].fieldPlayerCount").value(3))
                    .andExpect(jsonPath("$.content[0].goalkeeperCount").value(1))
                    .andExpect(jsonPath("$.content[0].payingCount").value(3))
                    .andExpect(jsonPath("$.content[0].paidCount").value(1))
                    .andExpect(jsonPath("$.content[1].id").value(closed.getId()))
                    .andExpect(jsonPath("$.content[1].status").value("CLOSED"))
                    .andExpect(jsonPath("$.content[1].organizer.name").value("Pedro Souza"))
                    .andExpect(jsonPath("$.content[1].fieldPlayerCount").value(2))
                    .andExpect(jsonPath("$.content[1].goalkeeperCount").value(0))
                    .andExpect(jsonPath("$.content[1].payingCount").value(2))
                    .andExpect(jsonPath("$.content[1].paidCount").value(1))
                    .andReturn().getResponse().getContentAsString();

            assertThat(body)
                    .doesNotContain(openWithParticipants.getPublicToken())
                    .doesNotContain("publicToken");
        }

        @Test
        void showsZeroCountsForAMatchWithoutParticipants() throws Exception {
            list().andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[2].id").value(emptyOpen.getId()))
                    .andExpect(jsonPath("$.content[2].fieldPlayerCount").value(0))
                    .andExpect(jsonPath("$.content[2].goalkeeperCount").value(0))
                    .andExpect(jsonPath("$.content[2].payingCount").value(0))
                    .andExpect(jsonPath("$.content[2].paidCount").value(0));
        }

        @Test
        void marksMatchesWhoseOriginWasCancelled() throws Exception {
            list().andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].cancelled").value(false))
                    .andExpect(jsonPath("$.content[3].id")
                            .value(cancelledReservation.getId()))
                    .andExpect(jsonPath("$.content[3].cancelled").value(true))
                    .andExpect(jsonPath("$.content[4].id")
                            .value(releasedOccurrence.getId()))
                    .andExpect(jsonPath("$.content[4].cancelled").value(true))
                    .andExpect(jsonPath("$.content[5].id").value(activeOccurrence.getId()))
                    .andExpect(jsonPath("$.content[5].cancelled").value(false));
        }

        @Test
        void neverIncludesMatchesOfAnotherEstablishment() throws Exception {
            assertThat(idsOf(list(otherEstablishment.getId())))
                    .containsExactly(otherEstablishmentMatch.getId());
            assertThat(idsOf(list()))
                    .doesNotContain(otherEstablishmentMatch.getId());
        }

        @Test
        void acceptsAnExplicitPeriodIncludingThePastAndTheFuture() throws Exception {
            assertThat(idsOf(list("from", "2026-10-12", "to", "2026-11-23")))
                    .containsExactly(
                            pastMatch.getId(), openWithParticipants.getId(), closed.getId(),
                            emptyOpen.getId(), cancelledReservation.getId(),
                            releasedOccurrence.getId(), activeOccurrence.getId(),
                            farMatch.getId());
        }

        @Test
        void filtersBySportFieldAndStatus() throws Exception {
            assertThat(idsOf(list("sportFieldId", field2.getId().toString())))
                    .containsExactly(emptyOpen.getId());
            assertThat(idsOf(list("status", "CLOSED"))).containsExactly(closed.getId());
            assertThat(idsOf(list("status", "OPEN", "status", "CLOSED"))).hasSize(6);
        }

        @Test
        void paginatesWithStableOrder() throws Exception {
            list("size", "4")
                    .andExpect(jsonPath("$.totalElements").value(6))
                    .andExpect(jsonPath("$.totalPages").value(2));

            assertThat(idsOf(list("size", "4", "page", "0")))
                    .containsExactly(openWithParticipants.getId(), closed.getId(),
                            emptyOpen.getId(), cancelledReservation.getId());
            assertThat(idsOf(list("size", "4", "page", "1")))
                    .containsExactly(releasedOccurrence.getId(), activeOccurrence.getId());
            list("size", "4", "page", "2")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isEmpty());
        }

        @Test
        void usesAConstantNumberOfQueriesForThePage() throws Exception {
            Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class)
                    .getStatistics();
            statistics.clear();

            list().andExpect(status().isOk());

            // Estabelecimento, página, contagem, participantes em lote e liberações em lote:
            // não cresce com o número de partidas.
            assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(6);
        }

        @Test
        void returnsNotFoundForSportFieldOfAnotherEstablishmentAndUnknownEstablishment()
                throws Exception {
            list("sportFieldId", otherField.getId().toString())
                    .andExpect(status().isNotFound());
            list(999999L).andExpect(status().isNotFound());
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("br.dev.guisleri.agendaarena.match.AdminMatchViewIntegrationTests#invalidQueries")
        void rejectsInvalidQuery(String name, String[] params, String field) throws Exception {
            list(params)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors." + field).isNotEmpty());
        }

        @Test
        void rejectsAnUnknownStatusWithTheStandardErrorBody() throws Exception {
            list("status", "NOPE")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("Bad Request"));
        }

        @Test
        void doesNotChangeAnything() throws Exception {
            long matches = matchRepository.count();
            long participants = matchParticipantRepository.count();

            list().andExpect(status().isOk());

            assertThat(matchRepository.count()).isEqualTo(matches);
            assertThat(matchParticipantRepository.count()).isEqualTo(participants);
        }
    }

    static Stream<Arguments> invalidQueries() {
        return Stream.of(
                Arguments.of("from after to",
                        new String[]{"from", "2026-10-20", "to", "2026-10-19"}, "to"),
                Arguments.of("period above 92 days",
                        new String[]{"from", "2026-10-19", "to", "2027-01-19"}, "to"),
                Arguments.of("size zero", new String[]{"size", "0"}, "size"),
                Arguments.of("size above the maximum", new String[]{"size", "101"}, "size"),
                Arguments.of("negative page", new String[]{"page", "-1"}, "page")
        );
    }

    // ---------- Detalhe ----------

    @Nested
    @DisplayName("GET /establishments/{id}/matches/{matchId}")
    class MatchDetail {

        @Test
        void showsParticipantsSharesPaymentsAndTeams() throws Exception {
            String body = detail(openWithParticipants.getId())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(openWithParticipants.getId()))
                    .andExpect(jsonPath("$.status").value("OPEN"))
                    .andExpect(jsonPath("$.cancelled").value(false))
                    .andExpect(jsonPath("$.reservationId").value(openReservation.getId()))
                    .andExpect(jsonPath("$.reservationStatus").value("CONFIRMED"))
                    .andExpect(jsonPath("$.recurringReservationId").value(nullValue()))
                    .andExpect(jsonPath("$.occurrenceDate").value(nullValue()))
                    .andExpect(jsonPath("$.date").value("2026-10-19"))
                    .andExpect(jsonPath("$.price").value(150.00))
                    .andExpect(jsonPath("$.sportField.name").value("Campo 1"))
                    .andExpect(jsonPath("$.organizer.name").value("Maria da Silva"))
                    .andExpect(jsonPath("$.organizer.phone").value("11999991234"))
                    .andExpect(jsonPath("$.closedAt").value(nullValue()))
                    .andExpect(jsonPath("$.fieldPlayerCount").value(3))
                    .andExpect(jsonPath("$.goalkeeperCount").value(1))
                    .andExpect(jsonPath("$.payingCount").value(3))
                    .andExpect(jsonPath("$.paidCount").value(1))
                    .andExpect(jsonPath("$.shareTotal").value(150.00))
                    .andExpect(jsonPath("$.paidTotal").value(50.00))
                    .andExpect(jsonPath("$.teams[0].team").value("A"))
                    .andExpect(jsonPath("$.teams[0].name").value("Os Pernas"))
                    .andExpect(jsonPath("$.teams[0].shirtColor").value("BLUE"))
                    .andExpect(jsonPath("$.teams[1].name").value("Time B"))
                    .andExpect(jsonPath("$.participants.length()").value(4))
                    .andExpect(jsonPath("$.participants[0].name").value("Joao"))
                    .andExpect(jsonPath("$.participants[0].position").value("FIELD"))
                    .andExpect(jsonPath("$.participants[0].paysShare").value(true))
                    .andExpect(jsonPath("$.participants[0].shareAmount").value(50.00))
                    .andExpect(jsonPath("$.participants[0].paid").value(true))
                    .andExpect(jsonPath("$.participants[0].paidAmount").value(50.00))
                    .andExpect(jsonPath("$.participants[0].team").value("A"))
                    .andExpect(jsonPath("$.participants[1].paid").value(false))
                    .andExpect(jsonPath("$.participants[3].name").value("Goleiro"))
                    .andExpect(jsonPath("$.participants[3].paysShare").value(false))
                    .andExpect(jsonPath("$.participants[3].shareAmount").value(0.0))
                    .andReturn().getResponse().getContentAsString();

            assertThat(body)
                    .doesNotContain(openWithParticipants.getPublicToken())
                    .doesNotContain("publicToken")
                    .doesNotContain("pix");
        }

        @Test
        void showsFrozenSharesAndClosingTimeOfAClosedMatch() throws Exception {
            detail(closed.getId())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CLOSED"))
                    .andExpect(jsonPath("$.closedAt").isNotEmpty())
                    .andExpect(jsonPath("$.participants[0].shareAmount").value(75.00))
                    .andExpect(jsonPath("$.shareTotal").value(150.00))
                    .andExpect(jsonPath("$.paidTotal").value(75.00))
                    .andExpect(jsonPath("$.paidCount").value(1));
        }

        @Test
        void showsAnOccurrenceMatchAndItsCancellation() throws Exception {
            detail(releasedOccurrence.getId())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.cancelled").value(true))
                    .andExpect(jsonPath("$.reservationId").value(nullValue()))
                    .andExpect(jsonPath("$.reservationStatus").value(nullValue()))
                    .andExpect(jsonPath("$.recurringReservationId").value(series.getId()))
                    .andExpect(jsonPath("$.occurrenceDate").value("2026-11-09"))
                    .andExpect(jsonPath("$.participants").isEmpty());
            detail(activeOccurrence.getId())
                    .andExpect(jsonPath("$.cancelled").value(false));
        }

        @Test
        void showsACancelledReservationMatch() throws Exception {
            detail(cancelledReservation.getId())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.cancelled").value(true))
                    .andExpect(jsonPath("$.reservationStatus").value("CANCELLED"));
        }

        @Test
        void returnsNotFoundForAnotherEstablishmentUnknownMatchAndUnknownEstablishment()
                throws Exception {
            detail(otherEstablishmentMatch.getId()).andExpect(status().isNotFound());
            detail(999999L).andExpect(status().isNotFound());
            mockMvc.perform(get(MATCHES + "/{matchId}", 999999L, openWithParticipants.getId()))
                    .andExpect(status().isNotFound());
        }

        @Test
        void sameMatchIsVisibleOnlyUnderItsOwnEstablishment() throws Exception {
            mockMvc.perform(get(MATCHES + "/{matchId}", otherEstablishment.getId(),
                            openWithParticipants.getId()))
                    .andExpect(status().isNotFound());
            mockMvc.perform(get(MATCHES + "/{matchId}", otherEstablishment.getId(),
                            otherEstablishmentMatch.getId()))
                    .andExpect(status().isOk());
        }

        @Test
        void doesNotChangeAnything() throws Exception {
            long participants = matchParticipantRepository.count();

            detail(openWithParticipants.getId()).andExpect(status().isOk());

            assertThat(matchParticipantRepository.count()).isEqualTo(participants);
            assertThat(matchRepository.findById(openWithParticipants.getId()).orElseThrow()
                    .getStatus().name()).isEqualTo("OPEN");
        }
    }

    // ---------- Apoio ----------

    private ScheduleSlot slot(SportField sportField, LocalTime startTime) {
        return scheduleSlotRepository.save(
                new ScheduleSlot(sportField, DayOfWeek.MONDAY, startTime, new BigDecimal("150.00")));
    }

    private Reservation reservation(
            User organizer,
            ScheduleSlot slot,
            LocalDate date,
            boolean cancelled
    ) {
        Reservation reservation = Reservation.online(organizer, slot, date);

        if (cancelled) {
            reservation.cancel();
        }

        return reservationRepository.save(reservation);
    }

    private Match matchOf(Reservation reservation) {
        return matchRepository.save(Match.forReservation(reservation, token(), NOW));
    }

    private MatchParticipant participant(Match match, String name, PlayerPosition position) {
        return matchParticipantRepository.save(
                MatchParticipant.join(match, name, position, NOW.plusSeconds(++tokenCounter)));
    }

    private String token() {
        return String.format("%043d", ++tokenCounter);
    }

    private ResultActions list(String... params) throws Exception {
        return list(establishment.getId(), params);
    }

    private ResultActions list(Long establishmentId, String... params) throws Exception {
        MockHttpServletRequestBuilder request = get(MATCHES, establishmentId);

        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }

        return mockMvc.perform(request);
    }

    private ResultActions detail(Long matchId) throws Exception {
        return mockMvc.perform(get(MATCHES + "/{matchId}", establishment.getId(), matchId));
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
            return Clock.fixed(NOW, SAO_PAULO);
        }
    }
}
