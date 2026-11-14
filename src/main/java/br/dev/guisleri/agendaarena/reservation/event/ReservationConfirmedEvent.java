package br.dev.guisleri.agendaarena.reservation.event;

/**
 * Reserva online criada e confirmada. Publicado dentro da transação; os ouvintes de e-mail só agem depois do commit.
 */
public record ReservationConfirmedEvent(ReservationNotice notice) {
}
