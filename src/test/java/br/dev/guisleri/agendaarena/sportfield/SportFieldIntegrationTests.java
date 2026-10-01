package br.dev.guisleri.agendaarena.sportfield;

import br.dev.guisleri.agendaarena.establishment.entity.Establishment;
import br.dev.guisleri.agendaarena.establishment.repository.EstablishmentRepository;
import br.dev.guisleri.agendaarena.sportfield.entity.SportField;
import br.dev.guisleri.agendaarena.sportfield.model.SportType;
import br.dev.guisleri.agendaarena.sportfield.repository.SportFieldRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.Map;

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
class SportFieldIntegrationTests {

    private static final String BASE_PATH = "/establishments/{establishmentId}/sports-fields";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EstablishmentRepository establishmentRepository;

    @Autowired
    private SportFieldRepository sportFieldRepository;

    @DynamicPropertySource
    static void configurePostgreSQL(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void cleanDatabase() {
        sportFieldRepository.deleteAll();
        establishmentRepository.deleteAll();
    }

    @Test
    void shouldCreateSportFieldWithValidNameAndSportType() throws Exception {
        Establishment establishment = createEstablishment("Arena Central");

        String response = createSportField(establishment.getId(), "Campo 1", "SOCIETY")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.establishmentId").value(establishment.getId()))
                .andExpect(jsonPath("$.name").value("Campo 1"))
                .andExpect(jsonPath("$.sportType").value("SOCIETY"))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn().getResponse().getContentAsString();

        Number returnedId = JsonPath.read(response, "$.id");
        SportField persistedSportField = sportFieldRepository.findById(returnedId.longValue()).orElseThrow();

        assertThat(sportFieldRepository.count()).isEqualTo(1);
        assertThat(persistedSportField.getEstablishment().getId()).isEqualTo(establishment.getId());
        assertThat(persistedSportField.getName()).isEqualTo("Campo 1");
        assertThat(persistedSportField.getSportType()).isEqualTo(SportType.SOCIETY);
        assertThat(persistedSportField.isActive()).isTrue();
    }

    @Test
    void shouldReturnNotFoundWhenCreatingSportFieldForUnknownEstablishment() throws Exception {
        assertError(createSportField(Long.MAX_VALUE, "Campo 1", "SOCIETY"), 404, "Not Found")
                .andExpect(jsonPath("$.message").value(containsString("Establishment with id " + Long.MAX_VALUE)));
        assertThat(sportFieldRepository.count()).isZero();
    }

    @Test
    void shouldRejectEmptyName() throws Exception {
        assertInvalidName("");
    }

    @Test
    void shouldRejectNameContainingOnlySpaces() throws Exception {
        assertInvalidName("   ");
    }

    @Test
    void shouldRejectNameLongerThan120Characters() throws Exception {
        assertInvalidName("a".repeat(121));
    }

    @Test
    void shouldRejectInvalidSportType() throws Exception {
        Establishment establishment = createEstablishment("Arena Central");

        assertError(createSportField(establishment.getId(), "Campo 1", "INVALID"), 400, "Bad Request")
                .andExpect(jsonPath("$.message").value("Invalid request body"));
        assertThat(sportFieldRepository.count()).isZero();
    }

    @Test
    void shouldRejectDuplicateNameInSameEstablishment() throws Exception {
        Establishment establishment = createEstablishment("Arena Central");
        createSportField(establishment.getId(), "Campo 1", "SOCIETY").andExpect(status().isCreated());

        assertError(createSportField(establishment.getId(), "Campo 1", "SOCIETY"), 409, "Conflict")
                .andExpect(jsonPath("$.message").value(containsString("already exists")));
        assertThat(sportFieldRepository.count()).isEqualTo(1);
    }

    @Test
    void shouldAllowSameNameInDifferentEstablishments() throws Exception {
        Establishment first = createEstablishment("Arena Norte");
        Establishment second = createEstablishment("Arena Sul");

        createSportField(first.getId(), "Campo 1", "SOCIETY")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.establishmentId").value(first.getId()));
        createSportField(second.getId(), "Campo 1", "SOCIETY")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.establishmentId").value(second.getId()));

        assertThat(sportFieldRepository.count()).isEqualTo(2);
        assertThat(sportFieldRepository.findAllByEstablishmentId(first.getId()))
                .extracting(SportField::getName).containsExactly("Campo 1");
        assertThat(sportFieldRepository.findAllByEstablishmentId(second.getId()))
                .extracting(SportField::getName).containsExactly("Campo 1");
    }

    @Test
    void shouldListAllSportFieldsForEstablishment() throws Exception {
        Establishment establishment = createEstablishment("Arena Central");
        SportField first = persistSportField(establishment, "Campo 1");
        SportField second = persistSportField(establishment, "Campo 2");

        String response = mockMvc.perform(get(BASE_PATH, establishment.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andReturn().getResponse().getContentAsString();

        List<Map<String, Object>> sportFields = JsonPath.read(response, "$[*]");
        assertThat(sportFields).anySatisfy(item -> assertSportField(item, first));
        assertThat(sportFields).anySatisfy(item -> assertSportField(item, second));
    }

    @Test
    void shouldReturnEmptyListWhenEstablishmentHasNoSportFields() throws Exception {
        Establishment establishment = createEstablishment("Arena Central");

        mockMvc.perform(get(BASE_PATH, establishment.getId()))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void shouldReturnNotFoundWhenListingSportFieldsForUnknownEstablishment() throws Exception {
        assertError(mockMvc.perform(get(BASE_PATH, Long.MAX_VALUE)), 404, "Not Found")
                .andExpect(jsonPath("$.message").value(containsString("Establishment with id " + Long.MAX_VALUE)));
    }

    @Test
    void shouldNotMixSportFieldsBetweenEstablishments() throws Exception {
        Establishment first = createEstablishment("Arena Norte");
        Establishment second = createEstablishment("Arena Sul");
        SportField firstSportField = persistSportField(first, "Campo Norte");
        SportField secondSportField = persistSportField(second, "Campo Sul");

        assertListContainsOnly(first, firstSportField);
        assertListContainsOnly(second, secondSportField);
    }

    @Test
    void shouldFindExistingSportFieldById() throws Exception {
        Establishment establishment = createEstablishment("Arena Central");
        SportField sportField = persistSportField(establishment, "Campo 1");

        mockMvc.perform(get(BASE_PATH + "/{sportFieldId}", establishment.getId(), sportField.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sportField.getId()))
                .andExpect(jsonPath("$.establishmentId").value(establishment.getId()))
                .andExpect(jsonPath("$.name").value("Campo 1"))
                .andExpect(jsonPath("$.sportType").value("SOCIETY"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void shouldReturnNotFoundForUnknownSportFieldId() throws Exception {
        Establishment establishment = createEstablishment("Arena Central");

        assertError(mockMvc.perform(get(BASE_PATH + "/{sportFieldId}",
                establishment.getId(), Long.MAX_VALUE)), 404, "Not Found")
                .andExpect(jsonPath("$.message").value(containsString("Sport field with id " + Long.MAX_VALUE)));
    }

    @Test
    void shouldReturnNotFoundForSportFieldBelongingToAnotherEstablishment() throws Exception {
        Establishment first = createEstablishment("Arena Norte");
        Establishment second = createEstablishment("Arena Sul");
        SportField sportField = persistSportField(first, "Campo 1");

        assertError(mockMvc.perform(get(BASE_PATH + "/{sportFieldId}",
                second.getId(), sportField.getId())), 404, "Not Found")
                .andExpect(jsonPath("$.message").value(containsString("not found for establishment " + second.getId())));
    }

    private Establishment createEstablishment(String name) {
        return establishmentRepository.save(new Establishment(name));
    }

    private SportField persistSportField(Establishment establishment, String name) {
        return sportFieldRepository.save(new SportField(establishment, name, SportType.SOCIETY));
    }

    private ResultActions createSportField(Long establishmentId, String name, String sportType) throws Exception {
        return mockMvc.perform(post(BASE_PATH, establishmentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"%s","sportType":"%s"}
                        """.formatted(name, sportType)));
    }

    private void assertInvalidName(String name) throws Exception {
        Establishment establishment = createEstablishment("Arena Central");

        assertError(createSportField(establishment.getId(), name, "SOCIETY"), 400, "Bad Request")
                .andExpect(jsonPath("$.errors.name").isNotEmpty());
        assertThat(sportFieldRepository.count()).isZero();
    }

    private ResultActions assertError(ResultActions result, int statusCode, String error) throws Exception {
        return result.andExpect(status().is(statusCode))
                .andExpect(jsonPath("$.status").value(statusCode))
                .andExpect(jsonPath("$.error").value(error))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.errors").isMap());
    }

    private void assertListContainsOnly(Establishment establishment, SportField sportField) throws Exception {
        String response = mockMvc.perform(get(BASE_PATH, establishment.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andReturn().getResponse().getContentAsString();

        List<Map<String, Object>> sportFields = JsonPath.read(response, "$[*]");
        assertSportField(sportFields.getFirst(), sportField);
    }

    private void assertSportField(Map<String, Object> response, SportField expected) {
        assertThat(((Number) response.get("id")).longValue()).isEqualTo(expected.getId());
        assertThat(((Number) response.get("establishmentId")).longValue())
                .isEqualTo(expected.getEstablishment().getId());
        assertThat(response.get("name")).isEqualTo(expected.getName());
        assertThat(response.get("sportType")).isEqualTo(expected.getSportType().name());
        assertThat(response.get("active")).isEqualTo(expected.isActive());
    }
}
