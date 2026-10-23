package br.dev.guisleri.agendaarena.schedule;

import br.dev.guisleri.agendaarena.establishment.entity.Establishment;
import br.dev.guisleri.agendaarena.establishment.repository.EstablishmentRepository;
import br.dev.guisleri.agendaarena.schedule.entity.ScheduleSlot;
import br.dev.guisleri.agendaarena.schedule.repository.ScheduleSlotRepository;
import br.dev.guisleri.agendaarena.sportfield.entity.SportField;
import br.dev.guisleri.agendaarena.sportfield.model.SportType;
import br.dev.guisleri.agendaarena.sportfield.repository.SportFieldRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ScheduleSlotIntegrationTests {

    private static final String BASE_PATH =
            "/establishments/{establishmentId}/sports-fields/{sportFieldId}/schedule-slots";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EstablishmentRepository establishmentRepository;

    @Autowired
    private SportFieldRepository sportFieldRepository;

    @Autowired
    private ScheduleSlotRepository scheduleSlotRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${spring.jpa.open-in-view}")
    private boolean openInView;

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
    void cleanDatabase() {
        scheduleSlotRepository.deleteAllInBatch();
        sportFieldRepository.deleteAllInBatch();
        establishmentRepository.deleteAllInBatch();
    }

    @Test
    void shouldCreateScheduleSlotAndPersistAllFields() throws Exception {
        Establishment establishment = createEstablishment("Arena Central");
        SportField sportField = createSportField(establishment, "Campo 1");

        String response = createScheduleSlot(
                establishment.getId(), sportField.getId(), "MONDAY", "10:30", "150.50"
        )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.sportFieldId").value(sportField.getId()))
                .andExpect(jsonPath("$.dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$.startTime").value("10:30:00"))
                .andExpect(jsonPath("$.endTime").value("11:30:00"))
                .andExpect(jsonPath("$.price").value(150.50))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn().getResponse().getContentAsString();

        Number returnedId = JsonPath.read(response, "$.id");
        ScheduleSlot persisted = scheduleSlotRepository.findById(returnedId.longValue()).orElseThrow();

        assertThat(scheduleSlotRepository.count()).isEqualTo(1);
        assertThat(persisted.getSportField().getId()).isEqualTo(sportField.getId());
        assertThat(persisted.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(persisted.getStartTime()).isEqualTo(LocalTime.of(10, 30));
        assertThat(persisted.getPrice()).isEqualByComparingTo("150.50");
        assertThat(persisted.isActive()).isTrue();
    }

    @Test
    void shouldReturnNotFoundWhenCreatingForUnknownSportField() throws Exception {
        Establishment establishment = createEstablishment("Arena Central");

        assertError(createScheduleSlot(
                establishment.getId(), Long.MAX_VALUE, "MONDAY", "10:00", "100.00"
        ), 404, "Not Found")
                .andExpect(jsonPath("$.message").value(containsString("Sport field with id " + Long.MAX_VALUE)));

        assertThat(scheduleSlotRepository.count()).isZero();
    }

    @Test
    void shouldReturnNotFoundWhenCreatingForSportFieldFromAnotherEstablishment() throws Exception {
        Establishment first = createEstablishment("Arena Norte");
        Establishment second = createEstablishment("Arena Sul");
        SportField sportField = createSportField(first, "Campo Norte");

        assertError(createScheduleSlot(
                second.getId(), sportField.getId(), "MONDAY", "10:00", "100.00"
        ), 404, "Not Found")
                .andExpect(jsonPath("$.message").value(containsString(
                        "not found for establishment " + second.getId()
                )));

        assertThat(scheduleSlotRepository.count()).isZero();
    }

    @Test
    void shouldReturnConflictForDuplicateSportFieldDayAndStartTime() throws Exception {
        Establishment establishment = createEstablishment("Arena Central");
        SportField sportField = createSportField(establishment, "Campo 1");

        createScheduleSlot(establishment.getId(), sportField.getId(), "TUESDAY", "18:00", "120.00")
                .andExpect(status().isCreated());

        assertError(createScheduleSlot(
                establishment.getId(), sportField.getId(), "TUESDAY", "18:00", "200.00"
        ), 409, "Conflict")
                .andExpect(jsonPath("$.message").value(containsString("already exists")));

        assertThat(scheduleSlotRepository.count()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"23:00", "23:30"})
    void shouldRejectScheduleSlotCrossingMidnight(String startTime) throws Exception {
        Establishment establishment = createEstablishment("Arena Central");
        SportField sportField = createSportField(establishment, "Campo 1");

        assertError(createScheduleSlot(
                establishment.getId(), sportField.getId(), "MONDAY", startTime, "100.00"
        ), 400, "Bad Request")
                .andExpect(jsonPath("$.message").value(containsString("cannot cross midnight")));

        assertThat(scheduleSlotRepository.count()).isZero();
    }

    @Test
    void shouldRejectOverlappingScheduleSlot() throws Exception {
        Establishment establishment = createEstablishment("Arena Central");
        SportField sportField = createSportField(establishment, "Campo 1");

        createScheduleSlot(establishment.getId(), sportField.getId(), "MONDAY", "19:00", "100.00")
                .andExpect(status().isCreated());

        assertError(createScheduleSlot(
                establishment.getId(), sportField.getId(), "MONDAY", "19:30", "100.00"
        ), 400, "Bad Request");

        assertThat(scheduleSlotRepository.count()).isEqualTo(1);
    }

    @Test
    void shouldRejectScheduleSlotOverlappingFromBefore() throws Exception {
        Establishment establishment = createEstablishment("Arena Central");
        SportField sportField = createSportField(establishment, "Campo 1");

        createScheduleSlot(establishment.getId(), sportField.getId(), "MONDAY", "19:00", "100.00")
                .andExpect(status().isCreated());

        assertError(createScheduleSlot(
                establishment.getId(), sportField.getId(), "MONDAY", "18:30", "100.00"
        ), 400, "Bad Request");

        assertThat(scheduleSlotRepository.count()).isEqualTo(1);
    }

    @Test
    void shouldAllowConsecutiveScheduleSlots() throws Exception {
        Establishment establishment = createEstablishment("Arena Central");
        SportField sportField = createSportField(establishment, "Campo 1");

        createScheduleSlot(establishment.getId(), sportField.getId(), "MONDAY", "19:00", "100.00")
                .andExpect(status().isCreated());
        createScheduleSlot(establishment.getId(), sportField.getId(), "MONDAY", "20:00", "100.00")
                .andExpect(status().isCreated());

        assertThat(scheduleSlotRepository.count()).isEqualTo(2);
    }

    @Test
    void shouldRejectNegativePrice() throws Exception {
        Establishment establishment = createEstablishment("Arena Central");
        SportField sportField = createSportField(establishment, "Campo 1");

        assertError(createScheduleSlot(
                establishment.getId(), sportField.getId(), "WEDNESDAY", "09:00", "-0.01"
        ), 400, "Bad Request")
                .andExpect(jsonPath("$.errors.price").isNotEmpty());

        assertThat(scheduleSlotRepository.count()).isZero();
    }

    @Test
    void shouldRejectInvalidDayOfWeek() throws Exception {
        Establishment establishment = createEstablishment("Arena Central");
        SportField sportField = createSportField(establishment, "Campo 1");

        assertError(createScheduleSlot(
                establishment.getId(), sportField.getId(), "FUNDAY", "09:00", "100.00"
        ), 400, "Bad Request")
                .andExpect(jsonPath("$.message").value("Invalid request body"));

        assertThat(scheduleSlotRepository.count()).isZero();
    }

    @ParameterizedTest(name = "should reject required field {1} when payload is {0}")
    @MethodSource("invalidRequiredFields")
    void shouldRejectNullOrMissingRequiredFields(
            String scenario,
            String expectedField,
            String requestBody
    ) throws Exception {
        Establishment establishment = createEstablishment("Arena Central");
        SportField sportField = createSportField(establishment, "Campo 1");

        assertError(mockMvc.perform(post(BASE_PATH, establishment.getId(), sportField.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)),
                400, "Bad Request")
                .andExpect(jsonPath("$.errors." + expectedField).isNotEmpty());

        assertThat(scheduleSlotRepository.count()).isZero();
    }

    @Test
    void shouldListAllScheduleSlotsForSportField() throws Exception {
        Establishment establishment = createEstablishment("Arena Central");
        SportField sportField = createSportField(establishment, "Campo 1");
        persistScheduleSlot(sportField, DayOfWeek.MONDAY, LocalTime.of(8, 0), "90.00");
        persistScheduleSlot(sportField, DayOfWeek.FRIDAY, LocalTime.of(19, 30), "140.00");

        String response = mockMvc.perform(get(BASE_PATH, establishment.getId(), sportField.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andReturn().getResponse().getContentAsString();

        List<Map<String, Object>> slots = JsonPath.read(response, "$[*]");
        assertThat(slots).anySatisfy(slot -> assertSlot(slot, sportField.getId(),
                "MONDAY", "08:00:00", "09:00:00", "90.0"));
        assertThat(slots).anySatisfy(slot -> assertSlot(slot, sportField.getId(),
                "FRIDAY", "19:30:00", "20:30:00", "140.0"));
    }

    @Test
    void shouldReturnEmptyListWhenSportFieldHasNoScheduleSlots() throws Exception {
        Establishment establishment = createEstablishment("Arena Central");
        SportField sportField = createSportField(establishment, "Campo 1");

        mockMvc.perform(get(BASE_PATH, establishment.getId(), sportField.getId()))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void shouldReturnNotFoundWhenListingSlotsForUnknownSportField() throws Exception {
        Establishment establishment = createEstablishment("Arena Central");

        assertError(mockMvc.perform(get(BASE_PATH, establishment.getId(), Long.MAX_VALUE)),
                404, "Not Found")
                .andExpect(jsonPath("$.message").value(containsString("Sport field with id " + Long.MAX_VALUE)));
    }

    @Test
    void shouldReturnNotFoundWhenListingSlotsForSportFieldFromAnotherEstablishment() throws Exception {
        Establishment first = createEstablishment("Arena Norte");
        Establishment second = createEstablishment("Arena Sul");
        SportField sportField = createSportField(first, "Campo Norte");
        persistScheduleSlot(sportField, DayOfWeek.MONDAY, LocalTime.of(10, 0), "100.00");

        assertError(mockMvc.perform(get(BASE_PATH, second.getId(), sportField.getId())),
                404, "Not Found")
                .andExpect(jsonPath("$.message").value(containsString(
                        "not found for establishment " + second.getId()
                )));
    }

    @Test
    void shouldFindExistingScheduleSlotById() throws Exception {
        Establishment establishment = createEstablishment("Arena Central");
        SportField sportField = createSportField(establishment, "Campo 1");
        ScheduleSlot slot = persistScheduleSlot(
                sportField, DayOfWeek.SATURDAY, LocalTime.of(14, 0), "175.00"
        );

        mockMvc.perform(get(BASE_PATH + "/{scheduleSlotId}",
                        establishment.getId(), sportField.getId(), slot.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(slot.getId()))
                .andExpect(jsonPath("$.sportFieldId").value(sportField.getId()))
                .andExpect(jsonPath("$.dayOfWeek").value("SATURDAY"))
                .andExpect(jsonPath("$.startTime").value("14:00:00"))
                .andExpect(jsonPath("$.endTime").value("15:00:00"))
                .andExpect(jsonPath("$.price").value(175.00))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void shouldReturnNotFoundForUnknownScheduleSlotId() throws Exception {
        Establishment establishment = createEstablishment("Arena Central");
        SportField sportField = createSportField(establishment, "Campo 1");

        assertError(mockMvc.perform(get(BASE_PATH + "/{scheduleSlotId}",
                        establishment.getId(), sportField.getId(), Long.MAX_VALUE)),
                404, "Not Found")
                .andExpect(jsonPath("$.message").value(containsString(
                        "Schedule slot with id " + Long.MAX_VALUE
                )));
    }

    @Test
    void shouldReturnNotFoundForScheduleSlotBelongingToAnotherSportField() throws Exception {
        Establishment establishment = createEstablishment("Arena Central");
        SportField first = createSportField(establishment, "Campo 1");
        SportField second = createSportField(establishment, "Campo 2");
        ScheduleSlot slot = persistScheduleSlot(
                first, DayOfWeek.SUNDAY, LocalTime.of(16, 0), "130.00"
        );

        assertError(mockMvc.perform(get(BASE_PATH + "/{scheduleSlotId}",
                        establishment.getId(), second.getId(), slot.getId())),
                404, "Not Found")
                .andExpect(jsonPath("$.message").value(containsString(
                        "not found for sport field " + second.getId()
                )));
    }

    @Test
    void shouldCalculateEndTimeUsingSportFieldSlotDuration() throws Exception {
        Establishment establishment = createEstablishment("Arena Central");
        SportField sportField = createSportField(establishment, "Campo 1");
        jdbcTemplate.update(
                "UPDATE sports_fields SET slot_duration_minutes = ? WHERE id = ?",
                90,
                sportField.getId()
        );

        createScheduleSlot(establishment.getId(), sportField.getId(), "THURSDAY", "21:15", "160.00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.startTime").value("21:15:00"))
                .andExpect(jsonPath("$.endTime").value("22:45:00"));
    }

    @Test
    void shouldServeListAndDetailWithOpenInViewDisabled() throws Exception {
        Establishment establishment = createEstablishment("Arena Central");
        SportField sportField = createSportField(establishment, "Campo 1");
        ScheduleSlot slot = persistScheduleSlot(
                sportField, DayOfWeek.MONDAY, LocalTime.of(7, 0), "80.00"
        );

        assertThat(openInView).isFalse();

        mockMvc.perform(get(BASE_PATH, establishment.getId(), sportField.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sportFieldId").value(sportField.getId()))
                .andExpect(jsonPath("$[0].endTime").value("08:00:00"));

        mockMvc.perform(get(BASE_PATH + "/{scheduleSlotId}",
                        establishment.getId(), sportField.getId(), slot.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sportFieldId").value(sportField.getId()))
                .andExpect(jsonPath("$.endTime").value("08:00:00"));
    }

    private Establishment createEstablishment(String name) {
        return establishmentRepository.save(new Establishment(name));
    }

    private static Stream<Arguments> invalidRequiredFields() {
        return Stream.of(
                Arguments.of("missing", "dayOfWeek", """
                        {"startTime":"10:00","price":100.00}
                        """),
                Arguments.of("null", "dayOfWeek", """
                        {"dayOfWeek":null,"startTime":"10:00","price":100.00}
                        """),
                Arguments.of("missing", "startTime", """
                        {"dayOfWeek":"MONDAY","price":100.00}
                        """),
                Arguments.of("null", "startTime", """
                        {"dayOfWeek":"MONDAY","startTime":null,"price":100.00}
                        """),
                Arguments.of("missing", "price", """
                        {"dayOfWeek":"MONDAY","startTime":"10:00"}
                        """),
                Arguments.of("null", "price", """
                        {"dayOfWeek":"MONDAY","startTime":"10:00","price":null}
                        """)
        );
    }

    private SportField createSportField(Establishment establishment, String name) {
        return sportFieldRepository.save(new SportField(establishment, name, SportType.SOCIETY));
    }

    private ScheduleSlot persistScheduleSlot(
            SportField sportField,
            DayOfWeek dayOfWeek,
            LocalTime startTime,
            String price
    ) {
        return scheduleSlotRepository.save(new ScheduleSlot(
                sportField,
                dayOfWeek,
                startTime,
                new BigDecimal(price)
        ));
    }

    private ResultActions createScheduleSlot(
            Long establishmentId,
            Long sportFieldId,
            String dayOfWeek,
            String startTime,
            String price
    ) throws Exception {
        return mockMvc.perform(post(BASE_PATH, establishmentId, sportFieldId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "dayOfWeek": "%s",
                          "startTime": "%s",
                          "price": %s
                        }
                        """.formatted(dayOfWeek, startTime, price)));
    }

    private ResultActions assertError(ResultActions result, int statusCode, String error) throws Exception {
        return result.andExpect(status().is(statusCode))
                .andExpect(jsonPath("$.status").value(statusCode))
                .andExpect(jsonPath("$.error").value(error))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.errors").isMap());
    }

    private void assertSlot(
            Map<String, Object> response,
            Long sportFieldId,
            String dayOfWeek,
            String startTime,
            String endTime,
            String price
    ) {
        assertThat(((Number) response.get("sportFieldId")).longValue()).isEqualTo(sportFieldId);
        assertThat(response.get("dayOfWeek")).isEqualTo(dayOfWeek);
        assertThat(response.get("startTime")).isEqualTo(startTime);
        assertThat(response.get("endTime")).isEqualTo(endTime);
        assertThat(new BigDecimal(response.get("price").toString())).isEqualByComparingTo(price);
        assertThat(response.get("active")).isEqualTo(true);
    }
}
