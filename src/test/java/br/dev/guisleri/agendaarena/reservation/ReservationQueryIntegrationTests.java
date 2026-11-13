package br.dev.guisleri.agendaarena.reservation;

import br.dev.guisleri.agendaarena.establishment.entity.Establishment;
import br.dev.guisleri.agendaarena.establishment.repository.EstablishmentRepository;
import br.dev.guisleri.agendaarena.reservation.entity.RecurringReservation;
import br.dev.guisleri.agendaarena.reservation.entity.Reservation;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import java.time.LocalTime;
import java.time.ZoneId;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(ReservationQueryIntegrationTests.TestClockConfiguration.class)
class ReservationQueryIntegrationTests {

    private static final String RESERVATION_PATH =
            "/establishments/{establishmentId}/sports-fields/{sportFieldId}"
                    + "/schedule-slots/{scheduleSlotId}/reservations/{reservationId}";
    private static final String AGENDA_PATH =
            "/establishments/{establishmentId}/sports-fields/{sportFieldId}/agenda";
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    // Relógio fixo: segunda-feira 2026-10-19, 18:30 em São Paulo.
    private static final LocalDate PAST_MONDAY = LocalDate.of(2026, 10, 12);
    private static final LocalDate FIRST_MONDAY = LocalDate.of(2026, 10, 26);
    private static final LocalDate SECOND_MONDAY = LocalDate.of(2026, 11, 2);
    private static final LocalDate THIRD_MONDAY = LocalDate.of(2026, 11, 9);
    private static final LocalDate TUESDAY = LocalDate.of(2026, 10, 27);

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
    private SlotBlockService slotBlockService;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private SlotBlockRepository slotBlockRepository;

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
    private JdbcTemplate jdbcTemplate;

