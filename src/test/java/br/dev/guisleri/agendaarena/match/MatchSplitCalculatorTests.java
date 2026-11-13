package br.dev.guisleri.agendaarena.match;

import br.dev.guisleri.agendaarena.match.service.MatchSplitCalculator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Rateio em centavos (teste unitário, sem Spring).
 */
class MatchSplitCalculatorTests {

    @Test
    void shouldGiveRemainingCentsToTheFirstPayers() {
        List<BigDecimal> shares = MatchSplitCalculator.split(new BigDecimal("150.00"), 7);

        assertThat(shares).containsExactly(
                new BigDecimal("21.43"),
                new BigDecimal("21.43"),
                new BigDecimal("21.43"),
                new BigDecimal("21.43"),
                new BigDecimal("21.43"),
                new BigDecimal("21.43"),
                new BigDecimal("21.42")
        );
    }

    @Test
    void shouldSplitEvenlyWhenThereIsNoRemainder() {
        assertThat(MatchSplitCalculator.split(new BigDecimal("150.00"), 3))
                .containsExactly(
                        new BigDecimal("50.00"),
                        new BigDecimal("50.00"),
                        new BigDecimal("50.00")
                );
    }

    @Test
    void shouldGiveWholeAmountToSinglePayer() {
        assertThat(MatchSplitCalculator.split(new BigDecimal("150"), 1))
                .containsExactly(new BigDecimal("150.00"));
    }

    @Test
    void shouldReturnNoSharesWithoutPayers() {
        assertThat(MatchSplitCalculator.split(new BigDecimal("150.00"), 0)).isEmpty();
    }

    @Test
    void shouldSplitZeroTotal() {
        assertThat(MatchSplitCalculator.split(new BigDecimal("0.00"), 2))
                .containsExactly(new BigDecimal("0.00"), new BigDecimal("0.00"));
    }

    @Test
    void shouldRejectNegativeTotal() {
        assertThatThrownBy(() -> MatchSplitCalculator.split(new BigDecimal("-1.00"), 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({
            "150.00, 7",
            "100.00, 3",
            "0.01, 3",
            "99.99, 11",
            "180.50, 14",
            "1.00, 60"
    })
    void sharesShouldAlwaysAddUpToTheTotalAndDifferByAtMostOneCent(
            BigDecimal total,
            int payers
    ) {
        List<BigDecimal> shares = MatchSplitCalculator.split(total, payers);

        assertThat(shares).hasSize(payers);
        assertThat(shares.stream().reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(total);
        assertThat(shares.getFirst().subtract(shares.getLast()))
                .isBetween(BigDecimal.ZERO, new BigDecimal("0.01"));
        assertThat(shares).allSatisfy(share -> assertThat(share.scale()).isEqualTo(2));
    }
}
