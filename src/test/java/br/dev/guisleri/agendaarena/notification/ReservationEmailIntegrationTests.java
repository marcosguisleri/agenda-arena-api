package br.dev.guisleri.agendaarena.notification;

import br.dev.guisleri.agendaarena.establishment.entity.Establishment;
import br.dev.guisleri.agendaarena.establishment.entity.EstablishmentAdmin;
import br.dev.guisleri.agendaarena.establishment.repository.EstablishmentAdminRepository;
import br.dev.guisleri.agendaarena.establishment.repository.EstablishmentRepository;
import br.dev.guisleri.agendaarena.notification.service.EmailMessage;
import br.dev.guisleri.agendaarena.notification.service.EmailSender;
import br.dev.guisleri.agendaarena.notification.service.LoggingEmailSender;
import br.dev.guisleri.agendaarena.notification.service.SmtpEmailSender;
import br.dev.guisleri.agendaarena.reservation.entity.Reservation;
import br.dev.guisleri.agendaarena.reservation.exception.ReservationNotFoundException;
import br.dev.guisleri.agendaarena.reservation.repository.ReservationRepository;
import br.dev.guisleri.agendaarena.reservation.repository.SlotBlockRepository;
import br.dev.guisleri.agendaarena.reservation.service.ReservationService;
import br.dev.guisleri.agendaarena.schedule.entity.ScheduleSlot;
import br.dev.guisleri.agendaarena.schedule.repository.ScheduleSlotRepository;
import br.dev.guisleri.agendaarena.sportfield.entity.SportField;
import br.dev.guisleri.agendaarena.sportfield.model.SportType;
import br.dev.guisleri.agendaarena.sportfield.repository.SportFieldRepository;
import br.dev.guisleri.agendaarena.user.entity.User;
import br.dev.guisleri.agendaarena.user.model.UserRole;
import br.dev.guisleri.agendaarena.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * E-mails de reserva. O envio roda no mesmo thread ({@code app.mail.async=false} nos testes) e
 * um {@link EmailSender} de captura substitui o SMTP. O {@code MockMvc} autoconfigurado já chama
 * como PLATFORM_ADMIN; os fluxos do organizador (pedido de cancelamento) usam o service direto.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(ReservationEmailIntegrationTests.CaptureConfiguration.class)
class ReservationEmailIntegrationTests {

    private static final String SLOT_PATH =
            "/establishments/{establishmentId}/sports-fields/{sportFieldId}"
                    + "/schedule-slots/{scheduleSlotId}";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ApplicationContext applicationContext;
    @Autowired
    private CapturingEmailSender emailSender;
    @Autowired
    private ReservationService reservationService;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private Clock clock;
    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private SlotBlockRepository slotBlockRepository;
    @Autowired
    private ScheduleSlotRepository scheduleSlotRepository;
    @Autowired
    private SportFieldRepository sportFieldRepository;
    @Autowired
    private EstablishmentRepository establishmentRepository;
    @Autowired
    private EstablishmentAdminRepository establishmentAdminRepository;
    @Autowired
    private UserRepository userRepository;

