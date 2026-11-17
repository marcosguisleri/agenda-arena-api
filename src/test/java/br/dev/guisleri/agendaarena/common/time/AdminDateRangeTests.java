package br.dev.guisleri.agendaarena.common.time;

import br.dev.guisleri.agendaarena.common.exception.ValidationFailedException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminDateRangeTests {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 19);

    @Test
    void shouldDefaultToTodayPlus30Days() {
        AdminDateRange range = AdminDateRange.resolve(null, null, TODAY);

        assertThat(range.from()).isEqualTo(TODAY);
        assertThat(range.to()).isEqualTo(TODAY.plusDays(30));
    }

    @Test
    void shouldCompleteTheMissingBoundWith30Days() {
        LocalDate date = LocalDate.of(2026, 11, 10);

        assertThat(AdminDateRange.resolve(date, null, TODAY))
                .isEqualTo(new AdminDateRange(date, date.plusDays(30)));
        assertThat(AdminDateRange.resolve(null, date, TODAY))
                .isEqualTo(new AdminDateRange(date.minusDays(30), date));
    }

    @Test
    void shouldAcceptUpTo92DaysInclusive() {
        assertThat(AdminDateRange.resolve(TODAY, TODAY.plusDays(91), TODAY).to())
                .isEqualTo(TODAY.plusDays(91));
        assertThat(AdminDateRange.resolve(TODAY, TODAY, TODAY).to()).isEqualTo(TODAY);
    }

    @Test
    void shouldRejectMoreThan92Days() {
        assertThatThrownBy(() -> AdminDateRange.resolve(TODAY, TODAY.plusDays(92), TODAY))
                .isInstanceOf(ValidationFailedException.class)
                .hasMessageContaining("92");
    }

    @Test
    void shouldRejectToBeforeFrom() {
        assertThatThrownBy(() -> AdminDateRange.resolve(TODAY, TODAY.minusDays(1), TODAY))
                .isInstanceOf(ValidationFailedException.class)
                .hasMessageContaining("cannot be before");
    }
}
