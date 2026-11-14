package br.dev.guisleri.agendaarena.notification.service;

import br.dev.guisleri.agendaarena.establishment.repository.EstablishmentAdminRepository;
import br.dev.guisleri.agendaarena.reservation.event.CancellationRefusedEvent;
import br.dev.guisleri.agendaarena.reservation.event.CancellationRequestedEvent;
import br.dev.guisleri.agendaarena.reservation.event.ReservationCancelledEvent;
import br.dev.guisleri.agendaarena.reservation.event.ReservationConfirmedEvent;
import br.dev.guisleri.agendaarena.reservation.event.ReservationNotice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Envia os e-mails de reserva depois do commit e fora da thread da requisição. Reserva que deu
 * rollback (por exemplo, perdeu a corrida pelo horário) nunca dispara e-mail, e uma falha de envio
 * só gera um aviso no log: nunca afeta a reserva, que já foi gravada. Reserva manual não tem
 * organizador com e-mail, então só os administradores são avisados (quando o evento prevê).
 */
@Component
public class ReservationEmailListener {

    private static final Logger log = LoggerFactory.getLogger(ReservationEmailListener.class);

    private final EmailSender emailSender;
    private final ReservationEmailTemplates templates;
    private final EstablishmentAdminRepository establishmentAdminRepository;

    public ReservationEmailListener(
            EmailSender emailSender,
            ReservationEmailTemplates templates,
            EstablishmentAdminRepository establishmentAdminRepository
    ) {
        this.emailSender = emailSender;
        this.templates = templates;
        this.establishmentAdminRepository = establishmentAdminRepository;
    }

    @Async("emailTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onConfirmed(ReservationConfirmedEvent event) {
        ReservationNotice notice = event.notice();
        List<EmailMessage> messages = new ArrayList<>();

        if (hasOrganizerEmail(notice)) {
            messages.add(templates.confirmedForOrganizer(notice));
        }

        for (String adminEmail : adminEmails(notice)) {
            messages.add(templates.newBookingForAdmin(notice, adminEmail));
        }

        deliver(notice, messages);
    }

    @Async("emailTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCancelled(ReservationCancelledEvent event) {
        ReservationNotice notice = event.notice();

        if (hasOrganizerEmail(notice)) {
            deliver(notice, List.of(templates.cancelledForOrganizer(notice)));
        }
    }

    @Async("emailTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCancellationRequested(CancellationRequestedEvent event) {
        ReservationNotice notice = event.notice();
        List<EmailMessage> messages = new ArrayList<>();

        if (hasOrganizerEmail(notice)) {
            messages.add(templates.cancellationRequestedForOrganizer(notice));
        }

        for (String adminEmail : adminEmails(notice)) {
            messages.add(templates.cancellationRequestedForAdmin(notice, adminEmail));
        }

        deliver(notice, messages);
    }

    @Async("emailTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCancellationRefused(CancellationRefusedEvent event) {
        ReservationNotice notice = event.notice();

        if (hasOrganizerEmail(notice)) {
            deliver(notice, List.of(templates.cancellationRefusedForOrganizer(notice)));
        }
    }

    private static boolean hasOrganizerEmail(ReservationNotice notice) {
        return notice.organizerEmail() != null && !notice.organizerEmail().isBlank();
    }

    private List<String> adminEmails(ReservationNotice notice) {
        try {
            return establishmentAdminRepository.findActiveAdminEmails(notice.establishmentId());
        } catch (RuntimeException exception) {
            log.warn(
                    "Could not load the administrators of establishment {} for reservation {}: {}",
                    notice.establishmentId(),
                    notice.reservationId(),
                    exception.getClass().getSimpleName()
            );
            return List.of();
        }
    }

    /**
     * Tenta cada mensagem separadamente: a falha de uma não impede as outras. O log não traz o
     * endereço (o texto da exceção do SMTP costuma repeti-lo): só o tipo do erro.
     */
    private void deliver(ReservationNotice notice, List<EmailMessage> messages) {
        for (EmailMessage message : messages) {
            try {
                emailSender.send(message);
            } catch (RuntimeException exception) {
                log.warn(
                        "Failed to send e-mail \"{}\" for reservation {}: {}",
                        message.subject(),
                        notice.reservationId(),
                        exception.getClass().getSimpleName()
                );
            }
        }
    }
}