    private Establishment establishment;
    private SportField sportField;
    private ScheduleSlot scheduleSlot;
    private User organizer;
    private LocalDate monday;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.open-in-view", () -> false);
    }

    @BeforeEach
    void setUp() {
        establishmentAdminRepository.deleteAllInBatch();
        slotBlockRepository.deleteAllInBatch();
        reservationRepository.deleteAllInBatch();
        scheduleSlotRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        sportFieldRepository.deleteAllInBatch();
        establishmentRepository.deleteAllInBatch();
        emailSender.reset();

        monday = LocalDate.now(clock).plusDays(7).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));

        establishment = establishmentRepository.save(new Establishment("Arena Central"));
        Establishment other = establishmentRepository.save(new Establishment("Arena Norte"));
        sportField = sportFieldRepository.save(
                new SportField(establishment, "Campo 1", SportType.SOCIETY));
        scheduleSlot = scheduleSlotRepository.save(
                new ScheduleSlot(sportField, DayOfWeek.MONDAY, LocalTime.of(19, 0),
                        new BigDecimal("150.50")));
        organizer = userRepository.save(
                new User("Maria da Silva", "maria@example.com", "11999991234", "hash"));

        User adminA = admin("admin.a@arena.com", establishment);
        User adminB = admin("admin.b@arena.com", establishment);
        User inactive = admin("inactive@arena.com", establishment);
        admin("other@norte.com", other);
        jdbcTemplate.update("UPDATE users SET active = false WHERE id = ?", inactive.getId());
        assertThat(List.of(adminA, adminB)).hasSize(2);
    }

    @Nested
    @DisplayName("Reserva online")
    class OnlineReservation {

        @Test
        void shouldConfirmToTheOrganizerAndNotifyTheActiveAdminsOfTheEstablishment()
                throws Exception {
            reserveOnline(monday).andExpect(status().isCreated());

            assertThat(emailSender.recipients())
                    .containsExactlyInAnyOrder(
                            "maria@example.com", "admin.a@arena.com", "admin.b@arena.com");

            EmailMessage toOrganizer = emailSender.to("maria@example.com");
            assertThat(toOrganizer.subject()).startsWith("Reserva confirmada: Campo 1, ");
            assertThat(toOrganizer.body())
                    .contains("Sua reserva foi confirmada.")
                    .contains("Estabelecimento: Arena Central")
                    .contains("Horário: 19:00 às 20:00")
                    .contains("Valor: R$ 150,50");

            EmailMessage toAdmin = emailSender.to("admin.a@arena.com");
            assertThat(toAdmin.subject()).startsWith("Novo agendamento online: Campo 1, ");
            assertThat(toAdmin.body())
                    .contains("Cliente: Maria da Silva")
                    .contains("Telefone: 11999991234");
        }

        @Test
        void shouldSendNothingWhenTheReservationIsRejected() throws Exception {
            reserveOnline(monday).andExpect(status().isCreated());
            emailSender.reset();

            reserveOnline(monday).andExpect(status().isConflict());
            reserveOnline(LocalDate.now(clock).minusDays(7).with(
                    TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
                    .andExpect(status().isBadRequest());

            assertThat(emailSender.sent()).isEmpty();
        }

        @Test
        void shouldSendNothingWhenTheTransactionRollsBack() {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                reservationService.createOnlineReservation(
                        establishment.getId(), sportField.getId(), scheduleSlot.getId(),
                        organizer.getId(), monday);
                status.setRollbackOnly();
            });

            assertThat(reservationRepository.count()).isZero();
            assertThat(emailSender.sent()).isEmpty();
        }

        @Test
        void shouldKeepTheReservationWhenEveryEmailFails() throws Exception {
            emailSender.failWhen(message -> true);

            reserveOnline(monday).andExpect(status().isCreated());

            assertThat(reservationRepository.count()).isEqualTo(1);
            assertThat(emailSender.sent()).isEmpty();
        }

        @Test
        void shouldStillSendTheOtherEmailsWhenOneFails() throws Exception {
            emailSender.failWhen(message -> message.to().equals("maria@example.com"));

            reserveOnline(monday).andExpect(status().isCreated());

            assertThat(emailSender.recipients())
                    .containsExactlyInAnyOrder("admin.a@arena.com", "admin.b@arena.com");
        }
    }

    @Test
    @DisplayName("Reserva manual não envia e-mail (o cliente não tem conta)")
    void manualReservationSendsNothing() throws Exception {
        mockMvc.perform(post(SLOT_PATH + "/reservations/manual",
                                establishment.getId(), sportField.getId(), scheduleSlot.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reservationDate\": \"%s\", \"customerName\": \"Joao Souza\"}"
                                .formatted(monday)))
                .andExpect(status().isCreated());

        assertThat(emailSender.sent()).isEmpty();
    }

    @Nested
    @DisplayName("Cancelamento")
    class Cancellation {

        @Test
        void shouldTellTheOrganizerWhenTheAdminCancels() throws Exception {
            long reservationId = reserveOnlineAndGetId(monday);
            emailSender.reset();

            cancel(reservationId, false).andExpect(status().isOk());

            assertThat(emailSender.recipients()).containsExactly("maria@example.com");
            assertThat(emailSender.to("maria@example.com").subject())
                    .startsWith("Reserva cancelada: Campo 1, ");
        }

        @Test
        void shouldAlsoTellTheOrganizerWhenTheSlotIsKeptBlocked() throws Exception {
            long reservationId = reserveOnlineAndGetId(monday);
            emailSender.reset();

            cancel(reservationId, true).andExpect(status().isOk());

            assertThat(emailSender.recipients()).containsExactly("maria@example.com");
        }

        @Test
        void shouldSendNothingWhenCancellingAManualReservation() throws Exception {
            Reservation manual = reservationRepository.saveAndFlush(
                    Reservation.manual(scheduleSlot, monday, "Joao Souza", "11988887777"));

            cancel(manual.getId(), false).andExpect(status().isOk());

            assertThat(emailSender.sent()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Pedido de cancelamento")
    class CancellationRequest {

        @Test
        void shouldAcknowledgeToTheOrganizerAndAlertTheAdmins() throws Exception {
            long reservationId = reserveOnlineAndGetId(monday);
            emailSender.reset();

            reservationService.requestCancellation(organizer.getId(), reservationId);

            assertThat(emailSender.recipients())
                    .containsExactlyInAnyOrder(
                            "maria@example.com", "admin.a@arena.com", "admin.b@arena.com");
            assertThat(emailSender.to("maria@example.com").subject())
                    .startsWith("Pedido de cancelamento recebido: Campo 1, ");
            EmailMessage toAdmin = emailSender.to("admin.b@arena.com");
            assertThat(toAdmin.subject()).startsWith("Pedido de cancelamento: Campo 1, ");
            assertThat(toAdmin.body()).contains("Telefone: 11999991234");
        }

        @Test
        void shouldTellTheOrganizerOnlyWhenTheRequestIsRefused() throws Exception {
            long reservationId = reserveOnlineAndGetId(monday);
            reservationService.requestCancellation(organizer.getId(), reservationId);
            emailSender.reset();

            mockMvc.perform(post(SLOT_PATH + "/reservations/{id}/refuse-cancellation",
                            establishment.getId(), sportField.getId(), scheduleSlot.getId(),
                            reservationId))
                    .andExpect(status().isOk());

            assertThat(emailSender.recipients()).containsExactly("maria@example.com");
            assertThat(emailSender.to("maria@example.com").subject())
                    .startsWith("Pedido de cancelamento recusado: Campo 1, ");
        }

        @Test
        void shouldTellTheOrganizerWhenTheAdminApprovesByCancelling() throws Exception {
            long reservationId = reserveOnlineAndGetId(monday);
            reservationService.requestCancellation(organizer.getId(), reservationId);
            emailSender.reset();

            cancel(reservationId, false).andExpect(status().isOk());

            assertThat(emailSender.recipients()).containsExactly("maria@example.com");
            assertThat(emailSender.to("maria@example.com").subject())
                    .startsWith("Reserva cancelada: Campo 1, ");
        }

        @Test
        void shouldSendNothingWhenTheRequestIsRejected() {
            long reservationId = reservationRepository.saveAndFlush(
                    Reservation.manual(scheduleSlot, monday, "Joao Souza", "11988887777")).getId();

            // Reserva manual não pertence a nenhum organizador: o pedido é recusado como 404.
            assertThatThrownBy(() ->
                    reservationService.requestCancellation(organizer.getId(), reservationId))
                    .isInstanceOf(ReservationNotFoundException.class);

            assertThat(emailSender.sent()).isEmpty();
        }
    }

    @Test
    @DisplayName("Com o envio desligado (padrão), o SMTP nem existe e o envio só registra no log")
    void disabledByDefaultUsesTheLoggingSender() {
        assertThat(applicationContext.getBeansOfType(SmtpEmailSender.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(LoggingEmailSender.class)).hasSize(1);

        new LoggingEmailSender()
                .send(new EmailMessage("maria@example.com", "Assunto", "Corpo"));
    }

    // ---------- Apoio ----------

    private User admin(String email, Establishment target) {
        User user = userRepository.save(
                new User("Admin " + email, email, "11988880000", "hash",
                        UserRole.ESTABLISHMENT_ADMIN));
        establishmentAdminRepository.save(new EstablishmentAdmin(user, target, Instant.now()));

        return user;
    }

    private ResultActions reserveOnline(LocalDate date) throws Exception {
        return mockMvc.perform(post(SLOT_PATH + "/reservations",
                        establishment.getId(), sportField.getId(), scheduleSlot.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"organizerId\": %d, \"reservationDate\": \"%s\"}"
                        .formatted(organizer.getId(), date)));
    }

    private long reserveOnlineAndGetId(LocalDate date) throws Exception {
        String response = reserveOnline(date)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(response, "$.id");

        return id.longValue();
    }

    private ResultActions cancel(long reservationId, boolean keepBlocked) throws Exception {
        return mockMvc.perform(post(SLOT_PATH + "/reservations/{id}/cancel",
                        establishment.getId(), sportField.getId(), scheduleSlot.getId(),
                        reservationId)
                .param("keepBlocked", String.valueOf(keepBlocked)));
    }

    // ---------- Captura ----------

    @TestConfiguration(proxyBeanMethods = false)
    static class CaptureConfiguration {

        @Bean
        @Primary
        CapturingEmailSender capturingEmailSender() {
            return new CapturingEmailSender();
        }
    }

    static class CapturingEmailSender implements EmailSender {

        private final List<EmailMessage> sent = new CopyOnWriteArrayList<>();
        private volatile Predicate<EmailMessage> failWhen = message -> false;

        @Override
        public void send(EmailMessage message) {
            if (failWhen.test(message)) {
                throw new MailSendException("smtp failure for " + message.to());
            }

            sent.add(message);
        }

        void failWhen(Predicate<EmailMessage> predicate) {
            this.failWhen = predicate;
        }

        void reset() {
            sent.clear();
            failWhen = message -> false;
        }

        List<EmailMessage> sent() {
            return List.copyOf(sent);
        }

        List<String> recipients() {
            return sent.stream().map(EmailMessage::to).toList();
        }

        EmailMessage to(String recipient) {
            return sent.stream()
                    .filter(message -> message.to().equals(recipient))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No e-mail sent to " + recipient));
        }
    }
}
