package br.dev.guisleri.agendaarena.reservation.controller;

import br.dev.guisleri.agendaarena.common.security.AuthenticatedUsers;
import br.dev.guisleri.agendaarena.reservation.dto.ReservationResponseDTO;
import br.dev.guisleri.agendaarena.reservation.service.ReservationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * "Minhas reservas": o usuário logado só enxerga e age sobre as próprias reservas. O id vem do
 * token, nunca da URL; reserva de outra pessoa responde 404.
 */
@RestController
@RequestMapping("/me/reservations")
public class MeReservationController {

    private final ReservationService reservationService;

    public MeReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @GetMapping
    public ResponseEntity<List<ReservationResponseDTO>> findMyReservations(
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                reservationService
                        .findOrganizerReservations(AuthenticatedUsers.userId(authentication))
                        .stream()
                        .map(ReservationResponseDTO::fromEntity)
                        .toList()
        );
    }

    @GetMapping("/{reservationId}")
    public ResponseEntity<ReservationResponseDTO> findMyReservation(
            @PathVariable Long reservationId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                ReservationResponseDTO.fromEntity(
                        reservationService.findOrganizerReservation(
                                AuthenticatedUsers.userId(authentication),
                                reservationId
                        )
                )
        );
    }

    @PostMapping("/{reservationId}/cancellation-request")
    public ResponseEntity<ReservationResponseDTO> requestCancellation(
            @PathVariable Long reservationId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                ReservationResponseDTO.fromEntity(
                        reservationService.requestCancellation(
                                AuthenticatedUsers.userId(authentication),
                                reservationId
                        )
                )
        );
    }

}
