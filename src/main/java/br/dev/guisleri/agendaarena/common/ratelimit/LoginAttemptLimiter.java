package br.dev.guisleri.agendaarena.common.ratelimit;

import br.dev.guisleri.agendaarena.common.config.AppRateLimitProperties;
import br.dev.guisleri.agendaarena.common.exception.TooManyRequestsException;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Limite de tentativas de login por conta. A chave é IP + e-mail (e não só o e-mail): quem não
 * está no mesmo IP não consegue bloquear o login de outra pessoa. O limite por IP puro é aplicado
 * antes, no filtro.
 */
@Component
public class LoginAttemptLimiter {

    private final TokenBucketRateLimiter rateLimiter;
    private final AppRateLimitProperties properties;

    public LoginAttemptLimiter(
            TokenBucketRateLimiter rateLimiter,
            AppRateLimitProperties properties
    ) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
    }

    public void check(String clientIp, String email) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);

        RateLimitDecision decision = rateLimiter.tryConsume(
                "login-account:" + clientIp + "|" + normalizedEmail,
                properties.loginAccount()
        );

        if (!decision.allowed()) {
            throw new TooManyRequestsException(decision.retryAfterSeconds());
        }
    }
}
