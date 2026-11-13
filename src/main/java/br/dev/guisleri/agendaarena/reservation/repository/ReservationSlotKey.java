package br.dev.guisleri.agendaarena.reservation.repository;

/**
 * Ids necessários para travar o horário de uma reserva antes de carregá-la.
 */
public record ReservationSlotKey(Long scheduleSlotId, Long sportFieldId) {
}
