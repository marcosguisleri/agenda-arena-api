package br.dev.guisleri.agendaarena.common.ratelimit;

/**
 * Resultado de uma tentativa de consumir uma ficha. {@code retryAfterSeconds} só vale quando a
 * requisição foi negada (tempo até a próxima ficha, no mínimo 1).
 */
public record RateLimitDecision(boolean allowed, long retryAfterSeconds) {

    public static final RateLimitDecision ALLOWED = new RateLimitDecision(true, 0);

    public static RateLimitDecision denied(long retryAfterSeconds) {
        return new RateLimitDecision(false, Math.max(1, retryAfterSeconds));
    }
}
