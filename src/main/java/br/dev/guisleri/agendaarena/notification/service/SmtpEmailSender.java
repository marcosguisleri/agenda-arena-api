package br.dev.guisleri.agendaarena.notification.service;

import br.dev.guisleri.agendaarena.common.config.AppMailProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Envio real pelo SMTP configurado em {@code spring.mail.*}. Só existe com
 * {@code app.mail.enabled=true}.
 */
@Component
@ConditionalOnProperty(prefix = "app.mail", name = "enabled", havingValue = "true")
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final AppMailProperties properties;

    public SmtpEmailSender(JavaMailSender mailSender, AppMailProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void send(EmailMessage message) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(properties.from());
        mail.setTo(message.to());
        mail.setSubject(message.subject());
        mail.setText(message.body());

        mailSender.send(mail);
    }
}
