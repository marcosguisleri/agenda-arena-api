package br.dev.guisleri.agendaarena.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * E-mails transacionais, lidos de {@code app.mail.*}. O servidor SMTP em si é configurado pelas
 * propriedades padrão do Spring ({@code spring.mail.*}, alimentadas por {@code MAIL_*}).
 *
 * @param enabled envia de verdade pelo SMTP; desligado, só registra no log que não enviou
 * @param from    remetente dos e-mails
 * @param async   envia em outra thread (padrão); os testes desligam para ficar determinístico
 */
@ConfigurationProperties(prefix = "app.mail")
public record AppMailProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("no-reply@agendaarena.local") String from,
        @DefaultValue("true") boolean async
) {
}
