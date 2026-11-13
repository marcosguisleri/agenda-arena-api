package br.dev.guisleri.agendaarena.security;

import br.dev.guisleri.agendaarena.auth.service.JwtTokenService;
import br.dev.guisleri.agendaarena.establishment.entity.Establishment;
import br.dev.guisleri.agendaarena.establishment.entity.EstablishmentAdmin;
import br.dev.guisleri.agendaarena.establishment.repository.EstablishmentAdminRepository;
import br.dev.guisleri.agendaarena.establishment.repository.EstablishmentRepository;
import br.dev.guisleri.agendaarena.reservation.entity.RecurringReservation;
import br.dev.guisleri.agendaarena.reservation.repository.RecurringReservationRepository;
import br.dev.guisleri.agendaarena.reservation.repository.ReservationRepository;
import br.dev.guisleri.agendaarena.reservation.repository.SlotBlockRepository;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Isolamento entre estabelecimentos. REGRA DO PROJETO: todo endpoint de gestão novo precisa entrar
 * na lista {@link #managementEndpoints()}, para ser coberto pelos testes de outro estabelecimento
 * (404), organizador (403) e anônimo (401).
 * <p>
 * Datas são calculadas a partir de hoje (relógio real), porque o token usa o relógio do sistema.
 */
@SpringBootTest
@Testcontainers
class EstablishmentAuthorizationIntegrationTests {

    private static final String PASSWORD = "password-123";
    private static final String SLOT_PATH =
            "/establishments/%d/sports-fields/%d/schedule-slots/%d";

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
    private EstablishmentAdminRepository establishmentAdminRepository;
    @Autowired
    private SportFieldRepository sportFieldRepository;
    @Autowired
    private ScheduleSlotRepository scheduleSlotRepository;
    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private SlotBlockRepository slotBlockRepository;
    @Autowired
    private RecurringReservationRepository recurringReservationRepository;

    private MockMvc mockMvc;
    private LocalDate baseDate;
    private Target targetA;
    private Target targetB;
    private User adminA;
    private User adminB;
    private User organizer;
    private User otherOrganizer;
    private User platformAdmin;

    @DynamicPropertySource
    static void configurePostgreSQL(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.execute("TRUNCATE TABLE establishments, users RESTART IDENTITY CASCADE");

        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        // Uma segunda-feira pelo menos 14 dias à frente: o slot é de segunda e as reservas
        // precisam ser futuras.
        baseDate = LocalDate.now(clock)
                .plusDays(14)
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));

        platformAdmin = user("platform@example.com", UserRole.PLATFORM_ADMIN);
        adminA = user("admin.a@example.com", UserRole.ESTABLISHMENT_ADMIN);
        adminB = user("admin.b@example.com", UserRole.ESTABLISHMENT_ADMIN);
        organizer = user("organizer@example.com", UserRole.ORGANIZER);
        otherOrganizer = user("other.organizer@example.com", UserRole.ORGANIZER);

        targetA = createTarget("Arena A", adminA);
        targetB = createTarget("Arena B", adminB);
    }

    // ---------- Administrador do próprio estabelecimento ----------

    @ParameterizedTest(name = "{0}: admin do estabelecimento acessa o próprio")
    @MethodSource("endpointsWithSuccessCase")
    void shouldAllowAdminOnOwnEstablishment(Endpoint endpoint) throws Exception {
        mockMvc.perform(endpoint.request(targetA, baseDate).header(
                        "Authorization", bearer(adminA)))
                .andExpect(status().is(endpoint.successStatus()));
    }

    @ParameterizedTest(name = "{0}: plataforma acessa qualquer estabelecimento")
    @MethodSource("endpointsWithSuccessCase")
    void shouldAllowPlatformAdminOnAnyEstablishment(Endpoint endpoint) throws Exception {
        mockMvc.perform(endpoint.request(targetB, baseDate).header(
                        "Authorization", bearer(platformAdmin)))
                .andExpect(status().is(endpoint.successStatus()));
    }

    // ---------- Isolamento: outro estabelecimento ----------

    @ParameterizedTest(name = "{0}: admin de outro estabelecimento recebe 404 e nada muda")
    @MethodSource("managementEndpoints")
    void shouldHideOtherEstablishmentFromAdmin(Endpoint endpoint) throws Exception {
        long before = totalRecords();

        mockMvc.perform(endpoint.request(targetB, baseDate).header(
                        "Authorization", bearer(adminA)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));

        assertThat(totalRecords()).isEqualTo(before);
        assertThat(reservationRepository.findById(targetB.reservationId()).orElseThrow()
                .getStatus().name()).isEqualTo("CONFIRMED");
        assertRecurringReservationHasNoOrganizer(targetB);
    }

    @ParameterizedTest(name = "{0}: caminho do estabelecimento A com quadra de B recebe 404")
    @MethodSource("endpointsScopedToField")
    void shouldRejectFieldFromAnotherEstablishmentInsideOwnPath(Endpoint endpoint)
            throws Exception {
        Target mixed = new Target(
                targetA.establishmentId(),
                targetB.sportFieldId(),
                targetB.scheduleSlotId(),
                targetB.reservationId(),
                targetB.recurringReservationId(),
                targetB.organizerId()
        );
        long before = totalRecords();

        mockMvc.perform(endpoint.request(mixed, baseDate).header(
                        "Authorization", bearer(adminA)))
                .andExpect(status().isNotFound());

        assertThat(totalRecords()).isEqualTo(before);
    }

    // ---------- Organizador e anônimo ----------

    @ParameterizedTest(name = "{0}: organizador recebe 403")
    @MethodSource("managementEndpoints")
    void shouldForbidOrganizerOnManagementEndpoints(Endpoint endpoint) throws Exception {
        long before = totalRecords();

        mockMvc.perform(endpoint.request(targetA, baseDate).header(
                        "Authorization", bearer(organizer)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));

        assertThat(totalRecords()).isEqualTo(before);
        assertRecurringReservationHasNoOrganizer(targetA);
    }

    @ParameterizedTest(name = "{0}: anônimo recebe 401")
    @MethodSource("managementEndpoints")
    void shouldRequireAuthenticationOnManagementEndpoints(Endpoint endpoint) throws Exception {
        long before = totalRecords();

        mockMvc.perform(endpoint.request(targetA, baseDate))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        assertThat(totalRecords()).isEqualTo(before);
        assertRecurringReservationHasNoOrganizer(targetA);
    }

    // ---------- Rotas só da plataforma ----------

    @Test
    void shouldAllowOnlyPlatformAdminToCreateEstablishments() throws Exception {
        String body = "{\"name\":\"Arena Nova\"}";

        for (User user : new User[]{adminA, organizer}) {
            mockMvc.perform(post("/establishments")
                            .header("Authorization", bearer(user))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isForbidden());
        }

        mockMvc.perform(post("/establishments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/establishments")
                        .header("Authorization", bearer(platformAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void shouldAllowOnlyPlatformAdminToChangeEstablishmentStatus() throws Exception {
        String path = "/establishments/" + targetA.establishmentId() + "/status";
        String body = "{\"active\":false}";

        // Nem o admin do próprio estabelecimento pode desativá-lo.
        mockMvc.perform(patch(path)
                        .header("Authorization", bearer(adminA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        assertThat(establishmentRepository.findById(targetA.establishmentId())
                .orElseThrow().isActive()).isTrue();

        mockMvc.perform(patch(path)
                        .header("Authorization", bearer(platformAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldAllowOnlyPlatformAdminToCreateEstablishmentAdmins() throws Exception {
        String path = "/establishments/" + targetA.establishmentId() + "/admins";
        String body = adminBody("novo.admin@example.com");

        mockMvc.perform(post(path)
                        .header("Authorization", bearer(adminA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(path)
                        .header("Authorization", bearer(organizer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());

        assertThat(establishmentAdminRepository.count()).isEqualTo(2);
    }

    @Test
    void shouldCreateAdminWhoManagesOnlyTheirEstablishment() throws Exception {
        String path = "/establishments/" + targetA.establishmentId() + "/admins";

        mockMvc.perform(post(path)
                        .header("Authorization", bearer(platformAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminBody("novo.admin@example.com")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("novo.admin@example.com"));

        String role = jdbcTemplate.queryForObject(
                "SELECT role FROM users WHERE email = 'novo.admin@example.com'",
                String.class
        );
        assertThat(role).isEqualTo("ESTABLISHMENT_ADMIN");

        String loginResponse = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "novo.admin@example.com", "password": "%s"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String token = "Bearer " + JsonPath.read(loginResponse, "$.accessToken");

        mockMvc.perform(get("/establishments/{e}/sports-fields/{f}/agenda",
                        targetA.establishmentId(), targetA.sportFieldId())
                        .param("date", baseDate.toString())
                        .header("Authorization", token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/establishments/{e}/sports-fields/{f}/agenda",
                        targetB.establishmentId(), targetB.sportFieldId())
                        .param("date", baseDate.toString())
                        .header("Authorization", token))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn404WhenCreatingAdminForMissingEstablishment() throws Exception {
        mockMvc.perform(post("/establishments/999999/admins")
                        .header("Authorization", bearer(platformAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminBody("ninguem@example.com")))
                .andExpect(status().isNotFound());

        Integer users = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM users WHERE email = 'ninguem@example.com'",
                Integer.class
        );
        assertThat(users).isZero();
    }

    // ---------- Painel do administrador ----------

    @Test
    void shouldListOnlyManagedEstablishments() throws Exception {
        mockMvc.perform(get("/me/establishments").header("Authorization", bearer(adminA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(targetA.establishmentId()))
                .andExpect(jsonPath("$[0].name").value("Arena A"));

        mockMvc.perform(get("/me/establishments").header("Authorization", bearer(platformAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/me/establishments").header("Authorization", bearer(organizer)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/me/establishments"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- Catálogo público ----------

    @Test
    void shouldKeepCatalogPublic() throws Exception {
        mockMvc.perform(get("/establishments"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/establishments/{e}", targetA.establishmentId()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/establishments/{e}/sports-fields", targetA.establishmentId()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/establishments/{e}/sports-fields/{f}/schedule-slots",
                        targetA.establishmentId(), targetA.sportFieldId()))
                .andExpect(status().isOk());
    }

    // ---------- Reserva online: organizerId vem do token ----------

    @Test
    void shouldUseOrganizerFromTokenWhenBodyHasNoOrganizerId() throws Exception {
        mockMvc.perform(onlineReservation(targetA, baseDate.plusDays(7), null)
                        .header("Authorization", bearer(organizer)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.organizerId").value(organizer.getId()))
                .andExpect(jsonPath("$.origin").value("ONLINE"));
    }

    @Test
    void shouldAcceptOrganizerIdInBodyWhenItIsTheirOwn() throws Exception {
        mockMvc.perform(onlineReservation(targetA, baseDate.plusDays(7), organizer.getId())
                        .header("Authorization", bearer(organizer)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.organizerId").value(organizer.getId()));
    }

    @Test
    void shouldNotLetOrganizerReserveForSomeoneElse() throws Exception {
        long before = reservationRepository.count();

        mockMvc.perform(onlineReservation(
                                targetA, baseDate.plusDays(7), otherOrganizer.getId())
                        .header("Authorization", bearer(organizer)))
                .andExpect(status().isForbidden());

        assertThat(reservationRepository.count()).isEqualTo(before);
    }

    @Test
    void shouldRequireOrganizerIdWhenAdminReservesOnline() throws Exception {
        mockMvc.perform(onlineReservation(targetA, baseDate.plusDays(7), null)
                        .header("Authorization", bearer(adminA)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.organizerId").isNotEmpty());
    }

    @Test
    void shouldLetAdminReserveOnlineForAUserInOwnEstablishmentOnly() throws Exception {
        mockMvc.perform(onlineReservation(targetA, baseDate.plusDays(7), organizer.getId())
                        .header("Authorization", bearer(adminA)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.organizerId").value(organizer.getId()));

        long before = reservationRepository.count();

        mockMvc.perform(onlineReservation(targetA, baseDate.plusDays(14), organizer.getId())
                        .header("Authorization", bearer(adminB)))
                .andExpect(status().isNotFound());

        assertThat(reservationRepository.count()).isEqualTo(before);
    }

    @Test
    void shouldRequireAuthenticationToReserveOnline() throws Exception {
        mockMvc.perform(onlineReservation(targetA, baseDate.plusDays(7), organizer.getId()))
                .andExpect(status().isUnauthorized());
    }

    // ---------- Montagem dos cenários ----------

    static Stream<Endpoint> managementEndpoints() {
        return Stream.of(
                Endpoint.of("GET agenda", 200, (t, d) ->
                        get("/establishments/{e}/sports-fields/{f}/agenda",
                                t.establishmentId(), t.sportFieldId())
                                .param("date", d.toString())),
                Endpoint.establishmentLevel("POST sports-fields", 201, (t, d) ->
                        post("/establishments/{e}/sports-fields", t.establishmentId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Campo Novo\",\"sportType\":\"SOCIETY\"}")),
                Endpoint.of("POST schedule-slots", 201, (t, d) ->
                        post("/establishments/{e}/sports-fields/{f}/schedule-slots",
                                t.establishmentId(), t.sportFieldId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"dayOfWeek":"TUESDAY","startTime":"20:00:00","price":120.00}
                                        """)),
                Endpoint.of("POST reservations/manual", 201, (t, d) ->
                        post(slotPath(t) + "/reservations/manual")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"reservationDate":"%s","customerName":"Joao",
                                         "customerPhone":"11999991234"}
                                        """.formatted(d.plusDays(21)))),
                Endpoint.of("POST blocks", 201, (t, d) ->
                        post(slotPath(t) + "/blocks")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"blockDate\":\"%s\",\"reason\":\"Manutencao\"}"
                                        .formatted(d.plusDays(14)))),
                Endpoint.of("POST recurring-reservations", 201, (t, d) ->
                        post(slotPath(t) + "/recurring-reservations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"responsibleName":"Turma","responsiblePhone":"11999991234",
                                         "startDate":"%s","endDate":"%s"}
                                        """.formatted(d.plusDays(7), d.plusDays(7)))),
                Endpoint.of("PATCH recurring-reservations link organizer", 200, (t, d) ->
                        patch(slotPath(t) + "/recurring-reservations/{id}",
                                t.recurringReservationId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"organizerId\":%d}".formatted(t.organizerId()))),
                Endpoint.of("GET reservation", 200, (t, d) ->
                        get(slotPath(t) + "/reservations/{id}", t.reservationId())),
                Endpoint.of("POST reservation cancel", 200, (t, d) ->
                        post(slotPath(t) + "/reservations/{id}/cancel", t.reservationId())),
                Endpoint.of("POST reservation refuse-cancellation", 0, (t, d) ->
                        post(slotPath(t) + "/reservations/{id}/refuse-cancellation",
                                t.reservationId())),
                Endpoint.of("POST reservation complete", 0, (t, d) ->
                        post(slotPath(t) + "/reservations/{id}/complete", t.reservationId())),
                Endpoint.of("POST reservation no-show", 0, (t, d) ->
                        post(slotPath(t) + "/reservations/{id}/no-show", t.reservationId()))
        );
    }

    static Stream<Endpoint> endpointsWithSuccessCase() {
        return managementEndpoints().filter(endpoint -> endpoint.successStatus() > 0);
    }

    static Stream<Endpoint> endpointsScopedToField() {
        return managementEndpoints().filter(Endpoint::scopedToField);
    }

    private static String slotPath(Target target) {
        return SLOT_PATH.formatted(
                target.establishmentId(),
                target.sportFieldId(),
                target.scheduleSlotId()
        );
    }

    private MockHttpServletRequestBuilder onlineReservation(
            Target target,
            LocalDate date,
            Long organizerId
    ) {
        String organizerPart = organizerId == null
                ? ""
                : "\"organizerId\": %d,".formatted(organizerId);

        return post(slotPath(target) + "/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{%s \"reservationDate\": \"%s\"}".formatted(organizerPart, date));
    }

    private User user(String email, UserRole role) {
        return userService.createUser("Usuario " + email, email, "11999991234", PASSWORD, role);
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenService.issue(user).accessToken();
    }

    private static String adminBody(String email) {
        return """
                {"name":"Novo Admin","email":"%s","phone":"11999991234","password":"%s"}
                """.formatted(email, PASSWORD);
    }

    private Target createTarget(String name, User admin) throws Exception {
        Establishment establishment = establishmentRepository.save(new Establishment(name));
        establishmentAdminRepository.save(
                new EstablishmentAdmin(admin, establishment, Instant.now(clock))
        );

        SportField sportField = sportFieldRepository.save(
                new SportField(establishment, "Campo 1", SportType.SOCIETY)
        );
        ScheduleSlot slot = scheduleSlotRepository.save(
                new ScheduleSlot(
                        sportField,
                        DayOfWeek.MONDAY,
                        LocalTime.of(19, 0),
                        new BigDecimal("150.00")
                )
        );

        // Uma reserva existente por estabelecimento, criada pela plataforma.
        String response = mockMvc.perform(
                        post(SLOT_PATH.formatted(
                                establishment.getId(), sportField.getId(), slot.getId())
                                + "/reservations/manual")
                                .header("Authorization", bearer(platformAdmin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"reservationDate":"%s","customerName":"Cliente",
                                         "customerPhone":"11999991234"}
                                        """.formatted(baseDate)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Number reservationId = JsonPath.read(response, "$.id");

        // Série sem organizador, longe das datas usadas pelas outras rotas (d, d+7, d+14, d+21).
        RecurringReservation recurring = recurringReservationRepository.save(
                new RecurringReservation(
                        slot,
                        null,
                        "Turma",
                        "11999991234",
                        baseDate.plusDays(35),
                        baseDate.plusDays(35)
                )
        );

        return new Target(
                establishment.getId(),
                sportField.getId(),
                slot.getId(),
                reservationId.longValue(),
                recurring.getId(),
                organizer.getId()
        );
    }

    /**
     * Efeito colateral do PATCH de vínculo: a série de cada alvo nasce sem organizador.
     */
    private void assertRecurringReservationHasNoOrganizer(Target target) {
        Long organizerId = jdbcTemplate.queryForObject(
                "SELECT organizer_id FROM recurring_reservations WHERE id = ?",
                Long.class,
                target.recurringReservationId()
        );

        assertThat(organizerId).isNull();
    }

    private long totalRecords() {
        return sportFieldRepository.count()
                + scheduleSlotRepository.count()
                + reservationRepository.count()
                + slotBlockRepository.count()
                + recurringReservationRepository.count();
    }

    record Target(
            Long establishmentId,
            Long sportFieldId,
            Long scheduleSlotId,
            Long reservationId,
            Long recurringReservationId,
            Long organizerId
    ) {
    }

    @FunctionalInterface
    interface RequestFactory {
        MockHttpServletRequestBuilder build(Target target, LocalDate baseDate);
    }

    /**
     * @param successStatus  status esperado quando o acesso é permitido; 0 = não testar sucesso
     *                       (a regra de negócio da rota exige um estado que o cenário não tem)
     * @param scopedToField  se o caminho tem quadra/horário (para o teste de caminho misturado)
     */
    record Endpoint(
            String name,
            int successStatus,
            boolean scopedToField,
            RequestFactory factory
    ) {

        static Endpoint of(String name, int successStatus, RequestFactory factory) {
            return new Endpoint(name, successStatus, true, factory);
        }

        static Endpoint establishmentLevel(
                String name,
                int successStatus,
                RequestFactory factory
        ) {
            return new Endpoint(name, successStatus, false, factory);
        }

        MockHttpServletRequestBuilder request(Target target, LocalDate baseDate) {
            return factory.build(target, baseDate);
        }

        @Override
        public String toString() {
            return name;
        }
    }

}
