package br.dev.guisleri.agendaarena.reservation;

import br.dev.guisleri.agendaarena.establishment.entity.Establishment;
import br.dev.guisleri.agendaarena.establishment.repository.EstablishmentRepository;
import br.dev.guisleri.agendaarena.reservation.entity.SlotBlock;
import br.dev.guisleri.agendaarena.reservation.repository.SlotBlockRepository;
import br.dev.guisleri.agendaarena.schedule.entity.ScheduleSlot;
import br.dev.guisleri.agendaarena.schedule.repository.ScheduleSlotRepository;
import br.dev.guisleri.agendaarena.sportfield.entity.SportField;
import br.dev.guisleri.agendaarena.sportfield.model.SportType;
import br.dev.guisleri.agendaarena.sportfield.repository.SportFieldRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Listagem de bloqueios do administrador ({@code GET /establishments/{id}/blocks}). O
 * {@code MockMvc} autoconfigurado já chama como PLATFORM_ADMIN; a autorização por estabelecimento
 * (404/403/401) é coberta em {@code EstablishmentAuthorizationIntegrationTests}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(AdminSlotBlockListIntegrationTests.TestClockConfiguration.class)
class AdminSlotBlockListIntegrationTests {

    private static final String BLOCKS = "/establishments/{establishmentId}/blocks";
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    // Relógio fixo: segunda-feira 2026-10-19, 18:30 em São Paulo.
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 19);
    private static final Instant CREATED_AT = Instant.parse("2026-10-01T12:00:00Z");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private SlotBlockRepository slotBlockRepository;
    @Autowired
    private ScheduleSlotRepository scheduleSlotRepository;
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
        scheduleSlotRepository.deleteAllInBatch();
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
    }

    @Test
    void shouldListBlocksOfTheNext30DaysInFixedOrder() throws Exception {
        SlotBlock later = block(slot1a, TODAY.plusDays(14), "Manutenção");
        SlotBlock sameDayLate = block(slot1b, TODAY, "Torneio interno");
        SlotBlock sameDayEarly = block(slot1a, TODAY, null);
        SlotBlock lastDay = block(slot2, TODAY.plusDays(30), "Chuva");
        block(slot1a, TODAY.plusDays(31), "Fora da janela (depois)");
        block(slot1a, TODAY.minusDays(1), "Fora da janela (antes)");
        block(otherSlot, TODAY, "De outro estabelecimento");

        assertThat(idsOf(list(BLOCKS)))
                .containsExactly(
                        sameDayEarly.getId(),
                        sameDayLate.getId(),
                        later.getId(),
                        lastDay.getId()
                );
    }

    @Test
    void shouldReturnWhatTheFrontNeedsToUnblock() throws Exception {
        SlotBlock block = block(slot1b, TODAY.plusDays(7), "Manutenção do gramado");

        list(BLOCKS)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(block.getId()))
                .andExpect(jsonPath("$.content[0].blockDate").value(TODAY.plusDays(7).toString()))
                .andExpect(jsonPath("$.content[0].reason").value("Manutenção do gramado"))
                .andExpect(jsonPath("$.content[0].createdAt").exists())
                .andExpect(jsonPath("$.content[0].sportField.id").value(field1.getId()))
                .andExpect(jsonPath("$.content[0].sportField.name").value("Campo 1"))
                .andExpect(jsonPath("$.content[0].scheduleSlot.id").value(slot1b.getId()))
                .andExpect(jsonPath("$.content[0].scheduleSlot.dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$.content[0].scheduleSlot.startTime").value("20:00:00"))
                .andExpect(jsonPath("$.content[0].scheduleSlot.endTime").value("21:00:00"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void shouldAllowBlocksWithoutReason() throws Exception {
        block(slot1a, TODAY, null);

        list(BLOCKS)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].reason").value(nullValue()));
    }

    @Test
    void shouldFilterBySportField() throws Exception {
        block(slot1a, TODAY, "Campo 1");
        SlotBlock onField2 = block(slot2, TODAY, "Campo 2");

        assertThat(idsOf(list(BLOCKS, "sportFieldId", String.valueOf(field2.getId()))))
                .containsExactly(onField2.getId());
    }

    @Test
    void shouldRespectExplicitFromAndTo() throws Exception {
        block(slot1a, TODAY.plusDays(2), "Antes");
        SlotBlock inside = block(slot1a, TODAY.plusDays(9), "Dentro");
        block(slot1a, TODAY.plusDays(20), "Depois");

        assertThat(idsOf(list(BLOCKS,
                "from", TODAY.plusDays(5).toString(),
                "to", TODAY.plusDays(10).toString())))
                .containsExactly(inside.getId());
    }

    @Test
    void shouldPaginateKeepingTheOrder() throws Exception {
        SlotBlock first = block(slot1a, TODAY, null);
        SlotBlock second = block(slot1a, TODAY.plusDays(7), null);
        SlotBlock third = block(slot1a, TODAY.plusDays(14), null);

        list(BLOCKS, "page", "0", "size", "2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(first.getId()))
                .andExpect(jsonPath("$.content[1].id").value(second.getId()))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));

        list(BLOCKS, "page", "1", "size", "2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(third.getId()));
    }

    @Test
    void shouldReturnEmptyPageWhenThereAreNoBlocks() throws Exception {
        list(BLOCKS)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void shouldReturnNotFoundForSportFieldOfAnotherEstablishment() throws Exception {
        list(BLOCKS, "sportFieldId", String.valueOf(otherField.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnNotFoundForUnknownEstablishment() throws Exception {
        list(BLOCKS, Long.MAX_VALUE).andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectPeriodWithToBeforeFrom() throws Exception {
        list(BLOCKS, "from", TODAY.toString(), "to", TODAY.minusDays(1).toString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.to").exists());
    }

    @Test
    void shouldRejectPeriodLongerThan92Days() throws Exception {
        list(BLOCKS, "from", TODAY.toString(), "to", TODAY.plusDays(92).toString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.to").exists());

        list(BLOCKS, "from", TODAY.toString(), "to", TODAY.plusDays(91).toString())
                .andExpect(status().isOk());
    }

    @ParameterizedTest(name = "page={0}, size={1} é recusado")
    @CsvSource({"-1,20", "0,0", "0,101"})
    void shouldRejectInvalidPagination(String page, String size) throws Exception {
        list(BLOCKS, "page", page, "size", size).andExpect(status().isBadRequest());
    }

    // ---------- Apoio ----------

    private ScheduleSlot slot(SportField sportField, LocalTime startTime) {
        return scheduleSlotRepository.save(
                new ScheduleSlot(sportField, DayOfWeek.MONDAY, startTime, new BigDecimal("150.50")));
    }

    private SlotBlock block(ScheduleSlot slot, LocalDate date, String reason) {
        return slotBlockRepository.saveAndFlush(new SlotBlock(slot, date, reason, CREATED_AT));
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
                .andReturn().getResponse().getContentAsString();
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
