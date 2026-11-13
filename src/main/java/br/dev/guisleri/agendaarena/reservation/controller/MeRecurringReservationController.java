package br.dev.guisleri.agendaarena.reservation.controller;

import br.dev.guisleri.agendaarena.common.security.AuthenticatedUsers;
import br.dev.guisleri.agendaarena.reservation.dto.MyRecurringReservationResponseDTO;
import br.dev.guisleri.agendaarena.reservation.service.RecurringReservationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * "Minhas recorrências": o usuário logado só enxerga as séries vinculadas a ele. O id vem do
 * token, nunca da URL.
 */
@RestController
@RequestMapping("/me/recurring-reservations")
public class MeRecurringReservationController {

    private final RecurringReservationService recurringReservationService;

    public MeRecurringReservationController(
            RecurringReservationService recurringReservationService
    ) {
        this.recurringReservationService = recurringReservationService;
    }

    @GetMapping
    public ResponseEntity<List<MyRecurringReservationResponseDTO>> findMyRecurringReservations(
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                recurringReservationService
                        .findOrganizerRecurringReservations(
                                AuthenticatedUsers.userId(authentication)
                        )
                        .stream()
                        .map(MyRecurringReservationResponseDTO::fromEntity)
                        .toList()
        );
    }

}
