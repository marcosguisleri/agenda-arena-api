package br.dev.guisleri.agendaarena.match;

import br.dev.guisleri.agendaarena.auth.service.JwtTokenService;
import br.dev.guisleri.agendaarena.establishment.entity.Establishment;
import br.dev.guisleri.agendaarena.establishment.repository.EstablishmentRepository;
import br.dev.guisleri.agendaarena.establishment.service.EstablishmentService;
import br.dev.guisleri.agendaarena.pix.model.PixKeyType;
import br.dev.guisleri.agendaarena.reservation.entity.RecurringReservation;
import br.dev.guisleri.agendaarena.reservation.repository.RecurringReservationRepository;
import br.dev.guisleri.agendaarena.schedule.entity.ScheduleSlot;
import br.dev.guisleri.agendaarena.schedule.repository.ScheduleSlotRepository;
import br.dev.guisleri.agendaarena.sportfield.entity.SportField;
import br.dev.guisleri.agendaarena.sportfield.model.SportType;
import br.dev.guisleri.agendaarena.sportfield.repository.SportFieldRepository;
import br.dev.guisleri.agendaarena.user.entity.User;
import br.dev.guisleri.agendaarena.user.model.UserRole;
import br.dev.guisleri.agendaarena.user.service.UserService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Escolha da chave Pix da partida: a do estabelecimento (copiada) ou uma do organizador, na
 * criação (reserva avulsa e ocorrência de recorrência) e depois. Usa tokens reais.
 */
@SpringBootTest
@Testcontainers
class MatchPixIntegrationTests {

    private static final String PASSWORD = "password-123";
    private static final String ESTABLISHMENT_PIX_NAME = "Arena Central Ltda";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private UserService userService;
    @Autowired
    private JwtTokenService jwtTokenService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private Clock clock;
    @Autowired
    private EstablishmentRepository establishmentRepository;
    @Autowired
    private EstablishmentService establishmentService;
    @Autowired
    private SportFieldRepository sportFieldRepository;
    @Autowired
    private ScheduleSlotRepository scheduleSlotRepository;
    @Autowired
    private RecurringReservationRepository recurringReservationRepository;

    private MockMvc mockMvc;
    private Establishment establishment;
    private ScheduleSlot scheduleSlot;
    private SportField sportField;
    private User organizer;
    private User otherOrganizer;
    private LocalDate baseDate;

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

