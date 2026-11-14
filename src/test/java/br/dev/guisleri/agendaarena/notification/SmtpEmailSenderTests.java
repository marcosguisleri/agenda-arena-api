package br.dev.guisleri.agendaarena.notification;

import br.dev.guisleri.agendaarena.common.config.AppMailProperties;
import br.dev.guisleri.agendaarena.notification.service.EmailMessage;
import br.dev.guisleri.agendaarena.notification.service.SmtpEmailSender;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SmtpEmailSenderTests {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final SmtpEmailSender sender = new SmtpEmailSender(
            mailSender,
            new AppMailProperties(true, "no-reply@arena.com", true)
    );

    @Test
    void shouldSendPlainTextWithTheConfiguredSender() {
        sender.send(new EmailMessage("maria@example.com", "Assunto", "Corpo do e-mail"));

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage sent = captor.getValue();
        assertThat(sent.getFrom()).isEqualTo("no-reply@arena.com");
        assertThat(sent.getTo()).containsExactly("maria@example.com");
        assertThat(sent.getSubject()).isEqualTo("Assunto");
        assertThat(sent.getText()).isEqualTo("Corpo do e-mail");
    }

    @Test
    void shouldPropagateSmtpFailuresToTheCaller() {
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() ->
                sender.send(new EmailMessage("maria@example.com", "Assunto", "Corpo")))
                .isInstanceOf(MailSendException.class);
    }
}
