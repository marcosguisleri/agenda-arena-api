package br.dev.guisleri.agendaarena.establishment;

import br.dev.guisleri.agendaarena.establishment.entity.Establishment;
import br.dev.guisleri.agendaarena.establishment.repository.EstablishmentRepository;
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
class EstablishmentIntegrationTests {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EstablishmentRepository establishmentRepository;

    @DynamicPropertySource
    static void configurePostgreSQL(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void cleanDatabase() {
        establishmentRepository.deleteAll();
    }

    @Test
    void shouldCreateEstablishmentWithValidName() throws Exception {
        String response = mockMvc.perform(post("/establishments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Arena Central"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Arena Central"))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Number returnedId = JsonPath.read(response, "$.id");
        Establishment persistedEstablishment = establishmentRepository
                .findById(returnedId.longValue())
                .orElseThrow();

        assertThat(establishmentRepository.count()).isEqualTo(1);
        assertThat(persistedEstablishment.getName()).isEqualTo("Arena Central");
        assertThat(persistedEstablishment.isActive()).isTrue();
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
    void shouldFindExistingEstablishmentById() throws Exception {
        Establishment establishment = establishmentRepository.save(new Establishment("Arena Norte"));

        mockMvc.perform(get("/establishments/{id}", establishment.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(establishment.getId()))
                .andExpect(jsonPath("$.name").value("Arena Norte"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void shouldReturnNotFoundForUnknownEstablishmentId() throws Exception {
        long unknownId = Long.MAX_VALUE;

        mockMvc.perform(get("/establishments/{id}", unknownId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value(containsString(
                        "Establishment with id " + unknownId + " not found"
                )))
                .andExpect(jsonPath("$.errors").isMap());
    }

    @Test
    void shouldListAllEstablishments() throws Exception {
        Establishment first = establishmentRepository.save(new Establishment("Arena Sul"));
        Establishment second = establishmentRepository.save(new Establishment("Arena Leste"));

        String response = mockMvc.perform(get("/establishments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<Map<String, Object>> establishments = JsonPath.read(response, "$[*]");

        assertThat(establishments).anySatisfy(item -> assertEstablishment(item, first));
        assertThat(establishments).anySatisfy(item -> assertEstablishment(item, second));
    }

    @Test
    void shouldReturnEmptyListWhenThereAreNoEstablishments() throws Exception {
        mockMvc.perform(get("/establishments"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    private void assertInvalidName(String name) throws Exception {
        mockMvc.perform(post("/establishments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s"}
                                """.formatted(name)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.errors").isMap())
                .andExpect(jsonPath("$.errors.name").isNotEmpty());

        assertThat(establishmentRepository.count()).isZero();
    }

    private void assertEstablishment(Map<String, Object> response, Establishment expected) {
        assertThat(((Number) response.get("id")).longValue()).isEqualTo(expected.getId());
        assertThat(response.get("name")).isEqualTo(expected.getName());
        assertThat(response.get("active")).isEqualTo(expected.isActive());
    }
}