        baseDate = LocalDate.now(clock)
                .plusDays(14)
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));

        organizer = user("organizer@example.com");
        otherOrganizer = user("other.organizer@example.com");

        establishment = establishmentRepository.save(new Establishment("Arena Central"));
        sportField = sportFieldRepository.save(
                new SportField(establishment, "Campo 1", SportType.SOCIETY));
        scheduleSlot = scheduleSlotRepository.save(
                new ScheduleSlot(
                        sportField,
                        DayOfWeek.MONDAY,
                        LocalTime.of(19, 0),
                        new BigDecimal("150.00")
                ));
    }

    // ---------- Reserva avulsa ----------

    @Nested
    @DisplayName("Create match from a reservation")
    class CreateFromReservation {

        @Test
        void shouldCreateWithoutPixWhenThereIsNoBody() throws Exception {
            long reservationId = reserve(baseDate);

            createFromReservation(reservationId, null)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.pix").value(nullValue()));

            assertThat(storedPixKey()).isNull();
        }

        @Test
        void shouldCreateWithoutPixWhenBodyHasNoPixChoice() throws Exception {
            long reservationId = reserve(baseDate);

            createFromReservation(reservationId, "{}")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.pix").value(nullValue()));
        }

        @Test
        void shouldCopyTheEstablishmentKey() throws Exception {
            configureEstablishmentPix();
            long reservationId = reserve(baseDate);

            createFromReservation(reservationId, "{\"pix\": {\"source\": \"ESTABLISHMENT\"}}")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.pix.source").value("ESTABLISHMENT"))
                    .andExpect(jsonPath("$.pix.keyType").value("CNPJ"))
                    .andExpect(jsonPath("$.pix.key").value("11222333000181"))
                    .andExpect(jsonPath("$.pix.holderName").value(ESTABLISHMENT_PIX_NAME))
                    .andExpect(jsonPath("$.pix.bankName").value("Banco do Brasil"));

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT pix_source || '|' || pix_key_type || '|' || pix_key "
                            + "|| '|' || pix_holder_name FROM matches",
                    String.class
            )).isEqualTo("ESTABLISHMENT|CNPJ|11222333000181|" + ESTABLISHMENT_PIX_NAME);
        }

        @Test
        void shouldUseTheOrganizersOwnKey() throws Exception {
            long reservationId = reserve(baseDate);

            createFromReservation(reservationId, "{\"pix\": " + organizerPix() + "}")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.pix.source").value("ORGANIZER"))
                    .andExpect(jsonPath("$.pix.keyType").value("PHONE"))
                    .andExpect(jsonPath("$.pix.key").value("+5519999998888"))
                    .andExpect(jsonPath("$.pix.holderName").value("Maria da Silva"))
                    .andExpect(jsonPath("$.pix.bankName").value("Nubank"));
        }

        @Test
        void shouldConflictWhenTheEstablishmentHasNoKeyAndCreateNothing() throws Exception {
            long reservationId = reserve(baseDate);

            createFromReservation(reservationId, "{\"pix\": {\"source\": \"ESTABLISHMENT\"}}")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message")
                            .value("Establishment has no Pix key configured"));

            assertThat(matchCount()).isZero();
        }

        @Test
        void shouldRejectKeyFieldsTogetherWithTheEstablishmentSource() throws Exception {
            configureEstablishmentPix();
            long reservationId = reserve(baseDate);

            createFromReservation(reservationId,
                    "{\"pix\": {\"source\": \"ESTABLISHMENT\", \"key\": \"a@b.co\"}}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.source").isNotEmpty());

            assertThat(matchCount()).isZero();
        }

        @Test
        void shouldRejectAnInvalidOrganizerKeyAndCreateNothing() throws Exception {
            long reservationId = reserve(baseDate);

            createFromReservation(reservationId,
                    "{\"pix\": {\"source\": \"ORGANIZER\", \"keyType\": \"CPF\","
                            + " \"key\": \"111.111.111-11\", \"holderName\": \"Maria da Silva\"}}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.key").isNotEmpty());
            createFromReservation(reservationId,
                    "{\"pix\": {\"source\": \"ORGANIZER\", \"keyType\": \"EMAIL\","
                            + " \"key\": \"a@b.co\", \"holderName\": \"Maria\"}}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.holderName").isNotEmpty());
            createFromReservation(reservationId,
                    "{\"pix\": {\"source\": \"ORGANIZER\"}}")
                    .andExpect(status().isBadRequest());

            assertThat(matchCount()).isZero();
        }

        @Test
        void shouldRejectAPixChoiceWithoutSource() throws Exception {
            long reservationId = reserve(baseDate);

            createFromReservation(reservationId, "{\"pix\": {}}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors").isNotEmpty());

            assertThat(matchCount()).isZero();
        }

        @Test
        void shouldIgnoreTheBodyWhenTheMatchAlreadyExists() throws Exception {
            long reservationId = reserve(baseDate);
            createFromReservation(reservationId, "{\"pix\": " + organizerPix() + "}")
                    .andExpect(status().isCreated());

            configureEstablishmentPix();
            createFromReservation(reservationId, "{\"pix\": {\"source\": \"ESTABLISHMENT\"}}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pix.source").value("ORGANIZER"))
                    .andExpect(jsonPath("$.pix.keyType").value("PHONE"));
        }
    }

    // ---------- Ocorrência de recorrência ----------

    @Nested
    @DisplayName("Create match from a recurring occurrence")
    class CreateFromOccurrence {

        @Test
        void shouldCopyTheEstablishmentKey() throws Exception {
            configureEstablishmentPix();
            long seriesId = series();

            createFromOccurrence(seriesId, "{\"pix\": {\"source\": \"ESTABLISHMENT\"}}")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.recurringReservationId").value(seriesId))
                    .andExpect(jsonPath("$.pix.source").value("ESTABLISHMENT"))
                    .andExpect(jsonPath("$.pix.key").value("11222333000181"));
        }

        @Test
        void shouldUseTheOrganizersOwnKeyAndAllowNoBody() throws Exception {
            long seriesId = series();

            createFromOccurrence(seriesId, "{\"pix\": " + organizerPix() + "}")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.pix.source").value("ORGANIZER"));

            jdbcTemplate.execute("DELETE FROM matches");

            createFromOccurrence(seriesId, null)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.pix").value(nullValue()));
        }

        @Test
        void shouldConflictWhenTheEstablishmentHasNoKeyAndCreateNothing() throws Exception {
            long seriesId = series();

            createFromOccurrence(seriesId, "{\"pix\": {\"source\": \"ESTABLISHMENT\"}}")
                    .andExpect(status().isConflict());

            assertThat(matchCount()).isZero();
        }
    }

    // ---------- Depois da criação ----------

    @Nested
    @DisplayName("Change the Pix key of an existing match")
    class ChangeAfterCreation {

        @Test
        void shouldKeepTheCopyWhenTheEstablishmentChangesAndRefreshWhenTheOrganizerReselects()
                throws Exception {
            configureEstablishmentPix();
            long matchId = createMatchWithEstablishmentPix();

            establishmentService.changePixKey(
                    establishment.getId(),
                    PixKeyType.EMAIL,
                    "novo@arena.com",
                    "Arena Central Ltda",
                    null
            );

            // A partida continua com a chave que foi copiada.
            myMatch(matchId)
                    .andExpect(jsonPath("$.pix.key").value("11222333000181"));

            // Reescolher a do estabelecimento atualiza a cópia.
            putPix(matchId, "{\"source\": \"ESTABLISHMENT\"}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pix.source").value("ESTABLISHMENT"))
                    .andExpect(jsonPath("$.pix.keyType").value("EMAIL"))
                    .andExpect(jsonPath("$.pix.key").value("novo@arena.com"))
                    .andExpect(jsonPath("$.pix.bankName").value(nullValue()));
        }

        @Test
        void shouldSwitchBetweenSourcesAndRemove() throws Exception {
            configureEstablishmentPix();
            long matchId = createMatchWithEstablishmentPix();

            putPix(matchId, organizerPix())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pix.source").value("ORGANIZER"))
                    .andExpect(jsonPath("$.pix.key").value("+5519999998888"));

            putPix(matchId, "{\"source\": \"ESTABLISHMENT\"}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pix.source").value("ESTABLISHMENT"));

            mockMvc.perform(delete("/me/matches/{id}/pix-key", matchId)
                            .header("Authorization", bearer(organizer)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pix").value(nullValue()));
            assertThat(storedPixKey()).isNull();
        }

        @Test
        void shouldKeepTheCurrentKeyWhenTheEstablishmentOptionFails() throws Exception {
            long reservationId = reserve(baseDate);
            long matchId = idOf(createFromReservation(reservationId,
                    "{\"pix\": " + organizerPix() + "}").andExpect(status().isCreated()));

            putPix(matchId, "{\"source\": \"ESTABLISHMENT\"}")
                    .andExpect(status().isConflict());

            myMatch(matchId)
                    .andExpect(jsonPath("$.pix.source").value("ORGANIZER"))
                    .andExpect(jsonPath("$.pix.key").value("+5519999998888"));
        }

        @Test
        void shouldRejectSourceWithoutBodyFieldsAndInvalidChoices() throws Exception {
            long reservationId = reserve(baseDate);
            long matchId = idOf(createFromReservation(reservationId, null)
                    .andExpect(status().isCreated()));

            putPix(matchId, "{}").andExpect(status().isBadRequest());
            putPix(matchId, "{\"source\": \"ORGANIZER\"}").andExpect(status().isBadRequest());
            putPix(matchId, "{\"source\": \"NOPE\"}").andExpect(status().isBadRequest());

            myMatch(matchId).andExpect(jsonPath("$.pix").value(nullValue()));
        }

        @Test
        void shouldHideTheMatchFromAnotherOrganizerWithoutSideEffects() throws Exception {
            configureEstablishmentPix();
            long matchId = createMatchWithEstablishmentPix();

            mockMvc.perform(put("/me/matches/{id}/pix-key", matchId)
                            .header("Authorization", bearer(otherOrganizer))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(organizerPix()))
                    .andExpect(status().isNotFound());
            mockMvc.perform(delete("/me/matches/{id}/pix-key", matchId)
                            .header("Authorization", bearer(otherOrganizer)))
                    .andExpect(status().isNotFound());

            myMatch(matchId).andExpect(jsonPath("$.pix.source").value("ESTABLISHMENT"));
        }

        @Test
        void shouldRequireAuthentication() throws Exception {
            mockMvc.perform(put("/me/matches/{id}/pix-key", 1)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(organizerPix()))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(delete("/me/matches/{id}/pix-key", 1))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Public match page")
    class PublicPage {

        @Test
        void shouldShowTheKeyHolderAndBankToGuestsWithoutInternalIds() throws Exception {
            configureEstablishmentPix();
            long reservationId = reserve(baseDate);
            String response = createFromReservation(reservationId,
                    "{\"pix\": {\"source\": \"ESTABLISHMENT\"}}")
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            String token = JsonPath.read(response, "$.publicToken");

            String publicBody = mockMvc.perform(get("/matches/{token}", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pix.source").value("ESTABLISHMENT"))
                    .andExpect(jsonPath("$.pix.keyType").value("CNPJ"))
                    .andExpect(jsonPath("$.pix.key").value("11222333000181"))
                    .andExpect(jsonPath("$.pix.holderName").value(ESTABLISHMENT_PIX_NAME))
                    .andExpect(jsonPath("$.pix.bankName").value("Banco do Brasil"))
                    .andReturn().getResponse().getContentAsString();

            assertThat(publicBody).doesNotContain("\"id\"");
        }

        @Test
        void shouldReturnNullPixWhenTheMatchHasNone() throws Exception {
            long reservationId = reserve(baseDate);
            String response = createFromReservation(reservationId, null)
                    .andReturn().getResponse().getContentAsString();
            String token = JsonPath.read(response, "$.publicToken");

            mockMvc.perform(get("/matches/{token}", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pix").value(nullValue()));
        }
    }

    // ---------- Apoio ----------

    private void configureEstablishmentPix() {
        establishmentService.changePixKey(
                establishment.getId(),
                PixKeyType.CNPJ,
                "11.222.333/0001-81",
                ESTABLISHMENT_PIX_NAME,
                "Banco do Brasil"
        );
    }

    private static String organizerPix() {
        return """
                {"source": "ORGANIZER", "keyType": "PHONE", "key": "(19) 99999-8888",
                 "holderName": "Maria da Silva", "bankName": "Nubank"}
                """;
    }

    private long createMatchWithEstablishmentPix() throws Exception {
        long reservationId = reserve(baseDate);

        return idOf(createFromReservation(reservationId,
                "{\"pix\": {\"source\": \"ESTABLISHMENT\"}}").andExpect(status().isCreated()));
    }

    private ResultActions createFromReservation(long reservationId, String body)
            throws Exception {
        return withBody(
                post("/me/reservations/" + reservationId + "/match")
                        .header("Authorization", bearer(organizer)),
                body
        );
    }

    private ResultActions createFromOccurrence(long seriesId, String body) throws Exception {
        return withBody(
                post("/me/recurring-reservations/" + seriesId + "/occurrences/" + baseDate
                        + "/match")
                        .header("Authorization", bearer(organizer)),
                body
        );
    }

    private ResultActions withBody(MockHttpServletRequestBuilder request, String body)
            throws Exception {
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }

        return mockMvc.perform(request);
    }

    private ResultActions putPix(long matchId, String body) throws Exception {
        return mockMvc.perform(put("/me/matches/{id}/pix-key", matchId)
                .header("Authorization", bearer(organizer))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions myMatch(long matchId) throws Exception {
        return mockMvc.perform(get("/me/matches/{id}", matchId)
                        .header("Authorization", bearer(organizer)))
                .andExpect(status().isOk());
    }

    private long idOf(ResultActions result) throws Exception {
        Number id = JsonPath.read(
                result.andReturn().getResponse().getContentAsString(), "$.id");

        return id.longValue();
    }

    private long reserve(LocalDate date) throws Exception {
        String response = mockMvc.perform(post(
                                "/establishments/%d/sports-fields/%d/schedule-slots/%d/reservations"
                                        .formatted(
                                                establishment.getId(),
                                                sportField.getId(),
                                                scheduleSlot.getId()))
                        .header("Authorization", bearer(organizer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reservationDate\": \"%s\"}".formatted(date)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Number id = JsonPath.read(response, "$.id");

        return id.longValue();
    }

    private long series() {
        return recurringReservationRepository.saveAndFlush(
                new RecurringReservation(
                        scheduleSlot,
                        organizer,
                        "Maria da Silva",
                        "11999991234",
                        baseDate,
                        null
                )
        ).getId();
    }

    private long matchCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM matches", Long.class);
    }

    private String storedPixKey() {
        return jdbcTemplate.queryForObject("SELECT pix_key FROM matches", String.class);
    }

    private User user(String email) {
        return userService.createUser(
                "Usuario " + email, email, "11999991234", PASSWORD, UserRole.ORGANIZER);
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenService.issue(user).accessToken();
    }
}
