package br.dev.guisleri.agendaarena.match.controller;

import br.dev.guisleri.agendaarena.common.security.AuthenticatedUsers;
import br.dev.guisleri.agendaarena.match.dto.MatchResponseDTO;
import br.dev.guisleri.agendaarena.match.service.MatchCreationResult;
import br.dev.guisleri.agendaarena.match.service.MatchService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Partida a partir da minha reserva avulsa: criar e consultar. A gestão é por id em
 * {@code /me/matches/{matchId}} ({@link MeMatchController}).
 */
@RestController
@RequestMapping("/me/reservations/{reservationId}/match")
public class MeReservationMatchController {

    private final MatchService matchService;

    public MeReservationMatchController(MatchService matchService) {
        this.matchService = matchService;
    }

    /**
     * 201 ao criar; 200 com a mesma partida se ela já existia.
     */
    @PostMapping
    public ResponseEntity<MatchResponseDTO> createMatch(
            @PathVariable Long reservationId,
            Authentication authentication
    ) {
        MatchCreationResult result = matchService.createMatch(
                AuthenticatedUsers.userId(authentication),
                reservationId
        );

        return ResponseEntity
                .status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(MatchResponseDTO.fromDetails(result.details()));
    }

    @GetMapping
    public ResponseEntity<MatchResponseDTO> findMyMatch(
            @PathVariable Long reservationId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(MatchResponseDTO.fromDetails(
                matchService.findMyMatchByReservation(
                        AuthenticatedUsers.userId(authentication),
                        reservationId
                )
        ));
    }
}
