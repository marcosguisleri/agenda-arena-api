package br.dev.guisleri.agendaarena.notification.service;

/**
 * Envio de e-mail. Falhas viram exceção; quem chama decide o que fazer (os avisos de reserva só
 * registram no log, sem afetar a reserva).
 */
public interface EmailSender {

    void send(EmailMessage message);
}
