package br.dev.guisleri.agendaarena.common.ratelimit;

import br.dev.guisleri.agendaarena.common.config.AppRateLimitProperties.Rule;
import br.dev.guisleri.agendaarena.support.MutableClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenBucketRateLimiterTests {

    private static final Rule FIVE_PER_FIVE_MINUTES = new Rule(5, 300);

    private final MutableClock clock =
            new MutableClock(Instant.parse("2026-10-19T12:00:00Z"), ZoneId.of("UTC"));
    private final TokenBucketRateLimiter limiter =
            new TokenBucketRateLimiter(clock, true, 1000);

    private RateLimitDecision consume(String key) {
        return limiter.tryConsume(key, FIVE_PER_FIVE_MINUTES);
    }

    @Test
    @DisplayName("Permite até o limite de uma vez e depois nega com o tempo até a próxima ficha")
    void allowsTheBurstThenDenies() {
        for (int i = 0; i < 5; i++) {
            assertThat(consume("k").allowed()).isTrue();
        }

        RateLimitDecision denied = consume("k");

        assertThat(denied.allowed()).isFalse();
        // 5 por 300 s = 1 ficha a cada 60 s.
        assertThat(denied.retryAfterSeconds()).isEqualTo(60);
    }

    @Test
    @DisplayName("As fichas voltam aos poucos com o tempo")
    void refillsGradually() {
        for (int i = 0; i < 5; i++) {
            consume("k");
        }

        clock.advance(Duration.ofSeconds(59));
        RateLimitDecision stillDenied = consume("k");
        assertThat(stillDenied.allowed()).isFalse();
        assertThat(stillDenied.retryAfterSeconds()).isEqualTo(1);

        clock.advance(Duration.ofSeconds(1));
        assertThat(consume("k").allowed()).isTrue();
        assertThat(consume("k").allowed()).isFalse();
    }

    @Test
    @DisplayName("A conta é exata: a ficha volta no instante anunciado, mesmo em várias esperas parciais")
    void refillIsExactAcrossPartialWaits() {
        Rule rule = new Rule(10, 60);

        for (int i = 0; i < 10; i++) {
            assertThat(limiter.tryConsume("k", rule).allowed()).isTrue();
        }

        RateLimitDecision denied = limiter.tryConsume("k", rule);
        assertThat(denied.retryAfterSeconds()).isEqualTo(6);

        clock.advance(Duration.ofSeconds(5));
        RateLimitDecision stillDenied = limiter.tryConsume("k", rule);
        assertThat(stillDenied.allowed()).isFalse();
        assertThat(stillDenied.retryAfterSeconds()).isEqualTo(1);

        clock.advance(Duration.ofSeconds(1));
        assertThat(limiter.tryConsume("k", rule).allowed()).isTrue();
        assertThat(limiter.tryConsume("k", rule).allowed()).isFalse();
    }

    @Test
    @DisplayName("A recarga nunca passa do limite, mesmo depois de muito tempo parado")
    void refillIsCappedAtTheLimit() {
        consume("k");
        clock.advance(Duration.ofDays(30));

        for (int i = 0; i < 5; i++) {
            assertThat(consume("k").allowed()).isTrue();
        }

        assertThat(consume("k").allowed()).isFalse();
    }

    @Test
    @DisplayName("Chaves diferentes têm baldes independentes")
    void keysAreIndependent() {
        for (int i = 0; i < 5; i++) {
            consume("a");
        }

        assertThat(consume("a").allowed()).isFalse();
        assertThat(consume("b").allowed()).isTrue();
    }

    @Test
    @DisplayName("Relógio que volta no tempo não quebra nem dá fichas de graça")
    void survivesAClockGoingBackwards() {
        for (int i = 0; i < 5; i++) {
            consume("k");
        }

        clock.advance(Duration.ofHours(-1));

        assertThat(consume("k").allowed()).isFalse();
    }

    @Test
    @DisplayName("Desligado, sempre permite e não guarda nada")
    void disabledAlwaysAllows() {
        TokenBucketRateLimiter disabled = new TokenBucketRateLimiter(clock, false, 1000);

        for (int i = 0; i < 100; i++) {
            assertThat(disabled.tryConsume("k", new Rule(1, 60)).allowed()).isTrue();
        }

        assertThat(disabled.size()).isZero();
    }

    @Test
    @DisplayName("O mapa é limitado: a chave menos usada sai e recomeça cheia")
    void boundsTheNumberOfKeys() {
        TokenBucketRateLimiter small = new TokenBucketRateLimiter(clock, true, 3);

        for (int i = 0; i < 5; i++) {
            small.tryConsume("exhausted", FIVE_PER_FIVE_MINUTES);
        }
        assertThat(small.tryConsume("exhausted", FIVE_PER_FIVE_MINUTES).allowed()).isFalse();

        small.tryConsume("b", FIVE_PER_FIVE_MINUTES);
        small.tryConsume("c", FIVE_PER_FIVE_MINUTES);
        small.tryConsume("d", FIVE_PER_FIVE_MINUTES);

        assertThat(small.size()).isEqualTo(3);
        // "exhausted" foi expulsa por ser a menos recente: o balde dela recomeça cheio.
        assertThat(small.tryConsume("exhausted", FIVE_PER_FIVE_MINUTES).allowed()).isTrue();
    }

    @Test
    @DisplayName("Regra inválida é recusada na configuração")
    void rejectsInvalidRules() {
        assertThatThrownBy(() -> new Rule(0, 60)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Rule(5, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
