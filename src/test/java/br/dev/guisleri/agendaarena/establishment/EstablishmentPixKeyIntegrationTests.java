package br.dev.guisleri.agendaarena.establishment;

import br.dev.guisleri.agendaarena.establishment.entity.Establishment;
import br.dev.guisleri.agendaarena.establishment.repository.EstablishmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Chave Pix do estabelecimento (gestão). O {@code MockMvc} autoconfigurado já chama como
 * PLATFORM_ADMIN; 404/403/401 por estabelecimento ficam em
 * {@code EstablishmentAuthorizationIntegrationTests}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class EstablishmentPixKeyIntegrationTests {

    private static final String PIX_PATH = "/establishments/{establishmentId}/pix-key";
    private static final String VALID_BODY = """
            {"keyType": "CPF", "key": "529.982.247-25", "holderName": "Maria da Silva",
             "bankName": "Banco do Brasil"}
            """;

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EstablishmentRepository establishmentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Establishment establishment;

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
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE establishments RESTART IDENTITY CASCADE");

        establishment = establishmentRepository.save(new Establishment("Arena Central"));
    }

    @Test
    void shouldSetAndReadNormalizedPixKey() throws Exception {
        putPix(VALID_BODY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keyType").value("CPF"))
                .andExpect(jsonPath("$.key").value("52998224725"))
                .andExpect(jsonPath("$.holderName").value("Maria da Silva"))
                .andExpect(jsonPath("$.bankName").value("Banco do Brasil"));

        mockMvc.perform(get(PIX_PATH, establishment.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("52998224725"))
                .andExpect(jsonPath("$.holderName").value("Maria da Silva"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT pix_key_type || '|' || pix_key || '|' || pix_holder_name "
                        + "FROM establishments WHERE id = ?",
                String.class, establishment.getId()
        )).isEqualTo("CPF|52998224725|Maria da Silva");
    }

    @Test
    void shouldReplaceTheKeyAndKeepBankOptional() throws Exception {
        putPix(VALID_BODY).andExpect(status().isOk());

        putPix("""
                {"keyType": "EMAIL", "key": "Pagamentos@Arena.com",
                 "holderName": "Arena Central Ltda"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keyType").value("EMAIL"))
                .andExpect(jsonPath("$.key").value("pagamentos@arena.com"))
                .andExpect(jsonPath("$.bankName").value(nullValue()));

        mockMvc.perform(get(PIX_PATH, establishment.getId()))
                .andExpect(jsonPath("$.keyType").value("EMAIL"))
                .andExpect(jsonPath("$.bankName").value(nullValue()));
    }

    @Test
    void shouldReturnNotFoundWhenNoKeyIsConfigured() throws Exception {
        mockMvc.perform(get(PIX_PATH, establishment.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Establishment has no Pix key configured"));
    }

    @Test
    void shouldRemoveTheKeyAndBeIdempotent() throws Exception {
        putPix(VALID_BODY).andExpect(status().isOk());

        mockMvc.perform(delete(PIX_PATH, establishment.getId()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete(PIX_PATH, establishment.getId()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(PIX_PATH, establishment.getId()))
                .andExpect(status().isNotFound());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT pix_key FROM establishments WHERE id = ?",
                String.class, establishment.getId())).isNull();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidBodies")
    void shouldRejectInvalidKey(String name, String body, String field) throws Exception {
        putPix(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors." + field).isNotEmpty());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT pix_key FROM establishments WHERE id = ?",
                String.class, establishment.getId())).isNull();
    }

    static Stream<Arguments> invalidBodies() {
        return Stream.of(
                Arguments.of("missing key type",
                        "{\"key\": \"a@b.co\", \"holderName\": \"Maria da Silva\"}", "keyType"),
                Arguments.of("blank key",
                        "{\"keyType\": \"EMAIL\", \"key\": \" \", \"holderName\": \"Maria da Silva\"}",
                        "key"),
                Arguments.of("invalid CPF",
                        "{\"keyType\": \"CPF\", \"key\": \"111.111.111-11\", "
                                + "\"holderName\": \"Maria da Silva\"}", "key"),
                Arguments.of("invalid phone",
                        "{\"keyType\": \"PHONE\", \"key\": \"123\", "
                                + "\"holderName\": \"Maria da Silva\"}", "key"),
                Arguments.of("holder name with one word",
                        "{\"keyType\": \"EMAIL\", \"key\": \"a@b.co\", \"holderName\": \"Maria\"}",
                        "holderName"),
                Arguments.of("missing holder name",
                        "{\"keyType\": \"EMAIL\", \"key\": \"a@b.co\"}", "holderName"),
                Arguments.of("bank name too long",
                        "{\"keyType\": \"EMAIL\", \"key\": \"a@b.co\", "
                                + "\"holderName\": \"Maria da Silva\", \"bankName\": \""
                                + "b".repeat(81) + "\"}", "bankName")
        );
    }

    @Test
    void shouldRejectUnknownKeyTypeWithTheStandardErrorBody() throws Exception {
        putPix("{\"keyType\": \"NOPE\", \"key\": \"a@b.co\", \"holderName\": \"Maria da Silva\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void shouldReturnNotFoundForUnknownEstablishment() throws Exception {
        mockMvc.perform(put(PIX_PATH, 999999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(PIX_PATH, 999999L)).andExpect(status().isNotFound());
        mockMvc.perform(delete(PIX_PATH, 999999L)).andExpect(status().isNotFound());
    }

    @Test
    void shouldNeverExposeThePixKeyInThePublicCatalog() throws Exception {
        putPix(VALID_BODY).andExpect(status().isOk());

        String list = mockMvc.perform(get("/establishments"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String single = mockMvc.perform(get("/establishments/{id}", establishment.getId()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        for (String body : new String[]{list, single}) {
            assertThat(body).doesNotContain("52998224725", "Maria da Silva", "Banco do Brasil");
            assertThat(body.toLowerCase()).doesNotContain("pix");
        }
    }

    @Test
    void shouldLetTheDatabaseRejectAPartialKey() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE establishments SET pix_key = 'a@b.co' WHERE id = ?",
                establishment.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE establishments SET pix_key = 'a@b.co', pix_key_type = 'NOPE', "
                        + "pix_holder_name = 'Maria da Silva' WHERE id = ?",
                establishment.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private ResultActions putPix(String body) throws Exception {
        return mockMvc.perform(put(PIX_PATH, establishment.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
