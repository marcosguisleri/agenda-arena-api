package br.dev.guisleri.agendaarena.common.time;

import br.dev.guisleri.agendaarena.common.exception.ValidationFailedException;

import java.time.LocalDate;

/**
 * Período das listagens do painel do administrador (reservas, partidas, bloqueios). Único ponto
 * da regra: sem {@code from}/{@code to}, de hoje a hoje+30; só um dos dois, o outro fica 30 dias
 * adiante (ou atrás); no máximo 92 dias, inclusive.
 */
public record AdminDateRange(LocalDate from, LocalDate to) {

    public static final int DEFAULT_RANGE_DAYS = 30;
    public static final int MAX_RANGE_DAYS = 92;

    public static AdminDateRange resolve(LocalDate from, LocalDate to, LocalDate today) {
        LocalDate resolvedFrom = from != null
                ? from
                : to != null ? to.minusDays(DEFAULT_RANGE_DAYS) : today;
        LocalDate resolvedTo = to != null
                ? to
                : resolvedFrom.plusDays(DEFAULT_RANGE_DAYS);

        if (resolvedTo.isBefore(resolvedFrom)) {
            throw new ValidationFailedException("to", "Date 'to' cannot be before 'from'");
        }

        if (resolvedTo.isAfter(resolvedFrom.plusDays(MAX_RANGE_DAYS - 1L))) {
            throw new ValidationFailedException(
                    "to",
                    "Period cannot exceed " + MAX_RANGE_DAYS + " days"
            );
        }

        return new AdminDateRange(resolvedFrom, resolvedTo);
    }
}
