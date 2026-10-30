package br.dev.guisleri.agendaarena.reservation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

/**
 * {@code organizerId} é opcional: o organizador autenticado reserva para si mesmo (o id vem do
 * token). Só o administrador do estabelecimento precisa informá-lo, para reservar em nome de um
 * usuário.
 */
public record CreateOnlineReservationRequestDTO(

        @Positive
        Long organizerId,

        @NotNull
        LocalDate reservationDate

) {
}
