package br.dev.guisleri.agendaarena.reservation.event;

/**
 * Reserva cancelada pelo estabelecimento (inclui aprovar um pedido de cancelamento). Publicado dentro da transação; os ouvintes de e-mail só agem depois do commit.
 */
public record ReservationCancelledEvent(ReservationNotice notice) {
}
