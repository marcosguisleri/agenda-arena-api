package br.dev.guisleri.agendaarena.common.exception;

/**
 * Limite de requisições atingido. Vira 429 com o cabeçalho {@code Retry-After}.
 */
public class TooManyRequestsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(long retryAfterSeconds) {
        super("Too many requests. Try again in " + retryAfterSeconds + " seconds");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
