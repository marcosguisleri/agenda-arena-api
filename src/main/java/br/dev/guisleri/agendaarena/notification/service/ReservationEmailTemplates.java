package br.dev.guisleri.agendaarena.notification.service;

import br.dev.guisleri.agendaarena.reservation.event.ReservationNotice;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Textos dos e-mails de reserva (texto simples, em português, curtos). Nomes vindos do usuário
 * entram só no corpo; o assunto usa apenas campo, data e horário, sem quebras de linha.
 */
@Component
public class ReservationEmailTemplates {

    private static final Locale PT_BR = Locale.of("pt", "BR");
    private static final DateTimeFormatter LONG_DATE =
            DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy", PT_BR);
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    // ---------- Organizador ----------

    public EmailMessage confirmedForOrganizer(ReservationNotice notice) {
        return new EmailMessage(
                notice.organizerEmail(),
                subject("Reserva confirmada", notice),
                "Olá, " + notice.organizerName() + "!\n\n"
                        + "Sua reserva foi confirmada.\n\n"
                        + details(notice) + "\n"
                        + "Para cancelar, faça o pedido pelo Agenda Arena antes do horário. "
                        + "O estabelecimento confirma o cancelamento.\n\n"
                        + signature()
        );
    }

    public EmailMessage cancelledForOrganizer(ReservationNotice notice) {
        return new EmailMessage(
                notice.organizerEmail(),
                subject("Reserva cancelada", notice),
                "Olá, " + notice.organizerName() + ".\n\n"
                        + "Sua reserva foi cancelada pelo estabelecimento.\n\n"
                        + details(notice) + "\n"
                        + "Em caso de dúvida, fale diretamente com o estabelecimento.\n\n"
                        + signature()
        );
    }

    public EmailMessage cancellationRequestedForOrganizer(ReservationNotice notice) {
        return new EmailMessage(
                notice.organizerEmail(),
                subject("Pedido de cancelamento recebido", notice),
                "Olá, " + notice.organizerName() + ".\n\n"
                        + "Recebemos seu pedido de cancelamento. O estabelecimento vai analisar "
                        + "e a reserva continua valendo até a decisão.\n\n"
                        + details(notice) + "\n"
                        + signature()
        );
    }

    public EmailMessage cancellationRefusedForOrganizer(ReservationNotice notice) {
        return new EmailMessage(
                notice.organizerEmail(),
                subject("Pedido de cancelamento recusado", notice),
                "Olá, " + notice.organizerName() + ".\n\n"
                        + "O estabelecimento recusou seu pedido de cancelamento. "
                        + "A reserva continua confirmada.\n\n"
                        + details(notice) + "\n"
                        + "Em caso de dúvida, fale diretamente com o estabelecimento.\n\n"
                        + signature()
        );
    }

    // ---------- Administrador do estabelecimento ----------

    public EmailMessage newBookingForAdmin(ReservationNotice notice, String adminEmail) {
        return new EmailMessage(
                adminEmail,
                subject("Novo agendamento online", notice),
                "Entrou uma reserva online no " + notice.establishmentName() + ".\n\n"
                        + customer(notice)
                        + details(notice) + "\n"
                        + "A reserva já está confirmada automaticamente.\n\n"
                        + signature()
        );
    }

    public EmailMessage cancellationRequestedForAdmin(
            ReservationNotice notice,
            String adminEmail
    ) {
        return new EmailMessage(
                adminEmail,
                subject("Pedido de cancelamento", notice),
                "Um cliente pediu o cancelamento de uma reserva no "
                        + notice.establishmentName() + ".\n\n"
                        + customer(notice)
                        + details(notice) + "\n"
                        + "A reserva continua valendo até você decidir. Aprove cancelando a "
                        + "reserva ou recuse o pedido pelo painel.\n\n"
                        + signature()
        );
    }

    // ---------- Partes comuns ----------

    private static String subject(String title, ReservationNotice notice) {
        return singleLine(
                title + ": " + notice.sportFieldName() + ", "
                        + notice.reservationDate().format(SHORT_DATE) + " às "
                        + notice.startTime().format(TIME)
        );
    }

    private static String customer(ReservationNotice notice) {
        return "Cliente: " + notice.organizerName() + "\n"
                + "Telefone: " + notice.organizerPhone() + "\n";
    }

    private static String details(ReservationNotice notice) {
        return "Estabelecimento: " + notice.establishmentName() + "\n"
                + "Campo: " + notice.sportFieldName() + "\n"
                + "Data: " + notice.reservationDate().format(LONG_DATE) + "\n"
                + "Horário: " + notice.startTime().format(TIME)
                + " às " + notice.endTime().format(TIME) + "\n"
                + "Valor: " + String.format(PT_BR, "R$ %,.2f", notice.price()) + "\n";
    }

    private static String signature() {
        return "Agenda Arena";
    }

    private static String singleLine(String text) {
        return text.replaceAll("[\\r\\n]+", " ").trim();
    }
}
