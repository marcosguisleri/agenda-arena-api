package br.dev.guisleri.agendaarena.notification.service;

/**
 * E-mail em texto simples, já pronto para enviar.
 */
public record EmailMessage(String to, String subject, String body) {
}
