package br.dev.guisleri.agendaarena.match.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Rateio em centavos com arredondamento consistente: cada pagante recebe a parte inteira da
 * divisão e os centavos que sobram vão, um a um, para os primeiros na ordem de inscrição.
 * A soma das cotas é sempre exatamente o total.
 */
public final class MatchSplitCalculator {

    private MatchSplitCalculator() {
    }

    /**
     * @param total  valor total (escala até 2 casas)
     * @param payers quantidade de pagantes, na ordem de inscrição
     * @return a cota de cada pagante, na mesma ordem; lista vazia se não há pagantes
     */
    public static List<BigDecimal> split(BigDecimal total, int payers) {
        if (total.signum() < 0) {
            throw new IllegalArgumentException("Total must not be negative");
        }

        List<BigDecimal> shares = new ArrayList<>(Math.max(payers, 0));

        if (payers <= 0) {
            return shares;
        }

        long totalCents = total.setScale(2, RoundingMode.UNNECESSARY)
                .movePointRight(2)
                .longValueExact();
        long baseCents = totalCents / payers;
        long remainder = totalCents % payers;

        for (int index = 0; index < payers; index++) {
            long cents = baseCents + (index < remainder ? 1 : 0);
            shares.add(BigDecimal.valueOf(cents, 2));
        }

        return shares;
    }
}
