package br.dev.guisleri.agendaarena.common.time;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class StartTimeCheckerTests {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 16);

    private StartTimeChecker checkerAt(LocalDateTime now) {
        return new StartTimeChecker(Clock.fixed(now.atZone(ZONE).toInstant(), ZONE));
    }

    @Test
    @DisplayName("horário futuro no mesmo dia ainda não começou")
    void laterTodayHasNotStarted() {
        StartTimeChecker checker = checkerAt(TODAY.atTime(18, 59, 59));

        assertThat(checker.hasStarted(TODAY, LocalTime.of(19, 0))).isFalse();
    }

    @Test
    @DisplayName("horário que começa exatamente agora já conta como iniciado")
    void exactlyNowHasStarted() {
        StartTimeChecker checker = checkerAt(TODAY.atTime(19, 0));

        assertThat(checker.hasStarted(TODAY, LocalTime.of(19, 0))).isTrue();
    }

    @Test
    @DisplayName("horário anterior no mesmo dia já começou")
    void earlierTodayHasStarted() {
        StartTimeChecker checker = checkerAt(TODAY.atTime(19, 0, 1));

        assertThat(checker.hasStarted(TODAY, LocalTime.of(19, 0))).isTrue();
    }

    @Test
    @DisplayName("dia futuro nunca começou, mesmo com horário menor que o atual")
    void futureDateHasNotStarted() {
        StartTimeChecker checker = checkerAt(TODAY.atTime(23, 0));

        assertThat(checker.hasStarted(TODAY.plusDays(1), LocalTime.of(0, 0))).isFalse();
    }

    @Test
    @DisplayName("dia passado sempre começou, mesmo com horário maior que o atual")
    void pastDateHasStarted() {
        StartTimeChecker checker = checkerAt(TODAY.atTime(0, 1));

        assertThat(checker.hasStarted(TODAY.minusDays(1), LocalTime.of(23, 59))).isTrue();
    }
}
