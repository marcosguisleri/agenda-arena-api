package br.dev.guisleri.agendaarena.notification;

import br.dev.guisleri.agendaarena.notification.service.EmailMessage;
import br.dev.guisleri.agendaarena.notification.service.FrontLinks;
import br.dev.guisleri.agendaarena.notification.service.ReservationEmailTemplates;
import br.dev.guisleri.agendaarena.reservation.event.ReservationNotice;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationEmailTemplatesTests {

    private static final String FRONT = "https://app.agendaarena.test";

    private final ReservationEmailTemplates templates =
            new ReservationEmailTemplates(new FrontLinks(FRONT + "/"));

    private static ReservationNotice notice() {
        return new ReservationNotice(
                10L,
                1L,
                "Arena Central",
                "Campo 1",
                LocalDate.of(2026, 10, 26),
                LocalTime.of(19, 0),
                LocalTime.of(20, 0),
                new BigDecimal("1150.50"),
                "Maria da Silva",
                "maria@example.com",
                "11999991234"
        );
    }

    @Test
    @DisplayName("Confirmação ao organizador: assunto curto e detalhes formatados em português")
    void confirmedForOrganizer() {
        EmailMessage message = templates.confirmedForOrganizer(notice());

        assertThat(message.to()).isEqualTo("maria@example.com");
        assertThat(message.subject()).isEqualTo("Reserva confirmada: Campo 1, 26/10 às 19:00");
        assertThat(message.body())
                .startsWith("Olá, Maria da Silva!")
                .contains("Sua reserva foi confirmada.")
                .contains("Estabelecimento: Arena Central")
                .contains("Campo: Campo 1")
                .contains("Data: segunda-feira, 26/10/2026")
                .contains("Horário: 19:00 às 20:00")
                .contains("Valor: R$ 1.150,50")
                .contains("O estabelecimento confirma o cancelamento.")
                .endsWith("Agenda Arena");
    }

    @Test
    @DisplayName("Cancelamento, pedido recebido e pedido recusado ao organizador")
    void otherOrganizerEmails() {
        EmailMessage cancelled = templates.cancelledForOrganizer(notice());
        EmailMessage requested = templates.cancellationRequestedForOrganizer(notice());
        EmailMessage refused = templates.cancellationRefusedForOrganizer(notice());

        assertThat(cancelled.subject())
                .isEqualTo("Reserva cancelada: Campo 1, 26/10 às 19:00");
        assertThat(cancelled.body()).contains("cancelada pelo estabelecimento");

        assertThat(requested.subject())
                .isEqualTo("Pedido de cancelamento recebido: Campo 1, 26/10 às 19:00");
        assertThat(requested.body()).contains("continua valendo até a decisão");

        assertThat(refused.subject())
                .isEqualTo("Pedido de cancelamento recusado: Campo 1, 26/10 às 19:00");
        assertThat(refused.body()).contains("A reserva continua confirmada.");

        for (EmailMessage message : new EmailMessage[]{cancelled, requested, refused}) {
            assertThat(message.to()).isEqualTo("maria@example.com");
            assertThat(message.body()).contains("Data: segunda-feira, 26/10/2026");
        }
    }

    @Test
    @DisplayName("Avisos ao admin trazem o contato do cliente")
    void adminEmails() {
        EmailMessage booking = templates.newBookingForAdmin(notice(), "admin@arena.com");
        EmailMessage request = templates.cancellationRequestedForAdmin(notice(), "admin@arena.com");

        assertThat(booking.to()).isEqualTo("admin@arena.com");
        assertThat(booking.subject()).isEqualTo("Novo agendamento online: Campo 1, 26/10 às 19:00");
        assertThat(booking.body())
                .contains("Entrou uma reserva online no Arena Central.")
                .contains("Cliente: Maria da Silva")
                .contains("Telefone: 11999991234")
                .contains("confirmada automaticamente");

        assertThat(request.subject())
                .isEqualTo("Pedido de cancelamento: Campo 1, 26/10 às 19:00");
        assertThat(request.body())
                .contains("Cliente: Maria da Silva")
                .contains("Telefone: 11999991234")
                .contains("continua valendo até você decidir");
    }

    @Test
    @DisplayName("O assunto nunca tem quebra de linha, mesmo com nome de campo malicioso")
    void subjectIsAlwaysASingleLine() {
        ReservationNotice malicious = new ReservationNotice(
                10L, 1L, "Arena", "Campo 1\r\nBcc: alguem@example.com",
                LocalDate.of(2026, 10, 26), LocalTime.of(19, 0), LocalTime.of(20, 0),
                new BigDecimal("150.00"), "Maria", "maria@example.com", "11999991234");

        EmailMessage message = templates.confirmedForOrganizer(malicious);

        assertThat(message.subject())
                .doesNotContain("\r")
                .doesNotContain("\n")
                .isEqualTo("Reserva confirmada: Campo 1 Bcc: alguem@example.com, 26/10 às 19:00");
    }

    @Test
    @DisplayName("Todo e-mail do organizador leva o link da reserva no front")
    void organizerEmailsLinkToTheReservation() {
        String link = "Ver a reserva: " + FRONT + "/me/reservations/10\n";

        assertThat(templates.confirmedForOrganizer(notice()).body()).contains(link);
        assertThat(templates.cancelledForOrganizer(notice()).body()).contains(link);
        assertThat(templates.cancellationRequestedForOrganizer(notice()).body()).contains(link);
        assertThat(templates.cancellationRefusedForOrganizer(notice()).body()).contains(link);
    }

    @Test
    @DisplayName("Todo aviso ao administrador leva o link da reserva no painel")
    void adminEmailsLinkToTheReservation() {
        String link = "Ver a reserva: " + FRONT + "/admin/reservations/10\n";

        assertThat(templates.newBookingForAdmin(notice(), "admin@example.com").body())
                .contains(link);
        assertThat(templates.cancellationRequestedForAdmin(notice(), "admin@example.com").body())
                .contains(link);
    }

    @Test
    @DisplayName("O link não vai no assunto (que só tem campo, data e horário)")
    void linkStaysOutOfTheSubject() {
        assertThat(templates.confirmedForOrganizer(notice()).subject()).doesNotContain("http");
    }
}
