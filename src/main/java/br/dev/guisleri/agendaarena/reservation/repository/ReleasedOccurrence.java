package br.dev.guisleri.agendaarena.reservation.repository;

import java.time.LocalDate;

/**
 * Par (série, data) liberado, para decidir em lote quais partidas de ocorrência foram canceladas.
 */
public record ReleasedOccurrence(Long recurringReservationId, LocalDate occurrenceDate) {
}
