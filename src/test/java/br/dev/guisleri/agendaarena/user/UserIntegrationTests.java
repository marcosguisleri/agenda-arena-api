package br.dev.guisleri.agendaarena.user;

import br.dev.guisleri.agendaarena.user.entity.User;
import br.dev.guisleri.agendaarena.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class UserIntegrationTests {

    private static final String ORIGINAL_PASSWORD = "senha-segura";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @DynamicPropertySource
    static void configurePostgreSQL(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAll();
    }

    @Test
    void shouldCreateUserWithNormalizedDataAndEncryptedPassword() throws Exception {
        String response = createUser(
                "  Maria da Silva  ",
                "MARIA.SILVA@EXAMPLE.COM",
                "(11) 99999-1234",
                ORIGINAL_PASSWORD
        )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Maria da Silva"))
                .andExpect(jsonPath("$.email").value("maria.silva@example.com"))
                .andExpect(jsonPath("$.phone").value("11999991234"))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Number returnedId = JsonPath.read(response, "$.id");
        User persistedUser = userRepository.findById(returnedId.longValue()).orElseThrow();

        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(persistedUser.getName()).isEqualTo("Maria da Silva");
        assertThat(persistedUser.getEmail()).isEqualTo("maria.silva@example.com");
        assertThat(persistedUser.getPhone()).isEqualTo("11999991234");
        assertThat(persistedUser.getPasswordHash()).isNotEqualTo(ORIGINAL_PASSWORD);
        assertThat(passwordEncoder).isInstanceOf(BCryptPasswordEncoder.class);
        assertThat(passwordEncoder.matches(ORIGINAL_PASSWORD, persistedUser.getPasswordHash())).isTrue();
        assertThat(persistedUser.isActive()).isTrue();
    }

    @Test
    void shouldNotExposePasswordOrPasswordHashInResponse() throws Exception {
        createUser("Maria", "maria@example.com", "11999991234", ORIGINAL_PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void shouldRejectDuplicateEmail() throws Exception {
        createUser("Maria", "maria@example.com", "11999991234", ORIGINAL_PASSWORD)
                .andExpect(status().isCreated());

        assertConflict(createUser("Outra Maria", "maria@example.com", "11988881234", ORIGINAL_PASSWORD));
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void shouldRejectDuplicateEmailIgnoringCase() throws Exception {
        createUser("Maria", "maria@example.com", "11999991234", ORIGINAL_PASSWORD)
                .andExpect(status().isCreated());

        assertConflict(createUser("Outra Maria", "MARIA@EXAMPLE.COM", "11988881234", ORIGINAL_PASSWORD));
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void shouldRejectEmptyName() throws Exception {
        assertValidationError(
                createUser("", "maria@example.com", "11999991234", ORIGINAL_PASSWORD),
                "name"
        );
    }

    @Test
    void shouldRejectInvalidEmail() throws Exception {
        assertValidationError(
                createUser("Maria", "invalid-email", "11999991234", ORIGINAL_PASSWORD),
                "email"
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234567", "telefone"})
    void shouldRejectInvalidOrShortPhone(String phone) throws Exception {
        assertValidationError(
                createUser("Maria", "maria@example.com", phone, ORIGINAL_PASSWORD),
                "phone"
        );
    }

    @Test
    void shouldRejectShortPassword() throws Exception {
        assertValidationError(
                createUser("Maria", "maria@example.com", "11999991234", "1234567"),
                "password"
        );
    }

    private ResultActions createUser(String name, String email, String phone, String password) throws Exception {
        return mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "name": "%s",
                          "email": "%s",
                          "phone": "%s",
                          "password": "%s"
                        }
                        """.formatted(name, email, phone, password)));
    }

    private void assertConflict(ResultActions result) throws Exception {
        result.andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value(containsString("already exists")))
                .andExpect(jsonPath("$.errors").isMap());
    }

    private void assertValidationError(ResultActions result, String field) throws Exception {
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors.%s".formatted(field)).isNotEmpty());

        assertThat(userRepository.count()).isZero();
    }
}
