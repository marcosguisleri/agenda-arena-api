package br.dev.guisleri.agendaarena.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Relógio que os testes avançam à mão (os limites dependem do tempo e não podem esperar de
 * verdade).
 */
public final class MutableClock extends Clock {

    private final ZoneId zoneId;
    private volatile Instant current;

    public MutableClock(Instant start, ZoneId zoneId) {
        this.current = start;
        this.zoneId = zoneId;
    }

    public void set(Instant instant) {
        this.current = instant;
    }

    public void advance(Duration duration) {
        this.current = current.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return zoneId;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(current, zone);
    }

    @Override
    public Instant instant() {
        return current;
    }
}
