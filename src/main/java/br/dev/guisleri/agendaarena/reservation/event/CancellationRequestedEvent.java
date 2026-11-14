package br.dev.guisleri.agendaarena.reservation.event;

/**
 * O organizador pediu o cancelamento da reserva. Publicado dentro da transação; os ouvintes de e-mail só agem depois do commit.
 */
public record CancellationRequestedEvent(ReservationNotice notice) {
}