    private Establishment establishment;
    private SportField sportField;
    private ScheduleSlot reservedSlot;
    private ScheduleSlot recurringSlot;
    private ScheduleSlot blockedSlot;
    private ScheduleSlot freeSlot;
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
        reservedSlot = saveSlot(sportField, DayOfWeek.MONDAY, LocalTime.of(19, 0));
        recurringSlot = saveSlot(sportField, DayOfWeek.MONDAY, LocalTime.of(20, 0));
        blockedSlot = saveSlot(sportField, DayOfWeek.MONDAY, LocalTime.of(21, 0));
        freeSlot = saveSlot(sportField, DayOfWeek.MONDAY, LocalTime.of(22, 0));
        organizer = userRepository.save(
                new User(
                        "Maria da Silva",
                        "maria@example.com",
                        "11999991234",
                        "password-hash"
                )
        );
    }

    @Nested
    @DisplayName("GET reservation by id")
    class FindReservation {

        @Test
        void shouldReturnOnlineReservation() throws Exception {
            Reservation reservation = reservationService.createOnlineReservation(
                    establishment.getId(),
                    sportField.getId(),
                    reservedSlot.getId(),
                    organizer.getId(),
                    FIRST_MONDAY
            );

            getReservation(reservedSlot.getId(), reservation.getId())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(reservation.getId().intValue()))
                    .andExpect(jsonPath("$.origin").value("ONLINE"))
                    .andExpect(jsonPath("$.organizerId")
                            .value(organizer.getId().intValue()))
                    .andExpect(jsonPath("$.scheduleSlotId")
                            .value(reservedSlot.getId().intValue()))
                    .andExpect(jsonPath("$.reservationDate").value("2026-10-26"))
                    .andExpect(jsonPath("$.startTime").value("19:00:00"))
                    .andExpect(jsonPath("$.endTime").value("20:00:00"))
                    .andExpect(jsonPath("$.price").value(150.50))
                    .andExpect(jsonPath("$.status").value("CONFIRMED"));
        }

        @Test
        void shouldReturnManualReservationWithCustomerData() throws Exception {
            Reservation reservation = createManualReservation(
                    reservedSlot,
                    FIRST_MONDAY
            );

            getReservation(reservedSlot.getId(), reservation.getId())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.origin").value("MANUAL"))
                    .andExpect(jsonPath("$.organizerId").value(nullValue()))
                    .andExpect(jsonPath("$.customerName").value("Cliente avulso"))
                    .andExpect(jsonPath("$.customerPhone").value("11988887777"));
        }

        @Test
        void shouldReturnCancelledReservation() throws Exception {
            Reservation reservation = createManualReservation(
                    reservedSlot,
                    FIRST_MONDAY
            );
            reservationService.cancelReservation(
                    establishment.getId(),
                    sportField.getId(),
                    reservedSlot.getId(),
                    reservation.getId()
            );

            getReservation(reservedSlot.getId(), reservation.getId())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"));
        }

        @Test
        void shouldReturnNotFoundForUnknownReservation() throws Exception {
            getReservation(reservedSlot.getId(), 999_999L)
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldReturnNotFoundWhenReservationBelongsToAnotherSlot()
                throws Exception {
            Reservation reservation = createManualReservation(
                    reservedSlot,
                    FIRST_MONDAY
            );

            getReservation(freeSlot.getId(), reservation.getId())
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldReturnNotFoundForUnknownScheduleSlot() throws Exception {
            Reservation reservation = createManualReservation(
                    reservedSlot,
                    FIRST_MONDAY
            );

            getReservation(999_999L, reservation.getId())
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldReturnNotFoundThroughAnotherEstablishment() throws Exception {
            Reservation reservation = createManualReservation(
                    reservedSlot,
                    FIRST_MONDAY
            );
            Establishment other = establishmentRepository.save(
                    new Establishment("Outra Arena")
            );

            mockMvc.perform(get(
                            RESERVATION_PATH,
                            other.getId(),
                            sportField.getId(),
                            reservedSlot.getId(),
                            reservation.getId()
                    ))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Administrator agenda")
    class Agenda {

        @Test
        void shouldShowFreeReservedRecurringAndBlockedSlots() throws Exception {
            Reservation reservation = reservationService.createOnlineReservation(
                    establishment.getId(),
                    sportField.getId(),
                    reservedSlot.getId(),
                    organizer.getId(),
                    FIRST_MONDAY
            );
            RecurringReservation recurring = createRecurring(
                    recurringSlot,
                    FIRST_MONDAY,
                    null
            );
            slotBlockService.createBlock(
                    establishment.getId(),
                    sportField.getId(),
                    blockedSlot.getId(),
                    FIRST_MONDAY,
                    "Manutencao da grama"
            );

            getAgenda(sportField.getId(), FIRST_MONDAY)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(4))

                    .andExpect(jsonPath("$[0].scheduleSlotId")
                            .value(reservedSlot.getId().intValue()))
                    .andExpect(jsonPath("$[0].active").value(true))
                    .andExpect(jsonPath("$[0].date").value("2026-10-26"))
                    .andExpect(jsonPath("$[0].startTime").value("19:00:00"))
                    .andExpect(jsonPath("$[0].endTime").value("20:00:00"))
                    .andExpect(jsonPath("$[0].price").value(150.50))
                    .andExpect(jsonPath("$[0].status").value("RESERVED"))
                    .andExpect(jsonPath("$[0].reservation.id")
                            .value(reservation.getId().intValue()))
                    .andExpect(jsonPath("$[0].reservation.origin").value("ONLINE"))
                    .andExpect(jsonPath("$[0].reservation.organizerId")
                            .value(organizer.getId().intValue()))
                    .andExpect(jsonPath("$[0].block").value(nullValue()))
                    .andExpect(jsonPath("$[0].recurring").value(nullValue()))

                    .andExpect(jsonPath("$[1].scheduleSlotId")
                            .value(recurringSlot.getId().intValue()))
                    .andExpect(jsonPath("$[1].status").value("RECURRING"))
                    .andExpect(jsonPath("$[1].reservation").value(nullValue()))
                    .andExpect(jsonPath("$[1].recurring.id")
                            .value(recurring.getId().intValue()))
                    .andExpect(jsonPath("$[1].recurring.responsibleName")
                            .value("Maria da Silva"))
                    .andExpect(jsonPath("$[1].recurring.startDate")
                            .value("2026-10-26"))
                    .andExpect(jsonPath("$[1].recurring.endDate")
                            .value(nullValue()))
                    .andExpect(jsonPath("$[1].recurring.released").value(false))

                    .andExpect(jsonPath("$[2].scheduleSlotId")
                            .value(blockedSlot.getId().intValue()))
                    .andExpect(jsonPath("$[2].status").value("BLOCKED"))
                    .andExpect(jsonPath("$[2].block.reason")
                            .value("Manutencao da grama"))
                    .andExpect(jsonPath("$[2].reservation").value(nullValue()))

                    .andExpect(jsonPath("$[3].scheduleSlotId")
                            .value(freeSlot.getId().intValue()))
                    .andExpect(jsonPath("$[3].status").value("FREE"))
                    .andExpect(jsonPath("$[3].reservation").value(nullValue()))
                    .andExpect(jsonPath("$[3].block").value(nullValue()))
                    .andExpect(jsonPath("$[3].recurring").value(nullValue()));
        }

        @Test
        void shouldShowManualReservationCustomer() throws Exception {
            createManualReservation(reservedSlot, FIRST_MONDAY);

            getAgenda(sportField.getId(), FIRST_MONDAY)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].status").value("RESERVED"))
                    .andExpect(jsonPath("$[0].reservation.origin").value("MANUAL"))
                    .andExpect(jsonPath("$[0].reservation.customerName")
                            .value("Cliente avulso"))
                    .andExpect(jsonPath("$[0].reservation.organizerId")
                            .value(nullValue()));
        }

        @Test
        void shouldKeepCompletedAndNoShowReservationsAsReserved() throws Exception {
            Reservation completed = createManualReservation(
                    reservedSlot,
                    FIRST_MONDAY
            );
            Reservation noShow = createManualReservation(
                    recurringSlot,
                    FIRST_MONDAY
            );
            forceStatus(completed, "COMPLETED");
            forceStatus(noShow, "NO_SHOW");

            getAgenda(sportField.getId(), FIRST_MONDAY)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].status").value("RESERVED"))
                    .andExpect(jsonPath("$[0].reservation.status").value("COMPLETED"))
                    .andExpect(jsonPath("$[1].status").value("RESERVED"))
                    .andExpect(jsonPath("$[1].reservation.status").value("NO_SHOW"));
        }

        @Test
        void shouldNotShowCancelledReservationAndShowSlotAsFree() throws Exception {
            Reservation reservation = createManualReservation(
                    reservedSlot,
                    FIRST_MONDAY
            );
            reservationService.cancelReservation(
                    establishment.getId(),
                    sportField.getId(),
                    reservedSlot.getId(),
                    reservation.getId()
            );

            getAgenda(sportField.getId(), FIRST_MONDAY)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].status").value("FREE"))
                    .andExpect(jsonPath("$[0].reservation").value(nullValue()));
        }

        @Test
        void shouldShowReleasedRecurringOccurrenceAsFree() throws Exception {
            RecurringReservation recurring = createRecurring(
                    recurringSlot,
                    FIRST_MONDAY,
                    THIRD_MONDAY
            );
            releaseOccurrence(recurring, SECOND_MONDAY);

            getAgenda(sportField.getId(), SECOND_MONDAY)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[1].status").value("FREE"))
                    .andExpect(jsonPath("$[1].recurring.id")
                            .value(recurring.getId().intValue()))
                    .andExpect(jsonPath("$[1].recurring.released").value(true));

            getAgenda(sportField.getId(), FIRST_MONDAY)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[1].status").value("RECURRING"))
                    .andExpect(jsonPath("$[1].recurring.released").value(false));
            getAgenda(sportField.getId(), THIRD_MONDAY)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[1].status").value("RECURRING"))
                    .andExpect(jsonPath("$[1].recurring.released").value(false));
        }

        @Test
        void shouldShowReservationOnReleasedOccurrence() throws Exception {
            RecurringReservation recurring = createRecurring(
                    recurringSlot,
                    FIRST_MONDAY,
                    null
            );
            releaseOccurrence(recurring, SECOND_MONDAY);
            createManualReservation(recurringSlot, SECOND_MONDAY);

            getAgenda(sportField.getId(), SECOND_MONDAY)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[1].status").value("RESERVED"))
                    .andExpect(jsonPath("$[1].reservation.origin").value("MANUAL"))
                    .andExpect(jsonPath("$[1].recurring.released").value(true));
        }

        @Test
        void shouldShowBlockOnReleasedOccurrence() throws Exception {
            RecurringReservation recurring = createRecurring(
                    recurringSlot,
                    FIRST_MONDAY,
                    null
            );
            releaseOccurrence(recurring, SECOND_MONDAY);
            slotBlockService.createBlock(
                    establishment.getId(),
                    sportField.getId(),
                    recurringSlot.getId(),
                    SECOND_MONDAY,
                    "Campeonato"
            );

            getAgenda(sportField.getId(), SECOND_MONDAY)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[1].status").value("BLOCKED"))
                    .andExpect(jsonPath("$[1].block.reason").value("Campeonato"))
                    .andExpect(jsonPath("$[1].recurring.released").value(true));
        }

        @Test
        void shouldIgnoreRecurrencesOutsideTheirPeriod() throws Exception {
            createRecurring(recurringSlot, SECOND_MONDAY, THIRD_MONDAY);

            getAgenda(sportField.getId(), FIRST_MONDAY)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[1].status").value("FREE"))
                    .andExpect(jsonPath("$[1].recurring").value(nullValue()));
            getAgenda(sportField.getId(), THIRD_MONDAY.plusDays(7))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[1].status").value("FREE"))
                    .andExpect(jsonPath("$[1].recurring").value(nullValue()));
        }

        @Test
        void shouldAllowPastDates() throws Exception {
            getAgenda(sportField.getId(), PAST_MONDAY)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(4))
                    .andExpect(jsonPath("$[0].status").value("FREE"));
        }

        @Test
        void shouldReturnEmptyListWhenThereAreNoSlotsForTheDayOfWeek()
                throws Exception {
            getAgenda(sportField.getId(), TUESDAY)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        void shouldNotMixSlotsOfAnotherSportFieldOrDayOfWeek() throws Exception {
            SportField otherField = sportFieldRepository.save(
                    new SportField(establishment, "Campo 2", SportType.SOCIETY)
            );
            ScheduleSlot otherFieldSlot = saveSlot(
                    otherField,
                    DayOfWeek.MONDAY,
                    LocalTime.of(19, 0)
            );
            saveSlot(sportField, DayOfWeek.TUESDAY, LocalTime.of(19, 0));
            reservationService.createManualReservation(
                    establishment.getId(),
                    otherField.getId(),
                    otherFieldSlot.getId(),
                    FIRST_MONDAY,
                    "Cliente do campo 2",
                    null
            );

            getAgenda(sportField.getId(), FIRST_MONDAY)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(4))
                    .andExpect(jsonPath("$[0].status").value("FREE"))
                    .andExpect(jsonPath("$[0].reservation").value(nullValue()));
            getAgenda(otherField.getId(), FIRST_MONDAY)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].status").value("RESERVED"));
        }

        @Test
        void shouldRejectMissingOrMalformedDate() throws Exception {
            mockMvc.perform(get(
                            AGENDA_PATH,
                            establishment.getId(),
                            sportField.getId()
                    ))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(get(
                                    AGENDA_PATH,
                                    establishment.getId(),
                                    sportField.getId()
                            )
                            .param("date", "not-a-date"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void shouldReturnNotFoundForUnknownSportField() throws Exception {
            getAgenda(999_999L, FIRST_MONDAY)
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldReturnNotFoundForSportFieldOfAnotherEstablishment()
                throws Exception {
            Establishment other = establishmentRepository.save(
                    new Establishment("Outra Arena")
            );

            mockMvc.perform(get(
                                    AGENDA_PATH,
                                    other.getId(),
                                    sportField.getId()
                            )
                            .param("date", FIRST_MONDAY.toString()))
                    .andExpect(status().isNotFound());
        }
    }

    private ScheduleSlot saveSlot(
            SportField field,
            DayOfWeek dayOfWeek,
            LocalTime startTime
    ) {
        return scheduleSlotRepository.save(
                new ScheduleSlot(
                        field,
                        dayOfWeek,
                        startTime,
                        new BigDecimal("150.50")
                )
        );
    }

    private ResultActions getReservation(Long scheduleSlotId, Long reservationId)
            throws Exception {
        return mockMvc.perform(get(
                RESERVATION_PATH,
                establishment.getId(),
                sportField.getId(),
                scheduleSlotId,
                reservationId
        ));
    }

    private ResultActions getAgenda(Long sportFieldId, LocalDate date)
            throws Exception {
        return mockMvc.perform(get(
                        AGENDA_PATH,
                        establishment.getId(),
                        sportFieldId
                )
                        .param("date", date.toString()));
    }

    private Reservation createManualReservation(
            ScheduleSlot slot,
            LocalDate date
    ) {
        return reservationService.createManualReservation(
                establishment.getId(),
                sportField.getId(),
                slot.getId(),
                date,
                "Cliente avulso",
                "11988887777"
        );
    }

    private RecurringReservation createRecurring(
            ScheduleSlot slot,
            LocalDate startDate,
            LocalDate endDate
    ) {
        return recurringReservationService.createRecurringReservation(
                establishment.getId(),
                sportField.getId(),
                slot.getId(),
                organizer.getId(),
                organizer.getName(),
                organizer.getPhone(),
                startDate,
                endDate
        );
    }

    private void releaseOccurrence(
            RecurringReservation recurring,
            LocalDate occurrenceDate
    ) {
        recurringReservationService.releaseOccurrence(
                establishment.getId(),
                sportField.getId(),
                recurring.getScheduleSlot().getId(),
                recurring.getId(),
                occurrenceDate
        );
    }

    private void forceStatus(Reservation reservation, String statusName) {
        jdbcTemplate.update(
                "UPDATE reservations SET status = ? WHERE id = ?",
                statusName,
                reservation.getId()
        );
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
