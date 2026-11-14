package br.dev.guisleri.agendaarena.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Padrão quando o envio está desligado ({@code app.mail.enabled=false}): não envia nada e não
 * registra o endereço nem o texto, só o assunto.
 */
@Component
@ConditionalOnProperty(
        prefix = "app.mail",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = true
)
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(EmailMessage message) {
        log.info("E-mail sending is disabled; not sent: \"{}\"", message.subject());
    }
}
