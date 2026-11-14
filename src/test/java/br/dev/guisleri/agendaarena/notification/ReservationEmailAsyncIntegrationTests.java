package br.dev.guisleri.agendaarena.notification;

import br.dev.guisleri.agendaarena.establishment.entity.Establishment;
import br.dev.guisleri.agendaarena.establishment.repository.EstablishmentRepository;
import br.dev.guisleri.agendaarena.notification.service.EmailMessage;
import br.dev.guisleri.agendaarena.notification.service.EmailSender;
import br.dev.guisleri.agendaarena.reservation.repository.ReservationRepository;
import br.dev.guisleri.agendaarena.reservation.service.ReservationService;
import br.dev.guisleri.agendaarena.schedule.entity.ScheduleSlot;
import br.dev.guisleri.agendaarena.schedule.repository.ScheduleSlotRepository;
import br.dev.guisleri.agendaarena.sportfield.entity.SportField;
import br.dev.guisleri.agendaarena.sportfield.model.SportType;
import br.dev.guisleri.agendaarena.sportfield.repository.SportFieldRepository;
import br.dev.guisleri.agendaarena.user.entity.User;
import br.dev.guisleri.agendaarena.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Caminho assíncrono de verdade ({@code app.mail.async=true}): o e-mail sai por outra thread, a
 * do executor de e-mails, e não pela thread que gravou a reserva.
 */
@SpringBootTest(properties = "app.mail.async=true")
@Testcontainers
@Import(ReservationEmailAsyncIntegrationTests.CaptureConfiguration.class)
class ReservationEmailAsyncIntegrationTests {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private ThreadCapturingEmailSender emailSender;
    @Autowired
    private ReservationService reservationService;
    @Autowired
    private Clock clock;
    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private ScheduleSlotRepository scheduleSlotRepository;
    @Autowired
    private SportFieldRepository sportFieldRepository;
    @Autowired
    private EstablishmentRepository establishmentRepository;
    @Autowired
    private UserRepository userRepository;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void cleanUp() {
        reservationRepository.deleteAllInBatch();
        scheduleSlotRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        sportFieldRepository.deleteAllInBatch();
        establishmentRepository.deleteAllInBatch();
        emailSender.threads.clear();
    }

    @Test
    void shouldSendFromTheEmailExecutorThreadAfterTheReservationIsSaved() {
        Establishment establishment = establishmentRepository.save(new Establishment("Arena"));
        SportField sportField = sportFieldRepository.save(
                new SportField(establishment, "Campo 1", SportType.SOCIETY));
        ScheduleSlot slot = scheduleSlotRepository.save(
                new ScheduleSlot(sportField, DayOfWeek.MONDAY, LocalTime.of(19, 0),
                        new BigDecimal("150.00")));
        User organizer = userRepository.save(
                new User("Maria da Silva", "maria@example.com", "11999991234", "hash"));
        LocalDate monday = LocalDate.now(clock).plusDays(7)
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        String callerThread = Thread.currentThread().getName();

        reservationService.createOnlineReservation(
                establishment.getId(), sportField.getId(), slot.getId(), organizer.getId(), monday);

        assertThat(reservationRepository.count()).isEqualTo(1);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(emailSender.threads).hasSize(1));
        assertThat(emailSender.threads.get(0))
                .startsWith("email-")
                .isNotEqualTo(callerThread);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class CaptureConfiguration {

        @Bean
        @Primary
        ThreadCapturingEmailSender threadCapturingEmailSender() {
            return new ThreadCapturingEmailSender();
        }
    }

    static class ThreadCapturingEmailSender implements EmailSender {

        private final List<String> threads = new CopyOnWriteArrayList<>();

        @Override
        public void send(EmailMessage message) {
            threads.add(Thread.currentThread().getName());
        }
    }
}
