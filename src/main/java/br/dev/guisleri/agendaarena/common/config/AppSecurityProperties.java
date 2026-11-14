package br.dev.guisleri.agendaarena.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuração de segurança lida de {@code app.security.*} (valores vêm do .env em desenvolvimento).
 */
@ConfigurationProperties(prefix = "app.security")
public record AppSecurityProperties(
        Jwt jwt,
        @DefaultValue Refresh refresh,
        PlatformAdmin platformAdmin
) {

    public record Jwt(
            String secret,
            @DefaultValue("15") long expirationMinutes
    ) {
    }

    /**
     * Refresh token: a sessão vale {@code expirationDays} a partir do último uso (janela deslizante).
     */
    public record Refresh(
            @DefaultValue("30") long expirationDays
    ) {
    }

    /**
     * Administrador da plataforma criado na subida da aplicação. Se e-mail ou senha estiverem
     * vazios, nada é criado.
     */
    public record PlatformAdmin(
            String name,
            String email,
            String phone,
            String password
    ) {
    }
}
