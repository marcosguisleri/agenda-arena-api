package br.dev.guisleri.agendaarena.reservation.event;

/**
 * O estabelecimento recusou o pedido de cancelamento; a reserva segue confirmada. Publicado dentro da transação; os ouvintes de e-mail só agem depois do commit.
 */
public record CancellationRefusedEvent(ReservationNotice notice) {
}
