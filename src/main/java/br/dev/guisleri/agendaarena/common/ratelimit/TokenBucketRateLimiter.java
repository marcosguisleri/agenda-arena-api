package br.dev.guisleri.agendaarena.common.ratelimit;

import br.dev.guisleri.agendaarena.common.config.AppRateLimitProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Limitador por balde de fichas, em memória. Cada chave (regra + IP, link ou conta) tem um balde
 * com {@code limit} fichas que se repõe a {@code limit / windowSeconds} por segundo; cada
 * requisição gasta uma. O tempo vem do {@link Clock} (os testes o controlam).
 * <p>
 * Os baldes ficam só nesta instância (suficiente para uma instância; com várias, o limite efetivo
 * multiplica). O mapa é limitado: passou de {@value #MAX_KEYS} chaves, sai a menos usada
 * recentemente (o balde dela recomeça cheio, o que é seguro para o usuário).
 */
@Component
public class TokenBucketRateLimiter {

    static final int MAX_KEYS = 100_000;

    private final Clock clock;
    private final boolean enabled;
    private final Object lock = new Object();
    private final Map<String, Bucket> buckets;

    @Autowired
    public TokenBucketRateLimiter(Clock clock, AppRateLimitProperties properties) {
        this(clock, properties.enabled(), MAX_KEYS);
    }

    TokenBucketRateLimiter(Clock clock, boolean enabled, int maxKeys) {
        this.clock = clock;
        this.enabled = enabled;
        this.buckets = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Bucket> eldest) {
                return size() > maxKeys;
            }
        };
    }

    /**
     * Tenta gastar uma ficha do balde da chave. Com a limitação desligada, sempre permite.
     * <p>
     * A conta é em inteiros para ser exata: uma ficha vale {@code windowMillis} unidades de
     * crédito, e cada milissegundo repõe {@code limit} unidades (limit fichas por janela). Assim a
     * ficha volta exatamente no instante que o {@code Retry-After} anunciou, sem erro de
     * arredondamento.
     */
    public RateLimitDecision tryConsume(
            String key,
            AppRateLimitProperties.Rule rule
    ) {
        if (!enabled) {
            return RateLimitDecision.ALLOWED;
        }

        long now = clock.millis();
        long windowMillis = rule.windowSeconds() * 1000L;
        long capacity = rule.limit() * windowMillis;

        synchronized (lock) {
            Bucket bucket = buckets.get(key);

            if (bucket == null) {
                bucket = new Bucket(capacity, now);
                buckets.put(key, bucket);
            }

            long elapsed = Math.max(0, now - bucket.lastRefillMillis);
            bucket.credit = Math.min(capacity, bucket.credit + elapsed * rule.limit());
            bucket.lastRefillMillis = now;

            if (bucket.credit >= windowMillis) {
                bucket.credit -= windowMillis;
                return RateLimitDecision.ALLOWED;
            }

            long missing = windowMillis - bucket.credit;
            long waitMillis = (missing + rule.limit() - 1) / rule.limit();

            return RateLimitDecision.denied((waitMillis + 999) / 1000);
        }
    }

    int size() {
        synchronized (lock) {
            return buckets.size();
        }
    }

    private static final class Bucket {

        private long credit;
        private long lastRefillMillis;

        private Bucket(long credit, long lastRefillMillis) {
            this.credit = credit;
            this.lastRefillMillis = lastRefillMillis;
        }
    }
}
