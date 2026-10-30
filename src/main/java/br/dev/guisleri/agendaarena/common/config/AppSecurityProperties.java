package br.dev.guisleri.agendaarena.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuração de segurança lida de {@code app.security.*} (valores vêm do .env em desenvolvimento).
 */
@ConfigurationProperties(prefix = "app.security")
public record AppSecurityProperties(
        Jwt jwt,
        PlatformAdmin platformAdmin
) {

    public record Jwt(
            String secret,
            @DefaultValue("60") long expirationMinutes
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
